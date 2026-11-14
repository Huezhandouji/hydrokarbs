package com.hydrokarbs.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hydrokarbs.engine.AttributeBag;
import com.hydrokarbs.engine.component.ComponentDependencyException;
import com.hydrokarbs.engine.component.MatchModeComponent;
import com.hydrokarbs.engine.component.SessionComponent;
import com.hydrokarbs.engine.component.SessionComponentContributionRegistry;
import com.hydrokarbs.engine.event.SessionEventBus;
import com.hydrokarbs.engine.instance.MatchModeInstance;
import com.hydrokarbs.engine.mode.MatchMode;
import com.hydrokarbs.engine.mode.MatchModeMetadata;
import com.hydrokarbs.engine.mode.MatchModeProvider;
import com.hydrokarbs.engine.mode.MatchModeRegistry;
import com.hydrokarbs.engine.port.EngineServices;
import com.hydrokarbs.engine.port.MatchModeInstanceServices;
import com.hydrokarbs.engine.session.DissolveReason;
import com.hydrokarbs.engine.session.EndResult;
import com.hydrokarbs.engine.session.Handoff;
import com.hydrokarbs.engine.session.JoinResult;
import com.hydrokarbs.engine.session.LeaveReason;
import com.hydrokarbs.engine.session.MatchModeConfig;
import com.hydrokarbs.engine.session.MatchModeSelection;
import com.hydrokarbs.engine.session.Session;
import com.hydrokarbs.engine.session.SessionId;
import com.hydrokarbs.engine.session.SessionPolicy;
import com.hydrokarbs.engine.session.SessionService;
import com.hydrokarbs.engine.session.SessionSpec;
import com.hydrokarbs.engine.session.SwitchResult;
import com.hydrokarbs.api.view.MatchModeInfo;
import com.hydrokarbs.map.MapRegistry;
import com.hydrokarbs.testkit.OfflineAssembly;
import com.hydrokarbs.testkit.SessionProbeComponent;
import com.hydrokarbs.testkit.TestComponent;
import com.hydrokarbs.testkit.TestPlugins;

/**
 * 跨插件模式可注册(★20;★v1.6 起经 api 显式注册面承载,离线):
 * <ol>
 *   <li>①合法 provider 注册后入目录({@code modes()} 含对应 {@link MatchModeInfo});</li>
 *   <li>②可被 switchMode 选中(fake SessionService 记录调用,断言目标 id 与 force);</li>
 *   <li>③坏 provider(缺必需依赖/build 抛/重复 id)响亮抛 + 不进注册表(§2.6);</li>
 *   <li>④unregisterMode 摘除 + 受影响会话恰一次回退;fallback 空/未注册 → 解散兜底;
 *       越权摘响亮拒、未知幂等(§14.17);</li>
 *   <li>④c 会话组件显式面按提供方收录/摘除(14.38);</li>
 *   <li>⑤主仓生产源零示例字面量 {@code their_mode}(§14.13;测试内源码扫描实现)。</li>
 * </ol>Plugin 以动态代理占位;零 Bukkit 静态调用。
 */
class ThirdPartyModeRegistrationTest {

    /** 测试用第三方模式 id(中性;their_mode 字面量只住 examples/,⑤ 守卫)。 */
    private static final String PROBE = "probe_mode";
    private static final String FALLBACK = "fallback_mode";

    private final List<LogRecord> logs = new ArrayList<>();
    private final List<String> hookLog = new ArrayList<>();
    private MatchModeRegistry registry;
    private Logger logger;
    private SessionComponentContributionRegistry contributions;
    /** Impl 类级日志的捕获 handler(回退 WARN/版本打点断言面;用毕摘除防泄漏)。 */
    private Handler implLogHandler;

    @BeforeEach
    void setUp() {
        logger = Logger.getLogger(ThirdPartyModeRegistrationTest.class.getName() + "-"
                + System.nanoTime());
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                logs.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        // 配置源:全回落缺省(装载链由 ConfigRepositoryTest 覆盖,此处只测注册面)
        registry = new MatchModeRegistry(id -> OfflineAssembly.cfg(), logger);
        contributions = new SessionComponentContributionRegistry();
    }

