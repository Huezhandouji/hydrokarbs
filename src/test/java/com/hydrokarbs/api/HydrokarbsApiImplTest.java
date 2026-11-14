package com.hydrokarbs.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.hydrokarbs.engine.AttributeBag;
import com.hydrokarbs.engine.component.SessionComponentContributionRegistry;
import com.hydrokarbs.engine.event.SessionEventBus;
import com.hydrokarbs.engine.mode.MatchMode;
import com.hydrokarbs.engine.mode.MatchModeMetadata;
import com.hydrokarbs.engine.mode.MatchModeProvider;
import com.hydrokarbs.engine.mode.MatchModeRegistry;
import com.hydrokarbs.engine.port.ResetStrategy;
import com.hydrokarbs.engine.session.DissolveReason;
import com.hydrokarbs.engine.session.EndResult;
import com.hydrokarbs.engine.session.Handoff;
import com.hydrokarbs.engine.session.JoinResult;
import com.hydrokarbs.engine.session.LeaveReason;
import com.hydrokarbs.engine.session.MatchModeSelection;
import com.hydrokarbs.engine.session.Session;
import com.hydrokarbs.engine.session.SessionId;
import com.hydrokarbs.engine.session.SessionMember;
import com.hydrokarbs.engine.session.SessionPolicy;
import com.hydrokarbs.engine.session.SessionSpec;
import com.hydrokarbs.engine.session.SessionService;
import com.hydrokarbs.engine.session.SessionState;
import com.hydrokarbs.engine.session.SwitchResult;
import com.hydrokarbs.api.view.MapInfo;
import com.hydrokarbs.api.view.MatchModeInfo;
import com.hydrokarbs.api.view.SessionInfo;
import com.hydrokarbs.map.MapDefinition;
import com.hydrokarbs.map.MapRegistry;
import com.hydrokarbs.testkit.MapTestKit;
import com.hydrokarbs.testkit.OfflineAssembly;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * {@link HydrokarbsApiImpl} 离线行为断言(7.1 施工文档「测试与验收」;★v1.3 适配:
 * fake 会话服务以测试源集假模式代目录,战绩透传断言随悬置不落地)。
 *
 * <p>覆盖六组:①快照不变性(取快照后改 fake 名册,旧 SessionInfo.members 不变;
 * ★v1.15/33 号:快照经句柄 {@code info()} 现取);②句柄失效(会话消亡)switchMode
 * 完成 Rejected(MODE_NOT_FOUND) 且存活会话无扰动(33 号语义迁移:「id 不存在」
 * 前移为解析点 empty);③线程断言(fake 记录的调用线程 = 注入的主线程执行器线程,
 * §14.1);④目录顺序 = 注册序(不排序不过滤,14.21);⑤createSession(recipe)
 * 未知模式响亮抛、成功路径返句柄、快照与 fake 会话一致;⑥视图 record 分量无引擎
 * 活类型(反射)。
 *
 * <p>离线:零 Bukkit import/静态调用(paper-api 仅 testRuntimeOnly 进运行期,
 * 同 OfflineAssembly.FakePlatform 先例);假会话用引擎真 Session(离线可构造)。
 */
class HydrokarbsApiImplTest {

    @TempDir
    Path tempDir;

    private MatchModeRegistry modeRegistry;
    private MapRegistry mapRegistry;
    private FakeSessionService fakeService;
    private ExecutorService mainExecutor;
    private HydrokarbsApi api;

