package com.hydrokarbs.engine.session;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.bukkit.World;

import com.hydrokarbs.engine.AttributeBag;
import com.hydrokarbs.engine.EngineTicker;
import com.hydrokarbs.engine.component.SessionComponent;
import com.hydrokarbs.engine.event.SessionEvent;
import com.hydrokarbs.engine.instance.MatchModeInstance;
import com.hydrokarbs.engine.mode.MatchMode;
import com.hydrokarbs.engine.mode.MatchModeMetadata;
import com.hydrokarbs.engine.mode.MatchModeRegistry;
import com.hydrokarbs.engine.port.EngineServices;
import com.hydrokarbs.engine.port.JoinAdmission;
import com.hydrokarbs.engine.port.JoinContext;
import com.hydrokarbs.engine.port.JoinDecision;
import com.hydrokarbs.engine.port.MatchModeInstanceAccess;
import com.hydrokarbs.engine.port.ModeLookup;
import com.hydrokarbs.engine.port.SessionEventPort;
import com.hydrokarbs.engine.port.SessionPort;
import com.hydrokarbs.engine.port.SessionServices;
import com.hydrokarbs.engine.port.WorldHandle;
import com.hydrokarbs.engine.port.WorldPort;
import com.hydrokarbs.map.MapDefinition;
import com.hydrokarbs.map.MapRegistry;

/**
 * 会话服务实现:两阶段切换 + 三级回退 + 成员进出与解散(§3.2)。
 *
 * <p><b>四块结构</b>:①同步段(主线程内存只读检查:守卫 + A1~A5 + 防护①,任何
 * 失败旧实例继续);②异步段 A6 世界准备(30s 超时,防护④;完成回程经主线程执行器,
 * 绝不在异步线程);③执行段 B(唯一入口:节拍器下一 tick 经 runPendingSwitch 驱动,
 * 主线程单 tick 原子);④回退与生死:B 失败 → 回退模式(带豁免;回退无效/被拒 →
 * SEVERE + 二级解散,单点收敛在 fallbackSwitch)→ 解散(全员传送 + 兜底清态,传送
 * 失败保持原地);原请求以拒绝完结,去向经会话事件告知。
 *
 * <p>引擎不直碰平台:传送/清态/两个世界清场口均为 L0 注入,best-effort WARN 不
 * 阻塞;future 完成点在节拍器执行段(成功)或主线程执行器(拒绝);公共方法全主线程。
 */
public final class SessionServiceImpl implements SessionService {

    /** 世界准备超时(秒,防护④,§3.5)。 */
    private static final long WORLD_PREPARE_TIMEOUT_SECONDS = 30L;

    private final ModeLookup modes;
    private final WorldPort worlds;
    private final MapRegistry maps;
    private final Function<String, MatchModeConfig> configSource;
    private final SessionPolicy policy;
    private final String fallbackModeId;
    private final EngineServices engineServices;
    private final Executor mainExecutor;
    private final Consumer<Collection<UUID>> hubRelocator;
    private final Consumer<Collection<UUID>> safeStateCleaner;
    private final BiConsumer<World, World> worldClearer;      // ★v1.9(27 号):切换世界清场(旧世界 → 目标世界出生点)
    private final Consumer<World> hubWorldClearer;            // ★v1.9(27 号):解散世界清场(地图世界 → 主世界出生点)
    private final Consumer<SessionEvent> sessionEventMirror;
    private final Logger log;

    /** 存活会话表(id → 会话;增删只在 create/dissolve,主线程单线程访问)。 */
    private final Map<SessionId, Session> sessions = new LinkedHashMap<>();

    /** 会话驱动钩子的实现体(全部会话共享同一实例,无会话态)。 */
    private final SessionDrive drive = new SessionDrive();

    private SessionServiceImpl(Builder builder) {
        this.modes = builder.modes;
        this.worlds = builder.worlds;
        this.maps = builder.maps;
        this.configSource = builder.configSource;
        this.policy = builder.policy;
        this.fallbackModeId = builder.fallbackModeId;
        this.engineServices = builder.engineServices;
        this.mainExecutor = builder.mainExecutor;
        this.hubRelocator = builder.hubRelocator;
        this.safeStateCleaner = builder.safeStateCleaner;
        this.worldClearer = builder.worldClearer;
        this.hubWorldClearer = builder.hubWorldClearer;
        this.sessionEventMirror = builder.sessionEventMirror;
        this.log = builder.logger;
    }

    // ── SessionService:创建与查询(★v4:会话组件装配接线,公开方法签名不变) ───

