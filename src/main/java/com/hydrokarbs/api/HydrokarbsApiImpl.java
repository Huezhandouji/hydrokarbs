package com.hydrokarbs.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.kyori.adventure.text.minimessage.MiniMessage;

import com.hydrokarbs.api.view.MapInfo;
import com.hydrokarbs.api.view.MatchModeInfo;
import com.hydrokarbs.engine.component.SessionComponent;
import com.hydrokarbs.engine.component.SessionComponentContributionRegistry;
import com.hydrokarbs.engine.event.SessionEvent;
import com.hydrokarbs.engine.event.SessionEventBus;
import com.hydrokarbs.engine.instance.MatchModeInstance;
import com.hydrokarbs.engine.mode.MatchMode;
import com.hydrokarbs.engine.mode.MatchModeMetadata;
import com.hydrokarbs.engine.mode.MatchModeProvider;
import com.hydrokarbs.engine.mode.MatchModeRegistry;
import com.hydrokarbs.engine.session.DissolveReason;
import com.hydrokarbs.engine.session.Handoff;
import com.hydrokarbs.engine.session.LeaveReason;
import com.hydrokarbs.engine.session.MatchModeSelection;
import com.hydrokarbs.engine.session.Session;
import com.hydrokarbs.engine.session.SessionId;
import com.hydrokarbs.engine.session.SessionService;
import com.hydrokarbs.engine.session.SessionSpec;
import com.hydrokarbs.engine.session.SwitchResult;
import com.hydrokarbs.map.MapDefinition;
import com.hydrokarbs.map.MapRegistry;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

/**
 * {@link HydrokarbsApi} 实现体:薄委托(07 §7.2)——「查表 → 转换 → 委托」,零业务
 * 分支。查询/建会话只做解析与产句柄({@link SessionHandleImpl},★v1.15/33 号:
 * 控制与成员操作随句柄走,不在本类重复);模式/会话组件显式注册走注册表/收录表
 * 响亮闸,归属簿记 {@code modeClaims} 在本类;摘除回退编排也在本类(§13.4,
 * 一份不写两处)。
 *
 * <p><b>依赖面</b>(§16.2 允许集内):engine 契约 + 地图读面 + 事件总线 + 贡献
 * 收录表 + MiniMessage;主线程执行器构造注入(生产行 = {@code Threads::runOnMain},
 * 本类不 import {@code util.Threads})。
 *
 * <p><b>L0 装配面</b>(不在冻结面):{@code unregisterModesOf}/{@code unregisterMapsOf}/
 * {@code unregisterSessionComponentsOf} 仅供 {@code PluginDisableEvent} 兜底监听(14.38)。
 *
 * <p><b>线程契约</b>:本类各法均假定主线程(句柄面的异步纪律见
 * {@code SessionHandleImpl#switchMode});订阅回调在事件发布线程。
 */
public final class HydrokarbsApiImpl implements HydrokarbsApi {

    private static final Logger LOG = Logger.getLogger(HydrokarbsApiImpl.class.getName());

    /** MiniMessage 实例建一次复用(papermc 纪律;序列化只在模式声明文案的转换点)。 */
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final SessionService sessionService;
    private final MatchModeRegistry modeRegistry;
    private final MapRegistry mapRegistry;
    private final SessionEventBus globalEvents;
    private final SessionComponentContributionRegistry contributions;
    private final Executor mainExecutor;
    /** 回退目标配置读口(惰性;生产 = {@code cfg.get("session.fallback-mode", "")},§14.21)。 */
    private final Supplier<String> fallbackModeId;

    /**
     * 模式提供方归属映射(★v1.6):提供方(按实例身份键)→ 其注册的模式 id 集。
     * 显式摘除只准摘自己注册的 id(越权响亮拒);提供方插件 disable → 兜底监听
     * 逐 id 摘除并做回退编排。主线程单线程访问。
     */
    private final Map<Object, Set<String>> modeClaims = new LinkedHashMap<>();