    @BeforeEach
    void setUp() {
        // 配置源全回落缺省(复用 testkit 手法;装载链已由 ConfigRepositoryTest 覆盖)
        modeRegistry = new MatchModeRegistry(id -> OfflineAssembly.cfg());
        // 地图目录(★v1.8 去模板根:无参构造;造数走 MapTestKit,世界目录由用例先落盘)
        mapRegistry = new MapRegistry();
        fakeService = new FakeSessionService();
        // 注入单线程假主线程(生产行 = Threads::runOnMain;离线以可指认线程替代)
        mainExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "api-test-main");
            thread.setDaemon(true);
            return thread;
        });
        // 7.5 构造增量:⑨ 汇聚总线 + D14 贡献收录表(空表;本测试零消费,
        //   增量行为由 HydrokarbsApiMembersTest/HydrokarbsApiEventsTest/ContributionTest 承载);
        //   ★v1.6 构造增量:fallbackModeId 空读口(本测试不消费摘除回退编排);
        //   ★v1.7 起构造不再收能力标签机制件(25 号修正案)
        api = new HydrokarbsApiImpl(fakeService, modeRegistry, mapRegistry,
                new SessionEventBus(), new SessionComponentContributionRegistry(), mainExecutor,
                () -> "");
    }

    @AfterEach
    void tearDown() {
        mainExecutor.shutdownNow();
    }

    /** 声明目录落盘挂点(★v1.8:无模板根约定,目录即声明本体)。 */
    private Path mapsDir() {
        return tempDir.resolve("maps");
    }

    // ── ① 快照不变性(7.1 要点 1:转换点一次性取值,不持活引用) ──────────

    @Test
    void snapshotIsImmuneToLaterRosterMutation() {
        Session session = fakeService.create(new SessionSpec(new SessionId("s1"), "m1", List.of()));
        UUID alice = UUID.randomUUID();
        session.roster().join(alice);

        SessionInfo before = api.sessions().get(0).info().orElseThrow();
        assertEquals(List.of(alice), before.members(), "快照取到加入序名册");

        UUID bob = UUID.randomUUID();
        session.roster().join(bob);                     // 取快照后改 fake 名册

        assertEquals(List.of(alice), before.members(), "旧快照不变(快照纪律)");
        assertEquals(Set.of(alice, bob),
                Set.copyOf(api.sessions().get(0).info().orElseThrow().members()),
                "新快照反映最新名册(句柄现取)");
        assertThrows(UnsupportedOperationException.class, () -> before.members().add(bob),
                "快照集合不可变(List.copyOf)");
    }

    // ── ② 句柄失效(会话消亡):Rejected(MODE_NOT_FOUND),存活会话无扰动 ───
    //    ★v1.15/33 号语义迁移:「id 不存在」前移为解析点 empty(同步早失败),
    //    future 内 Rejected 只剩「句柄在手后会话消亡」一态——本测即此态。

    @Test
    void switchModeOnDissolvedSessionCompletesRejectedWithoutDisturbance() throws Exception {
        Session live = fakeService.create(new SessionSpec(new SessionId("live"), "m1", List.of()));
        UUID alice = UUID.randomUUID();
        live.roster().join(alice);
        fakeService.create(new SessionSpec(new SessionId("ghost"), "m1", List.of()));
        SessionHandle ghost = api.session("ghost").orElseThrow();
        fakeService.sessions.remove(new SessionId("ghost"));   // 模拟解散摘除(句柄失效)

        // 33 号语义迁移:「id 不存在」前移为解析点 empty(同步早失败)——旧 ②组
        // 「从未存在的 id」形态在此承接:消亡后与从未存在同走 empty,不再产出句柄。
        assertTrue(api.session("ghost").isEmpty(), "消亡会话不再解析出句柄(解析点早失败)");
        assertTrue(api.session("never-there").isEmpty(), "从未存在的 id 同口径 → empty");

        CompletableFuture<SwitchResult> future =
                ghost.switchMode(MatchModeSelection.of("m2"), Handoff.empty());
        SwitchResult result = future.get(5, TimeUnit.SECONDS);

        SwitchResult.Rejected rejected = assertInstanceOf(SwitchResult.Rejected.class, result,
                "会话消亡 → Rejected(7.1 委托表失败语义;★v1.15 句柄失效语义)");
        assertEquals(SwitchResult.Reason.MODE_NOT_FOUND, rejected.code(),
                "「要找的 id 不在注册表」语义(§3.2 码表)");
        assertTrue(rejected.detail().contains("ghost"), "detail 点名消亡会话 id");
        assertTrue(fakeService.switchInvocations.isEmpty(),
                "会话消亡短路,未委托引擎 switchMode");
        assertEquals(SessionState.RUNNING, live.state(), "旧会话状态无扰动");
        assertEquals(List.of(alice), live.roster().all().stream().map(SessionMember::uuid).toList(),
                "旧会话名册无扰动");
    }

    // ── ③ 线程契约:switchMode 委托发生在注入的主线程执行器线程(§14.1) ──

    @Test
    void switchModeDelegatesOnInjectedMainExecutorThread() throws Exception {
        AtomicReference<Thread> executorThread = new AtomicReference<>();
        CountDownLatch probed = new CountDownLatch(1);
        mainExecutor.execute(() -> {
            executorThread.set(Thread.currentThread());
            probed.countDown();
        });
        assertTrue(probed.await(5, TimeUnit.SECONDS), "探测任务未执行(执行器哑火)");

        fakeService.create(new SessionSpec(new SessionId("s1"), "m1", List.of()));
        CompletableFuture<SwitchResult> future = api.session("s1").orElseThrow()
                .switchMode(MatchModeSelection.of("m1"), Handoff.empty());
        SwitchResult result = future.get(5, TimeUnit.SECONDS);

        assertEquals(1, fakeService.switchInvocations.size());
        Thread invokedOn = fakeService.switchInvocations.get(0).thread();
        assertEquals(executorThread.get(), invokedOn,
                "switchMode 委托发生在注入的主线程执行器线程(线程契约,javadoc 冻结)");
        assertNotEquals(Thread.currentThread(), invokedOn,
                "委托不得发生在调用方线程(任意线程发起,Impl 内部回主线程)");
        assertInstanceOf(SwitchResult.Succeeded.class, result, "拒绝路径之外的透传结果");
        assertEquals("s1", fakeService.switchInvocations.get(0).id().value());
        assertEquals("m1", fakeService.switchInvocations.get(0).targetModeId());
    }

    // ── ④ 目录顺序 = 注册序(14.21:不排序、不过滤) ───────────────

    @Test
    void directoryOrderMatchesRegistrationOrder() throws Exception {
        modeRegistry.register(provider("alpha"));
        modeRegistry.register(provider("beta"));
        modeRegistry.register(provider("gamma"));
        registerLoudly(fullMap(mapsDir().resolve("map_a")));
        registerLoudly(minimalMap(mapsDir().resolve("map_b")));

        assertEquals(List.of("alpha", "beta", "gamma"),
                api.modes().stream().map(MatchModeInfo::id).toList(),
                "modes() 顺序 = 注册序,不排序不过滤(14.21)");
        assertEquals(List.of("map_a", "map_b"),
                api.maps().stream().map(MapInfo::id).toList(),
                "maps() 顺序 = 注册序");

        assertTrue(api.mode("beta").isPresent());
        assertEquals("beta", api.mode("beta").orElseThrow().id());
        assertTrue(api.mode("ghost").isEmpty(), "mode() 缺失 → empty(7.1 失败语义列)");
    }

    /** 目录快照字段逐项映射(7.1 步骤 3/4 转换表;MiniMessage 序列化 + 枚举存 name());
     *  ★v1.8 视图收缩后:id 路径派生、tags/reset 走约定键,displayName/world 分量已删;
     *  ★v1.13/31 号:defaultTransition 分量随缺省去向管线退场删除(断言同步移除)。 */
    @Test
    void directorySnapshotsMapFieldsPerContract() throws Exception {
        modeRegistry.register(provider("alpha"));
        registerLoudly(fullMap(mapsDir().resolve("map_a")));
        registerLoudly(minimalMap(mapsDir().resolve("map_b")));

        MatchModeInfo modeInfo = api.mode("alpha").orElseThrow();
        assertTrue(modeInfo.displayName().startsWith("<red>") && modeInfo.displayName().contains("竞技"),
                "displayName = MiniMessage 序列化串(消费者可再解析): " + modeInfo.displayName());
        assertEquals(List.of("<red>第一行", "<red>第二行"), modeInfo.description(),
                "description 逐行序列化(MiniMessage 对串尾闭合标签省略——再解析等价)");
        assertNull(modeInfo.icon(), "icon 未声明为 null(MatchMode 既有口径,消费方自理)");
        assertEquals(2, modeInfo.minParticipants());
        assertEquals(10, modeInfo.maxParticipants());
        assertFalse(modeInfo.recordsHistory());

        MapInfo full = api.maps().get(0);
        assertEquals("map_a", full.id(), "id = 声明目录名(路径派生)");
        assertEquals(Set.of("arena"), full.tags(), "tags = 声明 TAGS 词料键读取");
        assertEquals("TEMPLATE_RESTORE", full.resetStrategy(), "枚举存 name()(声明 resetStrategy)");
        MapInfo minimal = api.maps().get(1);
        assertEquals("map_b", minimal.id(), "id = 声明目录名(路径派生)");
        assertEquals("REUSE_DIRTY", minimal.resetStrategy(), "resetStrategy 缺省 = REUSE_DIRTY(二参构造口径)");
    }

    // ── ⑤ createSession(recipe):未知模式响亮抛;成功路径返回快照与 fake 会话一致 ──

    @Test
    void createSessionRejectsUnknownModeLoudlyAndSnapshotsCreatedSession() {
        modeRegistry.register(provider("m1"));

        NoSuchElementException thrown = assertThrows(NoSuchElementException.class,
                () -> api.createSession(SessionRecipe.of("ghost-mode", "map_a")),
                "未知模式响亮抛(引擎既有 require 口径,§12.1;抛时句柄不存在)");
        assertTrue(thrown.getMessage().contains("ghost-mode"), "异常点名未知模式 id");

        SessionHandle handle = api.createSession(SessionRecipe.of("m1", "map_a"));
        SessionInfo info = handle.info().orElseThrow();   // ★v1.15:句柄唯一信息口(组合式)
        Session created = fakeService.session(new SessionId(handle.sessionId())).orElseThrow();
        assertEquals(created.id().value(), info.sessionId(), "快照与 fake 会话一致: sessionId");
        assertEquals("", info.modeId(), "首切换在途,无实例 → 空串(空串口径)");
        assertEquals("", info.mapId(), "首切换在途,无实例 → 空串(空串口径)");
        assertEquals(List.of(), info.members(), "新建会话名册为空");
        assertEquals(SessionState.RUNNING.name(), info.state(), "state = 状态机名字符串");
        assertTrue(handle.sessionId().startsWith("api-"), "api 建会话 id 带框架前缀(可辨认来源)");

        SessionSpec spec = fakeService.createdSpecs.get(0);
        assertEquals("m1", spec.initialModeId(), "initialModeId 透传");
        assertEquals(List.of("map_a"), spec.mapPool(), "指定图 = 会话地图池单元素(钉图置池首)");

        api.createSession(SessionRecipe.of("m1", "   "));
        assertEquals(List.of(), fakeService.createdSpecs.get(1).mapPool(),
                "空白图 = 空池自动挑图");
    }

    // ── ⑥ 视图纯净:record 分量零引擎类型 + 泛型元素类型补断言 ────────────

    @Test
    void viewRecordComponentsCarryNoEngineTypes() {
        for (Class<?> view : List.of(SessionInfo.class, MatchModeInfo.class, MapInfo.class)) {
            for (RecordComponent component : view.getRecordComponents()) {
                assertFalse(component.getType().getName().startsWith("com.hydrokarbs"),
                        view.getSimpleName() + "#" + component.getName() + " 携带框架类型 "
                                + component.getType().getName()
                                + "(快照不泄漏引擎活类型,§7.2 值对象行/要点 1)");
            }
        }
    }

    /** 泛型实参反射不可区分,由本测试补断言(SurfaceTest 归一化规则的缺口;
     *  ★v1.15/33 号:会话三查询泛型实参 SessionInfo → SessionHandle、createSession
     *  返句柄、switchMode 泛型断言随方法迁 SessionHandle、新增 info() 实参加
     *  SessionInfo 接管)。 */
    @Test
    void genericReturnTypesBindSnapshotTypes() throws Exception {
        assertEquals(SessionHandle.class,
                actualTypeArgument(HydrokarbsApi.class.getMethod("sessions")));
        assertEquals(SessionHandle.class,
                actualTypeArgument(HydrokarbsApi.class.getMethod("sessionOf", UUID.class)));
        assertEquals(MatchModeInfo.class,
                actualTypeArgument(HydrokarbsApi.class.getMethod("modes")));
        assertEquals(MapInfo.class, actualTypeArgument(HydrokarbsApi.class.getMethod("maps")));
        assertEquals(MatchModeInfo.class,
                actualTypeArgument(HydrokarbsApi.class.getMethod("mode", String.class)));
        assertEquals(SessionHandle.class, HydrokarbsApi.class
                .getMethod("createSession", SessionRecipe.class).getReturnType());
        assertEquals(SessionInfo.class,
                actualTypeArgument(SessionHandle.class.getMethod("info")));
        Method switchMode = SessionHandle.class.getMethod("switchMode",
                MatchModeSelection.class, Handoff.class);
        assertEquals(SwitchResult.class, actualTypeArgument(switchMode));
    }

    private static Class<?> actualTypeArgument(Method method) {
        return (Class<?>) ((ParameterizedType) method.getGenericReturnType())
                .getActualTypeArguments()[0];
    }

    // ── 假件(离线;只依赖引擎公开面) ────────────────────────────────

    /** 会话服务假实现:真 Session 入表;记录 create 的 spec 与 switchMode 的调用线程。 */
    private static final class FakeSessionService implements SessionService {

        final Map<SessionId, Session> sessions = new LinkedHashMap<>();
        final List<SessionSpec> createdSpecs = new ArrayList<>();
        final List<SwitchInvocation> switchInvocations = new ArrayList<>();

        record SwitchInvocation(Thread thread, SessionId id, String targetModeId) {
        }

        @Override
        public Session create(SessionSpec spec) {
            createdSpecs.add(spec);
            Session session = new Session(spec,
                    SessionPolicy.defaults(SessionPolicy.Topology.ARENA));
            sessions.put(spec.id(), session);
            return session;
        }

        @Override
        public Optional<Session> sessionOf(UUID player) {
            for (Session session : sessions.values()) {
                if (session.roster().contains(player)) {
                    return Optional.of(session);
                }
            }
            return Optional.empty();
        }

        @Override
        public Optional<Session> session(SessionId id) {
            return Optional.ofNullable(sessions.get(id));
        }

        @Override
        public Collection<Session> all() {
            return List.copyOf(sessions.values());
        }

        @Override
        public CompletableFuture<SwitchResult> switchMode(SessionId id, MatchModeSelection target,
                                                          Handoff payload) {
            switchInvocations.add(new SwitchInvocation(Thread.currentThread(), id, target.modeId()));
            return CompletableFuture.completedFuture(new SwitchResult.Succeeded(
                    id, "", target.modeId(), EndResult.nowhere()));
        }

        @Override
        public JoinResult join(SessionId id, UUID player) {
            throw new UnsupportedOperationException("测试桩不消费");
        }

        @Override
        public void leave(UUID player, LeaveReason reason) {
            throw new UnsupportedOperationException("测试桩不消费");
        }

        @Override
        public void dissolve(SessionId id, DissolveReason reason) {
            throw new UnsupportedOperationException("测试桩不消费");
        }
    }

    /** 零组件假工厂:displayName 带样式(验 MiniMessage 序列化);meta 逐字段自建。 */
    private static MatchModeProvider provider(String id) {
        return new MatchModeProvider() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public MatchMode build(com.hydrokarbs.engine.session.MatchModeConfig cfg) {
                return new MatchMode.Builder(id)
                        .displayName(Component.text("竞技", NamedTextColor.RED))
                        .description(List.of(Component.text("第一行", NamedTextColor.RED),
                                Component.text("第二行", NamedTextColor.RED)))
                        .metadata(MatchModeMetadata.addTo(new AttributeBag.Builder(), false, 2, 10).build())
                        .build();
            }
        };
    }

    /** 全量声明的测试地图(TEMPLATE_RESTORE 路径;★v1.8 新形状,世界目录须先落盘)。 */
    private static MapDefinition fullMap(Path dir) {
        return new MapDefinition(dir, ResetStrategy.TEMPLATE_RESTORE,
                new AttributeBag.Builder().put(MapDefinition.TAGS, Set.of("arena")).build());
    }

    /** 最小声明地图(REUSE_DIRTY 缺省路径;标签经 TAGS 词料键)。 */
    private static MapDefinition minimalMap(Path dir) {
        return MapTestKit.declaration(dir, Set.of("arena"));
    }

    /** 落盘声明目录并注册(MapTestKit 直录口的惯用包装;id = 目录名)。 */
    private void registerLoudly(MapDefinition map) throws Exception {
        java.nio.file.Files.createDirectories(map.worldDirectoryPath());
        MapTestKit.registered(mapRegistry, map);
    }
}