    /**
     * 创建会话并装配会话级组件(★v4/§3.8;顺序是规格的一部分):建骨架 → 注册表
     * bind 逐 Spec 冻结快照 → 依赖校验 → 声明序造件(构造期注入 svc)→ 事件钩子
     * 桥接 → awake → start → 发创建事件 → 首次 {@code switchMode};组件先于首个
     * 实例就绪(首实例的组件才查得到)。装配期任何一步抛 → 会话创建失败(响亮):
     * 记 SEVERE 后原样上抛,不入存活表(与运行期隔离不可混淆:坏组件不得带着起跑)。
     */
    @Override
    public Session create(SessionSpec spec) {
        Objects.requireNonNull(spec, "spec");
        if (sessions.containsKey(spec.id())) {
            throw new IllegalStateException("重复的会话 id: " + spec.id().value());
        }
        Session session = new Session(spec, policy);
        if (sessionEventMirror != null) {
            // ★v1.1-D3:构造后立即挂全局汇聚镜像(先于一切发布);镜像异常隔离在总线侧(红线)。
            session.events().setMirror(sessionEventMirror);
        }
        try {
            assembleSessionComponents(session, spec);
        } catch (Throwable t) {
            // 会话创建失败(响亮):已造组件未 start(无玩家痕迹),会话对象整体丢弃,不入表
            log.log(Level.SEVERE, "Session " + spec.id().value() + " component assembly failed, creation aborted", t);
            throw t;
        }
        session.bindDrive(drive);
        sessions.put(spec.id(), session);
        session.events().publish(SessionEvent.SessionCreated.of(spec.id(), spec.initialModeId()));
        log.log(Level.INFO, "Session {0} created (topology {1}, initial mode ''{2}'', {3} session component(s), {4} live)",
                new Object[] { spec.id().value(), policy.topology(), spec.initialModeId(),
                        spec.sessionComponents().size(), sessions.size() });
        // 首切装配初始模式(不受最短驻留限制);空/未注册 → WARN + 泊回退(★v1.13/31 号:
        // 不再「无实例驻留」;泊回退记切换时刻,窗口内后补切换被防护①拒,可传 force)。
        // 所有权判据(31 号复审 S1):本回调只接管「同步段被拒/A6 失败」(完结时 RUNNING
        // 且无挂起计划);B 段失败/隔离/解散各有归属,重入 → 双重回退 → 误 SEVERE 解散。
        beginSwitch(session, MatchModeSelection.of(spec.initialModeId()), Handoff.empty(), EndResult.nowhere())
                .whenComplete((result, err) -> {
                    if (!(result instanceof SwitchResult.Rejected rejected)) {
                        return;
                    }
                    if (session.state() != SessionState.RUNNING || session.pendingSwitch() != null) {
                        return;             // 已由 rollback/onQuarantine/dissolveNow 接管(所有权判据,见上注)
                    }
                    log.log(Level.WARNING, "Session {0} initial mode ''{1}'' missing or unregistered, parking on fallback: {2}",
                            new Object[] { spec.id().value(), spec.initialModeId(), rejected.detail() });
                    fallbackSwitch(session, EndResult.nowhere());
                });
        return session;
    }

    /**
     * 会话组件装配序(§3.8):bind 逐 Spec 冻结快照(id 供给面类型名推导,注册表内
     * 去重)→ 依赖校验 → 声明序造件(svc 构造期注入)→ 钩子桥接 → awake → start。
     * 任一步抛即会话创建失败。
     */
    private void assembleSessionComponents(Session session, SessionSpec spec) {
        var registry = session.sessionComponents();
        for (SessionComponent.Specification<?> componentSpec : spec.sessionComponents()) {
            registry.bind(deriveComponentId(componentSpec), componentSpec.freeze());
        }
        registry.verifyDependencies();
        SessionServices svc = new SessionServices(new SessionPortImpl(session), registry);
        registry.createAll(svc);
        registry.bridgeEventHooks();
        registry.awakeAll();
        registry.startAll();
    }

    /**
     * 会话组件 id 推导(单一落点):供给面类型的简单名——同会话同供给面类型的两个
     * Spec 撞 id,由注册表 bind 去重响亮拒绝(待裁决⑤)。id 不进快照,跨会话共享无影响。
     */
    private static String deriveComponentId(SessionComponent.Specification<?> spec) {
        return spec.providedType().getSimpleName();
    }