    /**
     * 依赖全量构造注入:engine 契约({@code sessionService}/{@code modeRegistry})、
     * 地图目录({@code mapRegistry})、汇聚总线({@code globalEvents})、贡献收录表
     * ({@code contributions})、主线程执行器({@code mainExecutor},生产行 =
     * {@code Threads::runOnMain})、回退模式 id 读口({@code fallbackModeId},惰性,
     * 不写死任何 id,§14.21)。null 即抛。
     */
    public HydrokarbsApiImpl(SessionService sessionService, MatchModeRegistry modeRegistry,
                             MapRegistry mapRegistry, SessionEventBus globalEvents,
                             SessionComponentContributionRegistry contributions,
                             Executor mainExecutor, Supplier<String> fallbackModeId) {
        this.sessionService = Objects.requireNonNull(sessionService, "sessionService");
        this.modeRegistry = Objects.requireNonNull(modeRegistry, "modeRegistry");
        this.mapRegistry = Objects.requireNonNull(mapRegistry, "mapRegistry");
        this.globalEvents = Objects.requireNonNull(globalEvents, "globalEvents");
        this.contributions = Objects.requireNonNull(contributions, "contributions");
        this.mainExecutor = Objects.requireNonNull(mainExecutor, "mainExecutor");
        this.fallbackModeId = Objects.requireNonNull(fallbackModeId, "fallbackModeId");
    }

    // ── HydrokarbsApi:查询(★v1.15/33 号:解析口产句柄,不产快照) ─────────

    @Override
    public Optional<SessionHandle> sessionOf(@NotNull UUID player) {
        return sessionService.sessionOf(player).map(this::handleOf);
    }

    @Override
    public Optional<SessionHandle> session(@NotNull String sessionId) {
        return sessionService.session(new SessionId(sessionId)).map(this::handleOf);
    }

    @Override
    public List<SessionHandle> sessions() {
        List<SessionHandle> handles = new ArrayList<>();
        for (Session session : sessionService.all()) {
            handles.add(handleOf(session));
        }
        return List.copyOf(handles);
    }

    // ── HydrokarbsApi:控制 ────────────────────────────────────────────

    @Override
    public SessionHandle createSession(@NotNull SessionRecipe recipe) {
        Objects.requireNonNull(recipe, "recipe");
        // 未知模式 → 注册表既有响亮抛(require,§12.1)
        modeRegistry.require(recipe.initialModeId());
        // 合并序(D14):①已收录 provider 贡献(注册序)→ ②recipe 单次贡献。
        List<SessionComponent.Specification<?>> components = new ArrayList<>(contributions.merged());
        components.addAll(recipe.extraComponents());
        // 钉图置池首(非空白钉图并入会话地图池池首,首切换自动挑图按池序 §3.2-A3)
        List<String> pool = new ArrayList<>(recipe.mapPool());
        String pinned = recipe.initialMapId().orElse("").strip();
        if (!pinned.isEmpty() && !pool.contains(pinned)) {
            pool.add(0, pinned);
        }
        // 查重/缺依赖由引擎装配闸响亮拒(原样上抛)——不写第二套校验;抛时句柄不存在。
        Session created = sessionService.create(
                new SessionSpec(generatedId(), recipe.initialModeId(), pool, components));
        return handleOf(created);
    }

    @Override
    public boolean leaveSession(@NonNull UUID player) {
        Objects.requireNonNull(player, "player");
        // 玩家域单口(★v1.16/34 号):解析现属 → KICK 立即移除(true 时已不在任何
        // 名册——宽限语义 QUIT 属断线管线,boolean 收口不容「true 但仍在册」);
        // 不在任何会话 → false(同步早失败,幂等)。
        if (sessionService.sessionOf(player).isEmpty()) {
            return false;
        }
        sessionService.leave(player, LeaveReason.KICK);
        return true;
    }

    // ── HydrokarbsApi:会话事件订阅(D3;全局汇聚总线订阅 + 快照转换) ──────

    @Override
    public EventSubscription subscribeSessionEvents(@NotNull Consumer<SessionEventView> handler) {
        Objects.requireNonNull(handler, "handler");
        // 汇聚总线订阅,快照转换后回调;异常隔离、close 幂等注销均系总线既有语义(★43)。
        SessionEventBus.Subscription handle = globalEvents.subscribe(SessionEvent.class,
                event -> handler.accept(toEventView(event)));
        return handle::close;
    }

    // ── HydrokarbsApi:地图注册(★v1.2;目录委托 + 正用守卫) ────────────────

    @Override
    public MapInfo registerMap(org.bukkit.plugin.@NonNull Plugin provider, @NonNull MapDefinition map) {
        Objects.requireNonNull(provider, "provider");
        // 注册期校验(世界目录存在性带绝对路径/id 查重点名先到者)在目录内响亮抛(20-D3)
        return toMapInfo(mapRegistry.register(provider, map));
    }