    @AfterEach
    void tearDown() {
        if (implLogHandler != null) {
            Logger.getLogger(HydrokarbsApiImpl.class.getName()).removeHandler(implLogHandler);
            implLogHandler = null;
        }
    }

    // ── 假件 ─────────────────────────────────────────────────────

    /** 合法假工厂:组件表全部自满足(缺省零组件;人数窗 1~16 对齐 TheirPlugin 示例声明)。 */
    private static MatchModeProvider provider(String id, Set<String> mapTags,
                                              MatchModeComponent.Specification<?>... specs) {
        return new MatchModeProvider() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public MatchMode build(MatchModeConfig cfg) {
                MatchMode.Builder builder = new MatchMode.Builder(id)
                        .mapTags(mapTags)
                        .metadata(MatchModeMetadata.addTo(new AttributeBag.Builder(), false, 1, 16).build());
                int index = 1;
                for (MatchModeComponent.Specification<?> spec : specs) {
                    builder.addComponent("c" + index++, spec);
                }
                return builder.build();
            }
        };
    }

    /** build 即抛的假工厂(第三方工厂代码异常的最坏形态)。 */
    private static MatchModeProvider brokenProvider(String id) {
        return new MatchModeProvider() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public MatchMode build(MatchModeConfig cfg) {
                throw new IllegalStateException("第三方工厂 build 异常(测试注入)");
            }
        };
    }

    /** 无人提供的组件类型(制造「依赖校验失败」的坏模板;先例 MatchModeRegistryTest)。 */
    private abstract static class Unresolved extends MatchModeComponent {

        Unresolved(String id, MatchModeInstanceServices svc) {
            super(id, svc);
        }
    }

    /**
     * 当前实例固定为给定模式实例的会话桩:覆写 {@code currentInstance} 返回
     * 真实实例({@code createInstance} 造,零钩子触发——awake/start 属切换执行段,
     * 这里只供 {@code modeId()} 读口)。
     */
    private static final class FixedCurrentSession extends Session {

        private final MatchModeInstance current;

        FixedCurrentSession(String id, MatchMode mode) {
            super(new SessionSpec(new SessionId(id), mode.id(), List.of()),
                    SessionPolicy.defaults(SessionPolicy.Topology.ROTATION));
            this.current = mode.createInstance(this, OfflineAssembly.map(), OfflineAssembly.cfg(),
                    null, Handoff.empty(), new EngineServices(new OfflineAssembly.FakePlatform()));
        }

        @Override
        public Optional<MatchModeInstance> currentInstance() {
            return Optional.of(current);
        }
    }

    /** switchMode/dissolve 调用记录行。 */
    private record SwitchCall(SessionId sessionId, MatchModeSelection selection, Handoff payload) {
    }

    private record DissolveCall(SessionId sessionId, DissolveReason reason) {
    }

    /**
     * 会话服务记录桩:switchMode 记录调用并按脚本回结果(缺省 Succeeded);
     * 摘除回退编排的「恰一次/目标/force」断言面。
     */
    private static final class RecordingService implements SessionService {

        final List<SwitchCall> switches = new ArrayList<>();
        final List<DissolveCall> dissolves = new ArrayList<>();
        Collection<Session> sessions = List.of();
        SwitchResult scripted;

        void setSessions(Collection<Session> value) {
            this.sessions = value;
        }

        @Override
        public Collection<Session> all() {
            return sessions;
        }

        @Override
        public Optional<Session> session(SessionId id) {
            for (Session session : sessions) {
                if (session.id().equals(id)) {
                    return Optional.of(session);
                }
            }
            return Optional.empty();
        }

        @Override
        public Optional<Session> sessionOf(UUID player) {
            return Optional.empty();
        }

        @Override
        public CompletableFuture<SwitchResult> switchMode(SessionId id, MatchModeSelection target,
                                                          Handoff payload) {
            switches.add(new SwitchCall(id, target, payload));
            SwitchResult result = scripted != null ? scripted
                    : new SwitchResult.Succeeded(id, "from", target.modeId(), EndResult.nowhere());
            return CompletableFuture.completedFuture(result);
        }

        @Override
        public Session create(SessionSpec spec) {
            throw new UnsupportedOperationException("测试桩不消费");
        }

        @Override
        public JoinResult join(SessionId id, UUID player) {
            throw new UnsupportedOperationException("测试桩不消费");
        }

        @Override
        public void leave(UUID player, LeaveReason reason) {
            // 测试桩不消费
        }

        @Override
        public void dissolve(SessionId id, DissolveReason reason) {
            dissolves.add(new DissolveCall(id, reason));
        }
    }

    /** 捕获 {@code HydrokarbsApiImpl} 类级日志(回退 WARN/版本协商打点断言面;@AfterEach 摘除)。 */
    private void captureImplLog() {
        implLogHandler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                logs.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        Logger.getLogger(HydrokarbsApiImpl.class.getName()).addHandler(implLogHandler);
    }

    private boolean hasLevelContaining(Level level, String fragment) {
        return logs.stream().anyMatch(r -> r.getLevel() == level
                && formatted(r).contains(fragment));
    }

    /** 参数化日志行取格式化文本(java.util.logging 的 {0} 占位需 MessageFormat 展开)。 */
    private static String formatted(LogRecord record) {
        Object[] params = record.getParameters();
        return params == null ? String.valueOf(record.getMessage())
                : java.text.MessageFormat.format(record.getMessage(), params);
    }

    // ── ① 合法 provider → 目录 + api 视图 ────────────────────────

    @Test
    void registeredProviderEntersCatalogAndApiView() {
        HydrokarbsApiImpl api = new HydrokarbsApiImpl(new RecordingService(), registry,
                new MapRegistry(),
                new SessionEventBus(),
                new SessionComponentContributionRegistry(), Runnable::run, () -> "");
        api.registerMode(TestPlugins.named("their-plugin"), provider(PROBE, Set.of("demo")));

        assertEquals(PROBE, registry.require(PROBE).id(), "注册表目录含其 id(§16.2-★20)");
        assertTrue(registry.all().stream().anyMatch(mode -> mode.id().equals(PROBE)));

        // 经 Impl modes():第三方模式入目录后服主可见(§7.1)
        Optional<MatchModeInfo> info = api.mode(PROBE);
        assertTrue(info.isPresent(), "mode(id) 命中第三方模式");
        assertEquals(PROBE, info.get().id());
        assertTrue(api.modes().stream().anyMatch(mode -> mode.id().equals(PROBE)),
                "modes() 与出厂/其他模式并列、无特判顺序(14.21)");
    }

    // ── ② 可被 switchMode 选中 ───────────────────────────────────

    @Test
    void thirdPartyModeSelectableThroughApiSwitch() {
        HydrokarbsApiImpl api = new HydrokarbsApiImpl(new RecordingService(), registry,
                new MapRegistry(),
                new SessionEventBus(),
                new SessionComponentContributionRegistry(), Runnable::run, () -> "");
        api.registerMode(TestPlugins.named("their-plugin"), provider(PROBE, Set.of("demo")));

        // 切换阶段 A 目标查找口(require,§3.2-A1)对第三方 id 不拒——「可被选中」的静态前提
        assertDoesNotThrow(() -> registry.require(PROBE));

        RecordingService fake = new RecordingService();
        fake.setSessions(List.of(OfflineAssembly.session("s1")));
        HydrokarbsApiImpl switching = new HydrokarbsApiImpl(fake, registry,
                new MapRegistry(),
                new SessionEventBus(),
                new SessionComponentContributionRegistry(), Runnable::run, () -> "");

        Handoff payload = Handoff.empty();
        CompletableFuture<SwitchResult> future = switching.session("s1").orElseThrow()
                .switchMode(new MatchModeSelection(PROBE, Optional.empty(), true), payload);

        SwitchResult result = future.join();                       // 直接执行器 → 内联完成
        assertInstanceOf(SwitchResult.Succeeded.class, result, "fake 回 Succeeded(★20 口径)");
        assertEquals(1, fake.switches.size(), "恰一次委托");
        SwitchCall call = fake.switches.get(0);
        assertEquals(new SessionId("s1"), call.sessionId(), "目标会话正确");
        assertEquals(PROBE, call.selection().modeId(), "目标 id = 第三方模式");
        assertTrue(call.selection().force(), "force 语义原样透传(§3.5 外部调用方显式选择)");
        assertSame(payload, call.payload(), "交接载荷原样透传");
    }

    // ── ③ 坏 provider:响亮抛 + 不进注册表(★v1.6 显式注册统一响亮口径) ──

    @Test
    void dependencyFailureIsLoudAndNotRegistered() {
        HydrokarbsApiImpl api = new HydrokarbsApiImpl(new RecordingService(), registry,
                new MapRegistry(),
                new SessionEventBus(),
                new SessionComponentContributionRegistry(), Runnable::run, () -> "");
        MatchModeProvider bad = provider("bad_probe", Set.of("demo"),
                TestComponent.requiring(hookLog, Unresolved.class));   // 无人提供 → 坏模板

        ComponentDependencyException thrown = assertThrows(ComponentDependencyException.class,
                () -> api.registerMode(TestPlugins.named("their-plugin"), bad),
                "依赖校验失败响亮抛(★v1.6:显式注册可捕获,onEnable 失败 = 拒载信号)");
        assertNotNull(thrown.getMessage(), "异常带 detail(引擎 verifyDependencies 既有口径)");

        assertThrows(NoSuchElementException.class, () -> registry.require("bad_probe"),
                "坏模板不进注册表(§2.6)");
        assertTrue(registry.all().isEmpty());
    }

    @Test
    void buildFailureIsLoudAndNotRegistered() {
        HydrokarbsApiImpl api = new HydrokarbsApiImpl(new RecordingService(), registry,
                new MapRegistry(),
                new SessionEventBus(),
                new SessionComponentContributionRegistry(), Runnable::run, () -> "");

        assertThrows(IllegalStateException.class,
                () -> api.registerMode(TestPlugins.named("their-plugin"), brokenProvider("broken_probe")),
                "第三方工厂 build 异常原样上抛(★v1.6 响亮口径)");

        assertThrows(NoSuchElementException.class, () -> registry.require("broken_probe"));
        assertTrue(registry.all().isEmpty(), "build 失败不留半注册态");
    }

    @Test
    void duplicateIdAtRegistrationIsLoudAndKeepsFirstArrival() {
        HydrokarbsApiImpl api = new HydrokarbsApiImpl(new RecordingService(), registry,
                new MapRegistry(),
                new SessionEventBus(),
                new SessionComponentContributionRegistry(), Runnable::run, () -> "");
        api.registerMode(TestPlugins.named("first-plugin"), provider(PROBE, Set.of("first")));

        assertThrows(IllegalStateException.class,
                () -> api.registerMode(TestPlugins.named("second-plugin"), provider(PROBE, Set.of("second"))),
                "重复 id 响亮抛(绝不静默覆盖,§2.6/§12.2)");

        assertEquals(Set.of("first"), registry.require(PROBE).mapTags(), "先到者保留");
        assertEquals(1, registry.all().size());
    }

    // ── ④ 摘除:目录摘除 + 受影响会话回退/解散 ────────────────────

    @Test
    void unregisterRemovesCatalogAndRedirectsAffectedSessionToFallback() {
        RecordingService fake = new RecordingService();
        HydrokarbsApiImpl api = new HydrokarbsApiImpl(fake, registry,
                new MapRegistry(),
                new SessionEventBus(), contributions, Runnable::run, () -> FALLBACK);
        api.registerMode(TestPlugins.named("fallback-plugin"), provider(FALLBACK, Set.of("demo")));
        var provider = TestPlugins.named("their-plugin");
        api.registerMode(provider, provider(PROBE, Set.of("demo")));   // fallback 先注册(可用性判定)

        Session affected = new FixedCurrentSession("s-affected", registry.require(PROBE));
        Session unaffected = OfflineAssembly.session("s-unaffected");
        fake.setSessions(List.of(affected, unaffected));
        api.unregisterMode(provider, PROBE);

        assertFalse(registry.all().stream().anyMatch(mode -> mode.id().equals(PROBE)),
                "目录摘除(modes() 视角同源,① 已验通路)");
        assertEquals(1, fake.switches.size(), "受影响会话恰一次回退(不重复编排)");
        SwitchCall call = fake.switches.get(0);
        assertEquals(affected.id(), call.sessionId(), "只有当前模式 ∈ 被摘集的会话被打扰");
        assertEquals(FALLBACK, call.selection().modeId(), "回退目标 = 配置的 fallback-mode(§14.21)");
        assertTrue(call.selection().force(), "回退带 force(豁免 min-dwell,§3.5)");
        assertNotNull(call.payload(), "Handoff.empty()");
        assertTrue(fake.dissolves.isEmpty(), "fallback 可用时不解散");
        // 未受影响会话零打扰:切换恰一次且 sessionId 命中受影响会话(上两行),解散记录为空(上一行)
    }

    @Test
    void unregisterWithBlankFallbackDissolvesSession() {
        RecordingService fake = new RecordingService();
        HydrokarbsApiImpl api = new HydrokarbsApiImpl(fake, registry,
                new MapRegistry(),
                new SessionEventBus(), contributions, Runnable::run, () -> "");   // R3:默认空 = 无 fallback
        var provider = TestPlugins.named("their-plugin");
        api.registerMode(provider, provider(PROBE, Set.of("demo")));
        fake.setSessions(List.of(new FixedCurrentSession("s1", registry.require(PROBE))));

        api.unregisterMode(provider, PROBE);

        assertEquals(1, fake.dissolves.size(), "fallback 空 → 直接二级 dissolve(★v1.3 R3 空值语义)");
        assertEquals(new DissolveCall(new SessionId("s1"), DissolveReason.FALLBACK_DISSOLVE),
                fake.dissolves.get(0));
        assertTrue(fake.switches.isEmpty(), "不发起切换");
    }

    @Test
    void unregisterWithUnregisteredFallbackDissolvesSession() {
        RecordingService fake = new RecordingService();
        HydrokarbsApiImpl api = new HydrokarbsApiImpl(fake, registry,
                new MapRegistry(),
                new SessionEventBus(), contributions, Runnable::run, () -> "ghost_mode");   // 指向未注册 id → 不可用
        var provider = TestPlugins.named("their-plugin");
        api.registerMode(provider, provider(PROBE, Set.of("demo")));
        fake.setSessions(List.of(new FixedCurrentSession("s1", registry.require(PROBE))));

        api.unregisterMode(provider, PROBE);

        assertEquals(1, fake.dissolves.size());
        assertEquals(DissolveReason.FALLBACK_DISSOLVE, fake.dissolves.get(0).reason());
    }

    @Test
    void rejectedFallbackSwitchWarnsAndDoesNotSpin() {
        captureImplLog();                          // 回退 WARN 走 Impl 类级日志
        RecordingService fake = new RecordingService();
        HydrokarbsApiImpl api = new HydrokarbsApiImpl(fake, registry,
                new MapRegistry(),
                new SessionEventBus(), contributions, Runnable::run, () -> FALLBACK);
        api.registerMode(TestPlugins.named("fallback-plugin"), provider(FALLBACK, Set.of("demo")));
        var provider = TestPlugins.named("their-plugin");
        api.registerMode(provider, provider(PROBE, Set.of("demo")));
        fake.scripted = new SwitchResult.Rejected(SwitchResult.Reason.SWITCH_IN_PROGRESS, "in flight");
        fake.setSessions(List.of(new FixedCurrentSession("s1", registry.require(PROBE))));

        api.unregisterMode(provider, PROBE);

        assertEquals(1, fake.switches.size(), "拒绝后放弃,不在事件栈自旋重试(§14.17)");
        assertTrue(hasLevelContaining(Level.WARNING, "SWITCH_IN_PROGRESS"), "WARN 观测拒绝");
    }

    @Test
    void unregisterForeignModeIsRefusedAndUnknownIdIsNoOp() {
        HydrokarbsApiImpl api = new HydrokarbsApiImpl(new RecordingService(), registry,
                new MapRegistry(),
                new SessionEventBus(), contributions, Runnable::run, () -> "");
        api.registerMode(TestPlugins.named("other-plugin"), provider(PROBE, Set.of("demo")));
        var provider = TestPlugins.named("their-plugin");

        // 越权:目录里有但不是本提供方注册的(★v1.6 响亮拒)
        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> api.unregisterMode(provider, PROBE),
                "id 属于其他提供方 → 响亮拒(★v1.6)");
        assertTrue(refused.getMessage().contains(PROBE), "detail 点名模式 id: " + refused.getMessage());
        assertEquals(PROBE, registry.require(PROBE).id(), "越权摘除不落地");

        // 未知 id:幂等 no-op(★v1.6)
        assertDoesNotThrow(() -> api.unregisterMode(provider, "never_registered"),
                "id 不存在 → 幂等 no-op(★v1.6)");
        assertTrue(registry.all().size() == 1, "no-op 不扰动目录");
    }

    // ── ④c D14 通道一显式面:收录/摘除按提供方插件键(14.38) ──────────────

    @Test
    void registeredContributionsRemovedByProviderPluginKey() {
        HydrokarbsApiImpl api = new HydrokarbsApiImpl(new RecordingService(), registry,
                new MapRegistry(),
                new SessionEventBus(), contributions, Runnable::run, () -> "");
        var provider = TestPlugins.named("their-plugin");
        SessionComponent.Specification<?> spec = new SessionProbeComponent.Spec(new ArrayList<>());
        api.registerSessionComponents(provider, List.of(spec));

        assertFalse(contributions.isEmpty(), "前置:显式注册已收录贡献");
        api.unregisterSessionComponents(provider);   // 插件 disable 兜底同键(14.38)
        assertTrue(contributions.isEmpty(), "按提供方插件键摘除(D14/14.38)");
    }

    @Test
    void reRegistrationReplacesContributionsWithoutGhostCollision() {
        HydrokarbsApiImpl api = new HydrokarbsApiImpl(new RecordingService(), registry,
                new MapRegistry(),
                new SessionEventBus(), contributions, Runnable::run, () -> "");
        var provider = TestPlugins.named("their-plugin");
        SessionComponent.Specification<?> first = new SessionProbeComponent.Spec(new ArrayList<>());
        SessionComponent.Specification<?> second = new SessionProbeComponent.Spec(new ArrayList<>());
        api.registerSessionComponents(provider, List.of(first));

        api.registerSessionComponents(provider, List.of(second));   // 同提供方重注册(幂等重收录)

        assertEquals(List.of(second), contributions.merged(), "合并视图 = 新贡献(旧条目被同键摘除)");
    }

    // ── ⑤ 主仓生产源零第三方示例字面量 ───────────────────────────

    @Test
    void mainSourcesContainNoThirdPartyModeLiteral() throws IOException {
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                if (Files.readString(file).contains("their_mode")) {
                    violations.add(file.toString());
                }
            }
        }
        assertTrue(violations.isEmpty(),
                "主仓生产源不得出现 their_mode 字面量(§14.13;示例只住 examples/TheirPlugin): "
                        + violations);
    }
}