    @Override
    public Optional<Session> sessionOf(UUID player) {
        Objects.requireNonNull(player, "player");
        for (Session session : sessions.values()) {
            if (session.roster().contains(player)) {
                return Optional.of(session);
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<Session> session(SessionId id) {
        Objects.requireNonNull(id, "id");
        return Optional.ofNullable(sessions.get(id));
    }

    @Override
    public Collection<Session> all() {
        return List.copyOf(sessions.values());
    }

    // ── SessionService:切换(公共入口 = 外部调用,触发来源①) ──────────

    @Override
    public CompletableFuture<SwitchResult> switchMode(SessionId id, MatchModeSelection target, Handoff payload) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(target, "target");
        Session session = sessions.get(id);
        if (session == null) {
            return CompletableFuture.completedFuture(
                    rejected(SwitchResult.Reason.REJECTED_BY_TARGET_MODE, "会话不存在: " + id.value()));
        }
        return beginSwitch(session, target, payload, EndResult.nowhere());
    }

    // ── 切换核心:同步段(守卫 + A1~A5 + 防护①)与异步段(A6) ──────────

    /**
     * 切换统一内核(外部调用/自动切换/回退递归殊途同归于本方法,§3.2)。
     * 同步段全部为内存只读检查;通过后挂占位计划并异步准备世界。
     */
    private CompletableFuture<SwitchResult> beginSwitch(Session session, MatchModeSelection target,
                                                         Handoff payload, EndResult cause) {
        CompletableFuture<SwitchResult> future = new CompletableFuture<>();
        // 守卫:防护②前半(重入)与解散互斥
        if (session.state() == SessionState.DISSOLVING) {
            future.complete(rejected(SwitchResult.Reason.REJECTED_BY_TARGET_MODE,
                    "会话 " + session.id().value() + " 正在解散,拒绝切换"));
            return future;
        }
        if (session.state() == SessionState.SWITCHING || session.pendingSwitch() != null) {
            future.complete(new SwitchResult.Rejected(SwitchResult.Reason.SWITCH_IN_PROGRESS,
                    "会话 " + session.id().value() + " 切换进行中,重入被拒"));
            return future;
        }
        // A1 目标模式在册
        MatchMode mode;
        try {
            mode = modes.require(target.modeId());
        } catch (RuntimeException e) {
            future.complete(new SwitchResult.Rejected(SwitchResult.Reason.MODE_NOT_FOUND,
                    target.modeId() + " 未注册: " + e.getMessage()));
            return future;
        }
        // A2 能力校验已随能力标签机制退场(★v1.7/25 号):外部能力硬依赖改由会话组件
        // 桥接表达(类型化声明 + 装配闸 + 运行期隔离),同步段自此无字符串校验步。
        // A3 地图解析(指定地图用之;否则按模式标签挑一张,最小实现取装载序第一张)
        MapDefinition map;
        try {
            map = resolveMap(session, mode, target);
        } catch (PrecheckFailure e) {
            future.complete(rejected(SwitchResult.Reason.REJECTED_BY_TARGET_MODE, e.getMessage()));
            return future;
        }
        MatchModeConfig cfg = configSource.apply(mode.id());
        // A3 后半:对全组件表逐个地图适配检查,任何非空 → 拒绝(问题清单进 detail)
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, MatchMode.ComponentEntry> entry : mode.components().entrySet()) {
            List<String> issues = entry.getValue().mapCheck().apply(map, cfg);
            if (issues != null) {
                for (String issue : issues) {
                    problems.add("组件 " + entry.getKey() + ": " + issue);
                }
            }
        }
        if (!problems.isEmpty()) {
            future.complete(rejected(SwitchResult.Reason.REJECTED_BY_TARGET_MODE,
                    "地图适配检查未通过(" + map.id() + "): " + problems));
            return future;
        }
        // A4 人数窗(豁免只放开下限;上限不豁免)
        int size = session.roster().size();
        if (size > MatchModeMetadata.maxParticipants(mode.meta())
                || (!target.force() && size < MatchModeMetadata.minParticipants(mode.meta()))) {
            future.complete(rejected(SwitchResult.Reason.REJECTED_BY_TARGET_MODE,
                    "人数窗外: 在册 " + size + ",目标窗口 [" + MatchModeMetadata.minParticipants(mode.meta())
                            + ", " + MatchModeMetadata.maxParticipants(mode.meta()) + "]"
                            + (target.force() ? "(豁免下限)" : "")));
            return future;
        }
        // A5 依赖校验(防御性二次:注册闸已验,这里失败即拒绝、旧实例毫发无损)
        try {
            mode.verifyDependencies();
        } catch (RuntimeException e) {
            future.complete(rejected(SwitchResult.Reason.REJECTED_BY_TARGET_MODE,
                    "目标模式依赖校验失败: " + e.getMessage()));
            return future;
        }
        // 防护①:最短驻留(非豁免一律受限;判据单一落点 dwellRemaining,★v1.13/31 号)
        if (!target.force()) {
            long remaining = dwellRemaining(session);
            if (remaining > 0) {
                log.log(Level.WARNING, "Session {0} switch rejected, min dwell: {1} ticks left, {2} required",
                        new Object[] { session.id().value(), remaining, policy.minDwellTicks() });
                future.complete(rejected(SwitchResult.Reason.REJECTED_BY_TARGET_MODE,
                        "驻留不足,剩 " + remaining + " tick(要求 " + policy.minDwellTicks() + ")"));
                return future;
            }
        }
        // A6 异步世界准备:先挂占位计划(阻止切换窗口内的并发请求),就绪后由节拍器执行
        PendingSwitch plan = new PendingSwitch(future, mode, map, cfg,
                payload == null ? Handoff.empty() : payload, target, cause, session.ticks());
        session.setPendingSwitch(plan);
        worlds.acquire(map, session.id())
                .orTimeout(WORLD_PREPARE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .whenComplete((handle, err) -> mainExecutor.execute(() -> onWorldReady(session, plan, handle, err)));
        return future;
    }

    /** A6 完成点(主线程执行器内):成功挂起并置 {@code SWITCHING};失败完结 future、旧实例毫发无损。 */
    private void onWorldReady(Session session, PendingSwitch plan, WorldHandle handle, Throwable err) {
        if (plan.aborted || session.pendingSwitch() != plan) {
            // 已作废/被顶替(防御):future 已按作废路径完结;已获取的副本引用须还回防泄漏(§14.19)
            if (handle != null && err == null) {
                worlds.release(handle);
            }
            return;
        }
        if (err != null || handle == null) {
            session.clearPendingSwitch();
            plan.future.complete(rejected(SwitchResult.Reason.REJECTED_BY_TARGET_MODE,
                    "世界副本准备失败" + (err != null ? ": " + err : "")));
            return;
        }
        plan.world = handle;
        plan.worldReady = true;
        String fromMode = session.currentInstance().map(MatchModeInstance::modeId).orElse("");
        session.transitionTo(SessionState.SWITCHING);
        session.events().publish(SessionEvent.ModeSwitchStarted.of(session.id(), fromMode, plan.mode.id()));
    }

    /** A3 地图解析:指定地图用之;否则按模式标签 × 会话地图池过滤(空 mapTags = 通配,
     *  单一判据经 MatchModeRegistry.matchesTags,31 号决策 B);空集拒绝,非空取装载序第一张。 */
    private MapDefinition resolveMap(Session session, MatchMode mode, MatchModeSelection target) {
        if (target.mapId().isPresent()) {
            String mapId = target.mapId().get();
            MapDefinition specified = maps.byId(mapId).orElse(null);
            if (specified == null) {
                throw new PrecheckFailure("指定地图不存在: " + mapId);
            }
            return specified;
        }
        List<String> pool = session.spec().mapPool();
        List<MapDefinition> candidates = new ArrayList<>();
        for (MapDefinition candidate : maps.all()) {
            if (!MatchModeRegistry.matchesTags(mode, candidate)) {
                continue;
            }
            if (!pool.isEmpty() && !pool.contains(candidate.id())) {
                continue;
            }
            candidates.add(candidate);
        }
        if (candidates.isEmpty()) {
            throw new PrecheckFailure("无可用地图: 模式标签 " + mode.mapTags()
                    + (pool.isEmpty() ? "" : ",会话地图池 " + pool));
        }
        return candidates.get(0);       // 最小实现:装载序第一张(自动挑图的占位策略)
    }

    /**
     * 驻余判据(★v1.13/31 号,单一落点):距最短驻留还差多少 tick,{@code <= 0} = 已满;
     * 从未切换过({@code NO_SWITCH_YET})恒 0(首切不设限)。防护①与回退预检共用本
     * 公式,豁免条件(force)差异在各自调用点,公式不复制。
     */
    private long dwellRemaining(Session session) {
        if (session.lastSwitchTick() == Session.NO_SWITCH_YET) {
            return 0L;
        }
        return Math.max(0L, policy.minDwellTicks() - (session.ticks() - session.lastSwitchTick()));
    }

    // ── 执行段 B(节拍器驱动,主线程单 tick 原子) ────────────────────

    /**
     * B 段执行(唯一入口:节拍器经 {@code runPendingSwitch} → {@link SessionDrive#onDrive})。
     * 挂起计划已冻结全部裁决数据,不再回查注册表(唯一例外:防护③的人数重校验)。
     */
    private void executeB(Session session, PendingSwitch plan) {
        session.clearPendingSwitch();       // 取走执行(此后重入守卫恢复生效)
        String fromMode = session.currentInstance().map(MatchModeInstance::modeId).orElse("");
        // 防护③:准备期人数漂移(非豁免且窗口外 → 中止,旧实例未动)
        int size = session.roster().size();
        AttributeBag meta = plan.mode.meta();
        if (size > MatchModeMetadata.maxParticipants(meta)
                || (!plan.forced() && size < MatchModeMetadata.minParticipants(meta))) {
            session.transitionTo(SessionState.RUNNING);
            plan.future.complete(rejected(SwitchResult.Reason.REJECTED_BY_TARGET_MODE,
                    "执行前人数重校验失败: 在册 " + size + ",窗口 [" + MatchModeMetadata.minParticipants(meta)
                            + ", " + MatchModeMetadata.maxParticipants(meta) + "]"));
            return;
        }
        try {
            // B1 交接出局(状态尚完整,只准写交接载荷)
            Handoff outgoing = session.currentInstance()
                    .map(MatchModeInstance::dispatchHandoff)
                    .orElseGet(Handoff::empty);
            // B2 幂等清场(唯一清场入口:组件逆序停用 → 登记资源回收 → 清总线)
            session.currentInstance().ifPresent(MatchModeInstance::close);
            // B3 合并:外部载荷覆盖旧实例自报(方向写反 = 外部指令被旧实例劫持)
            Handoff merged = Handoff.merge(outgoing, plan.payload);
            // ★v1.9(27 号)B3.5 同世界清场,先行于 B4 组件摆放:判据 = World 引用相等(==;
            // 复载必产生新对象 → 归 B5 异图清场,引用相等只来自端口表同句柄命中;首切
            // current 为 null 跳过;sameWorldReuse 供此处与 B5 两处门闩)。清场对象 = 世界内
            // 全部在场者,先行则组件其后摆位终态正确,殿后则拽回 startAll 摆好的位次(27 号
            // 登记的冻结语义变更;失败不阻塞,B4 照常)。
            WorldHandle current = session.worldHandle();
            boolean sameWorldReuse = current != null && current.world() == plan.world.world();
            if (sameWorldReuse) {
                clearWorldQuietly(current.world(), plan.world.world(), session.id());
            }
            // B4 造新实例并两段生效(名册原地位移经入场裁决触发点①)
            MatchModeInstance next = plan.mode.createInstance(
                    session, plan.map, plan.cfg, plan.world, merged, engineServices);
            next.awakeAll();
            try {
                next.startAll();
            } catch (Throwable startFailure) {
                // 生效段抛 = 部分组件已生效:先全停(幂等清场)再走回退
                next.close();
                throw startFailure;
            }
            // B5 落位(旧世界句柄的释放在切换完成之后,try/finally 接缝)
            WorldHandle previousWorld = session.replaceWorldHandle(plan.world);
            try {
                session.setCurrentInstance(next);
                session.recordSwitchTick(session.ticks());
                session.transitionTo(SessionState.RUNNING);
                session.events().publish(SessionEvent.ModeSwitchCompleted.of(
                        session.id(), fromMode, plan.mode.id()));
                plan.future.complete(new SwitchResult.Succeeded(
                        session.id(), fromMode, plan.mode.id(), plan.cause));
            } finally {
                // 释放旧句柄(§3.7:切换完成之后;§14.19);同图复用句柄未换,计数减一
                // 仍 >0,端口零卸载——等价无释放。
                // ★v1.9(27 号)B5 异图清场殿后:previousWorld 非 null 守卫内(首切守卫外
                // NPE)、完成事件后 release 前,门闩 !sameWorldReuse(B3.5 已先行,恰一次);
                // 兜底传走旧世界名册外滞留者 → 目标世界出生点,失败 WARN 不阻塞;卸载失败
                // 保留目录(23 号)为最后防线。
                if (previousWorld != null) {
                    if (!sameWorldReuse) {
                        clearWorldQuietly(previousWorld.world(), plan.world.world(), session.id());
                    }
                    worlds.release(previousWorld);
                }
            }
        } catch (Throwable t) {
            rollback(session, plan, t);
        }
    }

    /** B 段失败回退(两级判据:装配/唤醒抛直接回退,生效抛已先全停):原请求以拒绝
     *  完结 → 会话回常态 → 递归切换回退模式(一级;故障自愈立即执行,不加驻留预检
     *  ——回退目标再失败即二级解散终止,无循环死锁)。 */
    private void rollback(Session session, PendingSwitch plan, Throwable cause) {
        log.log(Level.SEVERE, "Switch failed, falling back: session " + session.id().value()
                + " target " + plan.mode.id(), cause);
        plan.future.complete(rejected(SwitchResult.Reason.REJECTED_BY_TARGET_MODE,
                "目标模式装配失败: " + cause.getMessage()));
        if (session.state() == SessionState.SWITCHING) {
            session.transitionTo(SessionState.RUNNING);    // 旧实例已清、当前无实例,回常态再递归
        }
        session.setCurrentInstance(null);
        fallbackSwitch(session, EndResult.nowhere());
    }

    /** 切换世界清场口(★v1.9/27 号,L0 注入):best-effort——失败只 WARN,不阻塞切换/释放。 */
    private void clearWorldQuietly(World from, World to, SessionId id) {
        try {
            worldClearer.accept(from, to);
        } catch (Throwable t) {
            log.log(Level.WARNING, "World clear on switch failed, members stay put: session " + id.value(), t);
        }
    }

    /** 解散世界清场口(★v1.9/27 号,L0 注入):best-effort——失败 WARN 不阻塞解散,
     *  finally 继续 discardSession → 归零卸载(保留目录为最后防线,23 号)。 */
    private void clearWorldToHubQuietly(World world, SessionId id) {
        try {
            hubWorldClearer.accept(world);
        } catch (Throwable t) {
            log.log(Level.WARNING, "World clear on dissolve failed, members stay put: session " + id.value(), t);
        }
    }

    /**
     * 一级回退(★v1.13/31 号单点收敛):递归切换到配置注入的回退模式(源码零字面量;
     * 带豁免绕过人数下限与最短驻留——服务不变式;cause 仅来源语义)。回退无效(空串/
     * 未注册,只可能是配置错误)或被拒/异常 → SEVERE + 即时二级解散。
     * 重入护栏(31 号复审 S1):解散中或已有切换在途(SWITCHING/挂起计划,含他路回退)
     * 时 no-op——同一失败只许一个回退归属,重入则误走解散分支(ENDING 不在护栏集)。
     */
    private void fallbackSwitch(Session session, EndResult cause) {
        if (session.state() == SessionState.DISSOLVING
                || session.state() == SessionState.SWITCHING
                || session.pendingSwitch() != null) {
            return;                     // 解散进行中/另有切换在途(含他路发起的回退):不重入(护栏见方法注)
        }
        if (fallbackModeId.isBlank()) {
            log.log(Level.SEVERE, "Session {0} has no valid fallback mode, dissolving",
                    session.id().value());
            dissolveNow(session, DissolveReason.FALLBACK_DISSOLVE);
            return;
        }
        CompletableFuture<SwitchResult> fallback = beginSwitch(session,
                new MatchModeSelection(fallbackModeId, Optional.empty(), true),
                Handoff.empty(), cause);
        fallback.whenComplete((result, err) -> {
            if (err != null || result instanceof SwitchResult.Rejected) {
                if (session.state() == SessionState.DISSOLVING) {
                    return;             // future 因解散作废被拒(非回退目标问题):不告警、不重入
                }
                log.log(Level.SEVERE, "Session {0} fallback switch rejected/failed, dissolving: {1}",
                        new Object[] { session.id().value(),
                                err != null ? err : ((SwitchResult.Rejected) result).detail() });
                dissolveNow(session, DissolveReason.FALLBACK_DISSOLVE);
            }
        });
    }

    // ── 自动切换(触发来源②③:节拍器在 ENDING 后的下一 tick 驱动) ─────────

    /**
     * ENDING 处置:读结束请求的显式意图;无显式去向回落回退模式(★v1.13/31 号:
     * 缺省去向管线退场)。回退链三步:空串先行即散(直呼 fallbackSwitch,即时解散
     * 不等驻留,消灭每拍 A1 拒绝空转)→ 驻留不足回填重试(节流,铁律 6:防回退环
     * 风暴,每窗口至多一轮)→ 驻留已满带原因 force 切回退(豁免人数下限)。
     * 机械闸门:调用栈必经节拍器(防护②后半),否则拒绝发起并保留请求待下一拍。
     */
    private void driveAutoSwitch(Session session) {
        if (!drivenByTicker()) {
            log.log(Level.WARNING, "Session {0} auto switch outside ticker, deferred",
                    session.id().value());
            return;                     // 结束请求保留,待节拍器正常驱动
        }
        List<Session.EndNotice> notices = session.drainEndNotices();
        if (notices.isEmpty()) {
            return;
        }
        Session.EndNotice notice = notices.get(notices.size() - 1);    // 以最后一条为准
        EndResult result = notice.result();
        if (result.transition().isPresent()) {
            TransitionIntent intent = result.transition().get();
            beginSwitch(session, intent.target(), intent.payload(), result).whenComplete((outcome, err) -> {
                // 同步段被拒(如最短驻留):回填结束请求,下一拍重试直至防护放行
                if (err == null && outcome instanceof SwitchResult.Rejected
                        && session.state() == SessionState.ENDING) {
                    session.restoreEndNotices(notices);
                }
            });
            return;
        }
        // 无显式去向:空串先行即散 + 驻留预检 + 回退(三步,31 号)
        if (fallbackModeId.isBlank()) {
            fallbackSwitch(session, result);
            return;
        }
        if (dwellRemaining(session) > 0) {
            session.restoreEndNotices(notices);        // 驻留窗内回填重试(节流,防回退环风暴)
            return;
        }
        fallbackSwitch(session, result);               // 驻留已满:force 切回退(cause = 本条结束结果)
    }

    /** 自动切换的调用栈检查:栈帧必含节拍器类(测试冻结的机械面,§3.5 防护②)。 */
    private static boolean drivenByTicker() {
        return StackWalker.getInstance(Set.of(StackWalker.Option.RETAIN_CLASS_REFERENCE))
                .walk(frames -> frames.anyMatch(frame -> frame.getDeclaringClass() == EngineTicker.class));
    }

    // ── 成员进出 ────────────────────────────────────────────────

    @Override
    public JoinResult join(SessionId id, UUID player) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(player, "player");
        Session session = sessions.get(id);
        if (session == null) {
            return new JoinResult.Rejected("session-not-found", "会话不存在: " + id.value());
        }
        if (session.state() == SessionState.DISSOLVING) {
            return new JoinResult.Rejected("dissolving", "会话 " + id.value() + " 正在解散,拒绝加入");
        }
        MatchModeInstance current = session.currentInstance().orElse(null);
        if (current == null || session.state() != SessionState.RUNNING) {
            // 无实例或切换中:名册照常登记,入场裁决推迟到新实例装配的触发点①(§3.2.1)
            registerMember(session, player);
            return new JoinResult.Joined(session.id());
        }
        // 触发点②:按组件声明序取第一个裁决实现者;无实现者默认 ADMIT
        JoinAdmission gate = current.joinGate();
        JoinDecision decision = gate != null
                ? gate.admit(player, new JoinContext(JoinContext.Trigger.MID_JOIN, session.id()))
                : new JoinDecision(JoinDecision.Kind.ADMIT, null);
        if (decision.kind() == JoinDecision.Kind.REJECT) {
            return new JoinResult.Rejected("rejected-by-mode",
                    decision.reason() != null ? decision.reason() : "实例入场裁决拒绝");
        }
        registerMember(session, player);
        current.admitParticipant(player);      // 安置细节归组件层
        return new JoinResult.Joined(session.id());
    }

    /** 名册登记(幂等)并发成员加入事件(仅新登记时发)。 */
    private void registerMember(Session session, UUID player) {
        boolean fresh = !session.roster().contains(player);
        session.roster().join(player);
        if (fresh) {
            session.events().publish(SessionEvent.MemberJoined.of(session.id(), player));
        }
    }

    @Override
    public void leave(UUID player, LeaveReason reason) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(reason, "reason");
        Session session = sessionOf(player).orElse(null);
        if (session == null || !session.roster().contains(player)) {
            return;                     // 未入会话/已不在册:no-op
        }
        switch (reason) {
            case QUIT -> {              // 断线:宽限标记(不移除)+ 即发离开事件
                long now = System.currentTimeMillis();
                session.tracker().onDisconnect(player, now);
                session.roster().markDisconnected(player, now);
            }
            case KICK -> session.roster().remove(player);   // 被踢:立即移除(宽限仅服务连接意外中断)
        }
        session.events().publish(SessionEvent.MemberLeft.of(session.id(), player, reason));
    }