    @Override
    public void unregisterMap(@NonNull Plugin provider, @NotNull String mapId) {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(mapId, "mapId");
        // 正用守卫在本类、目录类不查会话(7.5 决策 14):任一存活会话当前实例正用
        // 该图 → 摘除被拒,detail 点名会话(随会话打完自然退出后再摘)。
        String usedBy = findSessionUsing(mapId);
        if (usedBy != null) {
            throw new IllegalStateException("地图 " + mapId + " 正被会话 " + usedBy
                    + " 使用,摘除被拒(20-D4;未释放世界句柄无公开查询口,按当前实例落最小守卫"
                    + "——7.5 施工决策 14)");
        }
        mapRegistry.unregister(provider, mapId);    // 幂等 no-op 在目录侧(20-D4)
    }

    // ── HydrokarbsApi:模式显式注册(★v1.6;响亮闸 + 归属簿记) ──────────────

    @Override
    public MatchModeInfo registerMode(@NotNull Plugin provider, @NotNull MatchModeProvider factory) {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(factory, "factory");
        // 注册闸在注册表内响亮抛给调用方(§12.2)。
        LOG.log(Level.INFO, "Mode factory registered: id={0}", factory.id());
        MatchMode mode = modeRegistry.registerProvider(factory);
        modeClaims.computeIfAbsent(provider, key -> new LinkedHashSet<>()).add(mode.id());
        return toModeInfo(mode);
    }

    @Override
    public void unregisterMode(@NotNull Plugin provider, @NotNull String modeId) {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(modeId, "modeId");
        Set<String> claimed = modeClaims.get(provider);
        boolean owned = claimed != null && claimed.remove(modeId);
        if (!owned) {
            if (isRegistered(modeId)) {
                // 越权摘除:目录里有但不是本提供方注册的(含出厂模式)→ 响亮拒(★v1.6)
                throw new IllegalStateException("模式 " + modeId + " 不属于提供方 "
                        + provider.getName() + ",摘除被拒(只准摘自己注册的模式,★v1.6)");
            }
            return;                                    // id 不存在 → 幂等 no-op(★v1.6)
        }
        if (modeRegistry.unregister(modeId)) {
            runRemovalFallback(Set.of(modeId));        // 摘除回退编排(§13.4/§3.2 二级)
        }
    }

    // ── HydrokarbsApi:会话组件显式注册(★v1.6;D14 通道一的显式面) ──────────

    @Override
    public void registerSessionComponents(@NotNull Plugin provider,
                                          @NotNull List<SessionComponent.Specification<?>> specs) {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(specs, "specs");
        // 干跑合并校验在收录表内响亮抛(显式注册可捕获;注册失败 = 拒载信号)。
        contributions.register(provider, specs);
        LOG.log(Level.INFO, "Session component contributions registered: provider={0}, {1} spec(s)",
                new Object[] { provider.getName(), specs.size() });
    }

    @Override
    public void unregisterSessionComponents(@NotNull Plugin provider) {
        Objects.requireNonNull(provider, "provider");
        if (contributions.unregister(provider)) {      // 幂等:未收录静默(D14)
            LOG.log(Level.INFO, "Session component contributions removed: {0}",
                    provider.getName());
        }
    }

    // ── L0 装配面:PluginDisableEvent 兜底摘除(不在 HydrokarbsApi 冻结面;14.38) ──

    /**
     * 摘除某提供方注册的全部模式(兜底监听专用):逐 id 摘除并走 {@link HydrokarbsApi#unregisterMode}
     * 同一回退编排(会话回退不因插件下线而缺位,§13.4)。幂等(无注册 → no-op)。
     */
    public void unregisterModesOf(org.bukkit.plugin.Plugin provider) {
        Set<String> claimed = modeClaims.remove(provider);
        if (claimed == null || claimed.isEmpty()) {
            return;
        }
        for (String modeId : claimed) {
            if (modeRegistry.unregister(modeId)) {
                runRemovalFallback(Set.of(modeId));    // 逐 id 同一编排(unregisterMode 同款)
            }
        }
        LOG.log(Level.INFO, "Modes removed for provider: {0} → {1}",
                new Object[] { provider.getName(), claimed });
    }