    // ── 解散 ────────────────────────────────────────────────────

    @Override
    public void dissolve(SessionId id, DissolveReason reason) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(reason, "reason");
        Session session = sessions.get(id);
        if (session == null) {
            log.log(Level.FINE, "Dissolve skipped, session not found: {0}", id.value());
            return;
        }
        dissolveNow(session, reason);
    }

    /**
     * 解散执行(幂等;★v4/§3.8 顺序是规格的一部分):作废挂起切换 → 置 {@code DISSOLVING}
     * → 关闭实例 → 会话组件逆序 stop + 登记资源回收 → 成员传送主城出生点(失败保持
     * 原地)→ 销毁世界副本(discardSession,复用窗口随解散关闭)→ 兜底清态 → 摘除 →
     * 发解散事件。
     */
    private void dissolveNow(Session session, DissolveReason reason) {
        if (session.state() == SessionState.DISSOLVING) {
            return;                     // 幂等:解散中重复调用直接返回
        }
        // ★v1.13/31 号:先置 DISSOLVING 再作废挂起切换——作废同步完结 future,其回程
        // (create 首切接回退、回退被拒接二级解散)必须已见「解散中」,否则回退链在
        // 解散内部重入(嵌套解散 + 自迁移抛异常);重入由 fallbackSwitch 护栏吸收。
        session.transitionTo(SessionState.DISSOLVING);
        abortPending(session, "会话解散,挂起切换作废");
        List<UUID> members = session.roster().all().stream().map(SessionMember::uuid).toList();
        try {
            try {
                session.currentInstance().ifPresent(MatchModeInstance::close);
            } catch (Throwable t) {
                log.log(Level.WARNING, "Instance close threw on dissolve, continuing: " + session.id().value(), t);
            }
            session.setCurrentInstance(null);
            // ★v4:会话组件逆序 stop(无条件,无单例豁免;已隔离组件已停不重复停)→
            // 登记资源统一回收(订阅/延时任务句柄;stop 后框架回收,组件不自注销)
            try {
                session.sessionComponents().stopAll();
                session.sessionComponents().reclaimRegisteredResources();
            } catch (Throwable t) {
                log.log(Level.WARNING, "Session component reclaim threw on dissolve, continuing: " + session.id().value(), t);
            }
            try {
                hubRelocator.accept(members);       // 成员传送主城出生点(平台函数,L0 注入)
            } catch (Throwable t) {
                log.log(Level.WARNING, "Member relocation failed on dissolve, members stay put: session " + session.id().value(), t);
            }
        } finally {
            // 世界副本销毁兜底(§3.7:dissolve 的 finally;§14.19):heldWorld 先摘,
            // 计数表条目与全部副本(含已保留目录、迟到 in-flight 的已死短码)由
            // discardSession 在端口内部消化;无 heldWorld 也照发(在途获取须记已死短码)。
            WorldHandle heldWorld = session.worldHandle();
            if (heldWorld != null) {
                session.replaceWorldHandle(null);
            }
            // ★v1.9(27 号)解散世界清场(先名册后世界):名册成员已由上方 hubRelocator
            // 安置,此处兜底世界内全部在线玩家(getPlayers(),不以名册为界——名册外
            // 滞留者正是清场失败主因)→ 主世界出生点(hubSpawn() 单一口径);null 跳过
            // 无害,失败 WARN 不阻塞。
            if (heldWorld != null) {
                clearWorldToHubQuietly(heldWorld.world(), session.id());
            }
            worlds.discardSession(session.id());
            try {
                safeStateCleaner.accept(members);   // 兜底清理通用安全态(隐身/模式等)
            } catch (Throwable t) {
                log.log(Level.WARNING, "State cleanup threw on dissolve, ignored: " + session.id().value(), t);
            }
            sessions.remove(session.id());
            session.events().publish(SessionEvent.SessionDissolved.of(session.id(), reason));
            if (reason == DissolveReason.FALLBACK_DISSOLVE) {
                log.log(Level.SEVERE, "Session {0} fallback mode also failed, dissolved",
                        session.id().value());
            } else {
                log.log(Level.INFO, "Session {0} dissolved (reason {1}, {2} member(s) relocated)",
                        new Object[] { session.id().value(), reason, members.size() });
            }
        }
    }

    /** 作废挂起切换计划(future 以拒绝完结;解散/竞态时调用)。 */
    private void abortPending(Session session, String why) {
        PendingSwitch plan = session.pendingSwitch();
        if (plan == null) {
            return;
        }
        plan.aborted = true;
        session.clearPendingSwitch();
        plan.future.complete(rejected(SwitchResult.Reason.REJECTED_BY_TARGET_MODE, why));
    }

    private static SwitchResult.Rejected rejected(SwitchResult.Reason code, String detail) {
        return new SwitchResult.Rejected(code, detail);
    }

    /** 同步段预检失败(内部信号:消息即人读 detail)。 */
    private static final class PrecheckFailure extends RuntimeException {
        PrecheckFailure(String message) {
            super(message);
        }
    }

    // ── ★v4 SessionPort 实现体(会话自面:会话级组件对内核的唯一入口) ────────

    /**
     * 会话自面({@link SessionPort})实现体,每会话一份。读口现取;两个行动口(D8)
     * 镜像实例级:requestSwitch 与 switchMode 同一实现(受最短驻留与重入约束,§3.5),
     * requestDissolve 幂等。仅会话级组件与内核可调——svc 不给其他方。
     */
    private final class SessionPortImpl implements SessionPort {

        private final Session session;

        SessionPortImpl(Session session) {
            this.session = Objects.requireNonNull(session, "session");
        }

        @Override
        public SessionId id() {
            return session.id();
        }

        @Override
        public List<SessionMember> members() {
            return session.roster().all();
        }

        @Override
        public long tick() {
            return session.ticks();
        }

        @Override
        public SessionEventPort events() {
            return session.eventsPort();
        }

        @Override
        public Optional<MatchModeInstanceAccess> currentInstance() {
            // ★ 现取不缓存:empty 覆盖「无实例」与 ENDING/SWITCHING 间隙两种合法情况,
            // 不回落到旧实例(14.27);引用由调用方当场消费,不得越过切换缓存
            if (session.state() != SessionState.RUNNING) {
                return Optional.empty();
            }
            return session.currentInstance().map(MatchModeInstance::access);
        }

        @Override
        public void requestSwitch(MatchModeSelection target, Handoff payload) {
            // 与 switchMode 同一实现(§3.8):统一走 beginSwitch;请求口不带豁免——载荷
            // 选区中的豁免标记被剥离(§3.5:组件引发的切换计入最短驻留);被拒仅记日志。
            beginSwitch(session, new MatchModeSelection(target.modeId(), target.mapId(), false),
                    payload == null ? Handoff.empty() : payload, EndResult.nowhere());
        }

        @Override
        public void requestDissolve(DissolveReason reason) {
            Objects.requireNonNull(reason, "reason");
            dissolveNow(session, reason);   // 幂等:解散中重复请求被 DISSOLVING 护栏吸收
        }

        @Override
        public void later(int delayTicks, Runnable task) {
            scheduleSessionLater(session, delayTicks, task);
        }
    }

    /** 会话侧延迟任务(★v4):经平台口调度,句柄登记到发起组件名下(stop 统一回收)。 */
    private void scheduleSessionLater(Session session, int delayTicks, Runnable task) {
        Objects.requireNonNull(task, "task");
        AutoCloseable handle = engineServices.platform().scheduleOnce(delayTicks, task);
        session.sessionComponents()
                .registerResource(session.sessionComponents().callerOnStack(), handle);
    }

    // ── 会话驱动钩子(全部会话共享;挂在每个会话上) ───────────────────

    /**
     * 节拍器驱动落点:①挂起计划世界就绪 → 执行 B 段;②{@code ENDING} → 自动切换处置。
     * 故障隔离:强切回退模式(切换的第三个触发源,§3.1 状态机图「实例 tick 异常」)。
     */
    private final class SessionDrive implements Session.Drive {

        @Override
        public void onDrive(Session session) {
            PendingSwitch plan = session.pendingSwitch();
            if (plan != null) {
                if (plan.aborted) {
                    session.clearPendingSwitch();
                } else if (plan.worldReady) {
                    executeB(session, plan);       // 切换只在此驱动(§16.4-三律①)
                }
                return;                            // 世界准备中:本拍无事可做
            }
            if (session.state() == SessionState.ENDING) {
                driveAutoSwitch(session);
            }
        }

        @Override
        public void onQuarantine(Session session) {
            if (session.state() == SessionState.DISSOLVING) {
                return;
            }
            log.log(Level.SEVERE, "Session drive threw, quarantining and forcing fallback: {0}", session.id().value());
            abortPending(session, "会话故障隔离,挂起切换作废");
            // abortPending 同步完结 future——首切 create 回调可能已按所有权判据(31 号 S1)
            // 发起回退,其 SWITCHING 不得被下方归一化拽回 RUNNING(否则 B5 自迁移抛异常)。
            try {
                session.currentInstance().ifPresent(MatchModeInstance::close);
            } catch (Throwable t) {
                log.log(Level.WARNING, "Instance close threw on quarantine, continuing: " + session.id().value(), t);
            }
            session.setCurrentInstance(null);
            if (session.state() == SessionState.SWITCHING && session.pendingSwitch() == null) {
                session.transitionTo(SessionState.RUNNING);    // 回常态再递归(ENDING 可直达切换,他路在途则不动)
            }
            fallbackSwitch(session, EndResult.nowhere());      // 故障自愈立即执行,不加驻留预检;在途回退时 no-op(护栏)
        }
    }

    // ── 构造器(依赖全部注入;离线测试可替换每一跳) ─────────────────────

    /** 装配构造器:必填项缺失在 {@link #build()} 响亮抛。 */
    public static final class Builder {

        private ModeLookup modes;
        private WorldPort worlds;
        private MapRegistry maps;
        private Function<String, MatchModeConfig> configSource;
        private SessionPolicy policy;
        private String fallbackModeId;
        private EngineServices engineServices;
        private Executor mainExecutor = Runnable::run;
        private Consumer<Collection<UUID>> hubRelocator = members -> { };
        private Consumer<Collection<UUID>> safeStateCleaner = members -> { };
        private BiConsumer<World, World> worldClearer = (from, to) -> { };   // ★v1.9(27 号):默认 no-op
        private Consumer<World> hubWorldClearer = world -> { };              // ★v1.9(27 号):默认 no-op
        private Consumer<SessionEvent> sessionEventMirror;
        private Logger logger = Logger.getLogger(SessionServiceImpl.class.getName());

        /** 模式查询口(生产 = 模式注册表;测试 = 可控假实现)。 */
        public Builder modes(ModeLookup modes) {
            this.modes = Objects.requireNonNull(modes, "modes");
            return this;
        }

        /** 世界端口(2.4 前注入占位实现,接口不变)。 */
        public Builder worlds(WorldPort worlds) {
            this.worlds = Objects.requireNonNull(worlds, "worlds");
            return this;
        }

        public Builder maps(MapRegistry maps) {
            this.maps = Objects.requireNonNull(maps, "maps");
            return this;
        }

        /** 模式配置源(与模式注册表同源;生产 = 配置仓库)。 */
        public Builder configSource(Function<String, MatchModeConfig> configSource) {
            this.configSource = Objects.requireNonNull(configSource, "configSource");
            return this;
        }

        /** 会话策略模板(create 按此建会话;从 {@code config.yml} 会话块构造)。 */
        public Builder policy(SessionPolicy policy) {
            this.policy = Objects.requireNonNull(policy, "policy");
            return this;
        }

        /**
         * 回退模式 id(配置注入,引擎零字面量;§14.21)。★v1.13/31 号:默认值 = 出厂
         * 空模式 id(SPI 注册兜底);空串/未注册 = <b>无效回退</b>——回退需求时
         * SEVERE + 强制解散(fallbackSwitch 单点判据);仅 {@code null} 视为装配缺失。
         */
        public Builder fallbackModeId(String fallbackModeId) {
            this.fallbackModeId = Objects.requireNonNull(fallbackModeId, "fallbackModeId");
            return this;
        }

        /** 引擎服务集(造实例的末位参数)。 */
        public Builder engineServices(EngineServices engineServices) {
            this.engineServices = Objects.requireNonNull(engineServices, "engineServices");
            return this;
        }

        /** 主线程执行器(生产行 = Threads 回程口;离线测试默认直通)。 */
        public Builder mainExecutor(Executor mainExecutor) {
            this.mainExecutor = Objects.requireNonNull(mainExecutor, "mainExecutor");
            return this;
        }

        /** 成员传送主城出生点函数(= 服务器主世界出生点,由装配方提供;L0 注入)。 */
        public Builder hubRelocator(Consumer<Collection<UUID>> hubRelocator) {
            this.hubRelocator = Objects.requireNonNull(hubRelocator, "hubRelocator");
            return this;
        }

        /** 通用安全态清理函数(三级回退兜底:清模式/隐身等,L0 注入)。 */
        public Builder safeStateCleaner(Consumer<Collection<UUID>> safeStateCleaner) {
            this.safeStateCleaner = Objects.requireNonNull(safeStateCleaner, "safeStateCleaner");
            return this;
        }

        /**
         * 切换世界清场函数(★v1.9/27 号,L0 注入):旧世界内全部在线玩家
         * ({@code getPlayers()})传往目标世界出生点({@code getSpawnLocation()})。
         * 调用点 = B3.5(同世界复用,先行于组件摆放)与 B5 finally(异图,殿后于
         * 完成事件、release 之前);抛异常只 WARN 不阻塞切换。默认 no-op。
         */
        public Builder worldClearer(BiConsumer<World, World> worldClearer) {
            this.worldClearer = Objects.requireNonNull(worldClearer, "worldClearer");
            return this;
        }

        /**
         * 解散世界清场函数(★v1.9/27 号,L0 注入):地图世界内全部在线玩家传往主世界
         * 出生点(与 {@code hubRelocator} 同源 hubSpawn() 单一口径)。调用点 =
         * dissolveNow 的 finally(先名册后世界,discardSession 之前);失败不阻塞
         * 解散。默认 no-op。
         */
        public Builder hubWorldClearer(Consumer<World> hubWorldClearer) {
            this.hubWorldClearer = Objects.requireNonNull(hubWorldClearer, "hubWorldClearer");
            return this;
        }

        /**
         * 全局汇聚镜像(★v1.1-D3;可空 = 不挂):每个新会话构造后立即
         * {@code setMirror(本消费者)}——会话事件照旧进本会话总线,同时镜像到全局
         * 汇聚总线(L0 {@code SessionEventBus});镜像异常隔离在总线侧(红线)。
         */
        public Builder sessionEventMirror(Consumer<SessionEvent> sessionEventMirror) {
            this.sessionEventMirror = sessionEventMirror;   // null 合法 = 不挂镜像(离线测试默认)
            return this;
        }

        /** 日志注入(测试捕获断言用)。 */
        public Builder logger(Logger logger) {
            this.logger = Objects.requireNonNull(logger, "logger");
            return this;
        }

        public SessionServiceImpl build() {
            StringBuilder missing = new StringBuilder();
            if (modes == null) missing.append("modes ");
            if (worlds == null) missing.append("worlds ");
            if (maps == null) missing.append("maps ");
            if (configSource == null) missing.append("configSource ");
            if (policy == null) missing.append("policy ");
            if (fallbackModeId == null) missing.append("fallbackModeId ");   // 空串合法(无效回退语义,31 号),仅 null 缺失
            if (engineServices == null) missing.append("engineServices ");
            if (!missing.isEmpty()) {
                throw new IllegalStateException("SessionServiceImpl 依赖缺失: " + missing);
            }
            return new SessionServiceImpl(this);
        }
    }
}