    /**
     * 摘除某提供方注册的全部地图(兜底监听专用):逐张走 {@link HydrokarbsApi#unregisterMap}
     * 同一守卫——正用拒绝 = 保留并 WARN(20-D4)。幂等(无注册 → no-op)。
     */
    public void unregisterMapsOf(org.bukkit.plugin.Plugin provider) {
        for (String mapId : mapRegistry.registeredIdsOf(provider)) {
            try {
                unregisterMap(provider, mapId);
            } catch (RuntimeException refused) {
                LOG.log(Level.WARNING, "Map removal refused, map kept: {0} — {1}",
                        new Object[] { mapId, refused.getMessage() });
            }
        }
    }

    /**
     * 摘除某提供方的全部会话组件贡献(★v1.6;兜底监听专用):后续会话不再合并
     * (已装配会话不受影响——无运行期拆卸,随 dissolve 自然消亡,D14)。幂等。
     */
    public void unregisterSessionComponentsOf(org.bukkit.plugin.Plugin provider) {
        unregisterSessionComponents(provider);
    }

    // ── HydrokarbsApi:目录 ────────────────────────────────────────────

    @Override
    public List<MatchModeInfo> modes() {
        List<MatchModeInfo> snapshots = new ArrayList<>();
        for (MatchMode mode : modeRegistry.all()) {
            snapshots.add(toModeInfo(mode));
        }
        return List.copyOf(snapshots);
    }

    @Override
    public List<MapInfo> maps() {
        List<MapInfo> snapshots = new ArrayList<>();
        for (MapDefinition map : mapRegistry.all()) {
            snapshots.add(toMapInfo(map));
        }
        return List.copyOf(snapshots);
    }

    @Override
    public Optional<MatchModeInfo> mode(String modeId) {
        try {
            return Optional.of(toModeInfo(modeRegistry.require(modeId)));
        } catch (NoSuchElementException missing) {
            return Optional.empty();            // mode() 缺失 → empty
        }
    }

    // ── 转换(快照纪律:转换点一次性取值、集合拷贝、不持活引用——7.1 要点 1) ──

    /**
     * 会话 → 句柄(★v1.15/33 号):句柄自解析会话快照(单点在
     * {@code SessionHandleImpl#snapshot},一份不写两处),本类不再转快照。
     */
    private SessionHandle handleOf(Session session) {
        return new SessionHandleImpl(session.id(), sessionService, mainExecutor);
    }

    /** 模式 → 快照:displayName/description 经 MiniMessage 序列化(MINI 静态复用)。 */
    private MatchModeInfo toModeInfo(MatchMode mode) {
        return new MatchModeInfo(mode.id(), MINI.serialize(mode.displayName()),
                mode.description().stream().map(MINI::serialize).toList(), mode.icon(),
                Set.copyOf(mode.mapTags()), MatchModeMetadata.recordsHistory(mode.meta()),
                MatchModeMetadata.minParticipants(mode.meta()), MatchModeMetadata.maxParticipants(mode.meta()));
    }

    /** 地图 → 快照(★v1.8 视图收缩:id 路径派生,tags 取声明 {@link MapDefinition#tags()} 唯一读口,reset 取声明字段)。 */
    private MapInfo toMapInfo(MapDefinition map) {
        return new MapInfo(map.id(), map.tags(),
                map.resetStrategy().name());
    }

    /**
     * 引擎会话事件 → api 快照族(D3;八事件一一对应)。快照纪律:转换点一次性取值、
     * 枚举存 {@code name()}、不持活引用;{@code tick} 承载毫秒时间戳。
     * {@code ModeSwitchCompleted#ok} 恒 true——引擎仅在切换成功后发布(口径见
     * {@link SessionEventView} 接口注)。
     */
    private static SessionEventView toEventView(SessionEvent event) {
        if (event instanceof SessionEvent.SessionCreated created) {
            return new SessionEventView.Created(created.session(), created.occurredAtMillis());
        }
        if (event instanceof SessionEvent.MemberJoined joined) {
            return new SessionEventView.MemberJoined(joined.session(), joined.member(),
                    joined.occurredAtMillis());
        }
        if (event instanceof SessionEvent.MemberLeft left) {
            return new SessionEventView.MemberLeft(left.session(), left.member(),
                    left.reason().name(), left.occurredAtMillis());
        }
        if (event instanceof SessionEvent.MemberReconnected reconnected) {
            return new SessionEventView.MemberReconnected(reconnected.session(), reconnected.member(),
                    reconnected.occurredAtMillis());
        }
        if (event instanceof SessionEvent.ModeSwitchStarted started) {
            return new SessionEventView.ModeSwitchStarted(started.session(), started.fromMode(),
                    started.toMode(), started.occurredAtMillis());
        }
        if (event instanceof SessionEvent.ModeSwitchCompleted completed) {
            return new SessionEventView.ModeSwitchCompleted(completed.session(), completed.fromMode(),
                    completed.toMode(), true, completed.occurredAtMillis());
        }
        if (event instanceof SessionEvent.SessionDissolved dissolved) {
            return new SessionEventView.Dissolved(dissolved.session(), dissolved.reason().name(),
                    dissolved.occurredAtMillis());
        }
        SessionEvent.SessionComponentQuarantined quarantined =
                (SessionEvent.SessionComponentQuarantined) event;   // 封闭子族末支
        return new SessionEventView.ComponentQuarantined(quarantined.session(),
                quarantined.componentId(), quarantined.reason(), quarantined.occurredAtMillis());
    }

    /** 正用守卫查询:返回正在使用该图的存活会话 id(无则 null;当前实例口径,7.5 决策 14)。 */
    private String findSessionUsing(String mapId) {
        for (Session session : sessionService.all()) {
            String used = session.currentInstance()
                    .map(instance -> instance.view().map().id())
                    .orElse("");
            if (used.equals(mapId)) {
                return session.id().value();
            }
        }
        return null;
    }

    // ── 摘除回退编排(§13.4/§3.2 二级) ──────────────────────────────

    /**
     * 摘除回退编排:当前模式 id ∈ 被摘集的存活会话——回退模式(经 {@code fallbackModeId}
     * 惰性读,不写死任何 id,§14.21)已配置且已注册 → force 切换回退;空或未注册 →
     * 解散({@code FALLBACK_DISSOLVE},§3.2)。切换间隙无实例的会话不匹配、不重复编排。
     */
    private void runRemovalFallback(Set<String> removedIds) {
        String fallback = fallbackModeId.get().strip();
        boolean fallbackUsable = !fallback.isEmpty() && isRegistered(fallback);
        int redirected = 0;
        int dissolved = 0;
        for (Session session : sessionService.all()) {
            String current = session.currentInstance().map(MatchModeInstance::modeId).orElse("");
            if (!removedIds.contains(current)) {
                continue;                                  // 切换间隙无实例(empty)→ 不匹配、不重复编排
            }
            if (fallbackUsable) {
                initiateFallback(session, fallback);
                redirected++;
            } else {
                sessionService.dissolve(session.id(), DissolveReason.FALLBACK_DISSOLVE);
                dissolved++;
            }
        }
        LOG.log(Level.INFO, "Mode removal aftermath: {0} session(s) redirected, {1} dissolved (ids: {2})",
                new Object[] { redirected, dissolved, removedIds });
    }

    /**
     * 发起回退切换(force 豁免 min-dwell,§3.5):future 只挂 WARN 观测——被拒
     * (典型 {@code SWITCH_IN_PROGRESS})放弃不自旋(§14.17),执行由节拍器下一
     * tick 驱动(§16.4 三律①)。
     */
    private void initiateFallback(Session session, String fallback) {
        MatchModeSelection selection = new MatchModeSelection(fallback, Optional.empty(), true);
        sessionService.switchMode(session.id(), selection, Handoff.empty()).whenComplete((result, err) -> {
            if (err != null) {
                LOG.log(Level.WARNING, "Fallback switch threw for session " + session.id().value() + ": " + err);
                return;
            }
            if (result instanceof SwitchResult.Rejected rejected) {
                LOG.log(Level.WARNING, "Fallback to " + fallback + " rejected for session "
                        + session.id().value() + " [" + rejected.code() + "]: " + rejected.detail());
            }
        });
    }

    /** 模式 id 是否已注册(摘除后判定 fallback 可用性;走 all() 快照,不新增注册表面)。 */
    private boolean isRegistered(String modeId) {
        return modeRegistry.all().stream().anyMatch(mode -> mode.id().equals(modeId));
    }

    /** api 建会话 id = 框架前缀 + UUID(天然满足 {@code SessionId#shortCode} 安全字符集;§3.6)。 */
    private static SessionId generatedId() {
        return new SessionId("api-" + UUID.randomUUID());
    }
}
