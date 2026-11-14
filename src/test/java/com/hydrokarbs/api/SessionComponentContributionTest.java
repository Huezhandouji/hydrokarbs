package com.hydrokarbs.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hydrokarbs.engine.component.ComponentDependencyException;
import com.hydrokarbs.engine.component.SessionComponent;
import com.hydrokarbs.engine.component.SessionComponentContributionRegistry;
import com.hydrokarbs.engine.mode.MatchMode;
import com.hydrokarbs.engine.port.SessionServices;
import com.hydrokarbs.engine.session.DissolveReason;
import com.hydrokarbs.engine.session.Session;
import com.hydrokarbs.engine.session.SessionId;
import com.hydrokarbs.engine.session.SessionPolicy;
import com.hydrokarbs.engine.session.SessionSpec;
import com.hydrokarbs.engine.session.SessionState;
import com.hydrokarbs.testkit.FakeWorldPort;
import com.hydrokarbs.testkit.MapTestKit;
import com.hydrokarbs.testkit.OfflineApi;
import com.hydrokarbs.testkit.SessionProbeComponent;
import com.hydrokarbs.testkit.StubSessionService;
import com.hydrokarbs.testkit.TestPlugins;

/**
 * 会话组件贡献端到端(★51~★53;D14 两通道,离线):
 * <ul>
 *   <li>★51(通道一·全局):显式注册后新会话合并(合并序 = 注册序 → recipe.extra);
 *       坏贡献干跑校验失败响亮上抛给调用方;摘除后新会话不再合并;</li>
 *   <li>★52(通道二·单次):recipe.extra 进表、随 dissolve 全灭;查重/缺依赖
 *       createSession 响亮拒绝(引擎装配闸,不写第二套校验);</li>
 *   <li>★53(隔离):贡献组件运行期抛异常仅自身隔离、会话不解散(★30 扩展)。</li>
 * </ul>
 * <p>合并序断言用 {@link StubSessionService} 记录 spec;生命周期/隔离/响亮拒用真会话服务。
 * {@code Plugin} 以动态代理占位;零 Bukkit 静态调用。
 */
class SessionComponentContributionTest {

    private static final String MODE_ID = "probe_mode";
    private static final MatchMode MODE = OfflineApi.mode(MODE_ID, OfflineApi.TAG);


    private OfflineApi harness;

    @BeforeEach
    void setUp() {
        harness = new OfflineApi(FakeWorldPort.immediate(), MODE);
        // ★v1.13/31 号适配:首切失败不再「无实例驻留」而是泊回退——本 rig 回退为空串
        //(无效回退 → SEVERE + 解散),无地图时会话创建即散、后续断言全塌;注册一张
        // probe 模式可用的图让首切实例就位(镜像 HydrokarbsApiMembersTest 装配惯例)。
        MapTestKit.registered(harness.maps, MapTestKit.newMapsDir(), "probe_map", OfflineApi.TAG);
    }

    /** 假会话服务:记录 create 收到的 spec(合并序断言面;镜像 HydrokarbsApiImplTest 假件)。 */
    private static final class SpecRecordingService extends StubSessionService {

        final List<SessionSpec> specs = new ArrayList<>();

        SpecRecordingService() {
            super(List::of);
        }

        @Override
        public Session create(SessionSpec spec) {
            specs.add(spec);
            return new Session(spec, SessionPolicy.defaults(SessionPolicy.Topology.ARENA));
        }
    }

    // ── ★51 通道一·全局:收录 → 合并(序)→ 摘除 ────────────────────────

    @Test
    void providerContributionMergedIntoEveryNewSessionInOrder() {
        SessionComponentContributionRegistry contributions = harness.contributions;
        SessionProbeComponent.Spec providerSpec = new SessionProbeComponent.Spec(new ArrayList<>());
        harness.api.registerSessionComponents(TestPlugins.named("provider-plugin"),
                List.of(providerSpec));

        // 合并序(注册序 → recipe.extra):假服务记录 spec 断言输入面
        SpecRecordingService recorder = new SpecRecordingService();
        HydrokarbsApiImpl recording = new HydrokarbsApiImpl(recorder, harness.modes, harness.maps,
                harness.globalEvents, contributions, Runnable::run, () -> "");
        recording.createSession(SessionRecipe.of(MODE_ID, null));
        SessionProbeComponent.Spec extraSpec = new SessionProbeComponent.Spec(new ArrayList<>());
        recording.createSession(new SessionRecipe(MODE_ID, Optional.empty(), List.of(),
                List.of(extraSpec)));

        assertEquals(List.of(providerSpec), recorder.specs.get(0).sessionComponents(),
                "provider 贡献并入无 recipe 会话(D14 通道一,21-D5:api 建会话宿主表为空)");
        assertEquals(List.of(providerSpec, extraSpec), recorder.specs.get(1).sessionComponents(),
                "合并序 = 已收录 provider(注册序)→ recipe.extra(D14)");

        // 真服务路径:贡献真实装配(装配闸通过,id = 供给面类型名)
        harness.api.createSession(SessionRecipe.of(MODE_ID, null));
        Session created = harness.service.all().iterator().next();
        assertTrue(created.sessionComponents().byId("SessionProbeComponent").isPresent(),
                "provider 贡献进新会话并装配(D14)");
    }

    @Test
    void badContributionFailsLoudlyAndRegistrationRejects() {
        SessionComponent.Specification<?> bad = new SessionProbeComponent.Spec(new ArrayList<>())
                .requires(UnresolvedSessionComponent.class);   // 无人提供 → 干跑校验必失败

        ComponentDependencyException thrown = assertThrows(ComponentDependencyException.class,
                () -> harness.api.registerSessionComponents(TestPlugins.named("bad-plugin"),
                        List.<SessionComponent.Specification<?>>of(bad)),
                "坏贡献响亮上抛(★v1.6:显式注册可捕获,onEnable 失败 = 拒载信号)");
        assertTrue(thrown.getMessage().contains("缺必需依赖"), "点名缺失类型: " + thrown.getMessage());
        assertTrue(harness.contributions.isEmpty(), "贡献不进默认面(失败不留半收录态)");
        assertDoesNotThrow(() -> harness.api.createSession(SessionRecipe.of(MODE_ID, null)),
                "建会话照常(收录闸失败不扰动建会话)");
    }

    @Test
    void providerRemovalStopsMergingIntoNewSessions() {
        var provider = TestPlugins.named("provider-plugin");
        harness.api.registerSessionComponents(provider,
                List.of(new SessionProbeComponent.Spec(new ArrayList<>())));
        harness.api.createSession(SessionRecipe.of(MODE_ID, null));
        assertTrue(harness.service.all().iterator().next().sessionComponents()
                        .byId("SessionProbeComponent").isPresent(),
                "前置:注册期贡献进表");

        harness.api.unregisterSessionComponents(provider);   // PluginDisableEvent 兜底的同一摘除口

        harness.api.createSession(SessionRecipe.of(MODE_ID, null));
        Session next = harness.service.all().stream().reduce((first, second) -> second).orElseThrow();
        assertTrue(next.sessionComponents().byId("SessionProbeComponent").isEmpty(),
                "provider disable 后新会话不再合并(D14/14.38;已装配会话不受影响)");
    }

    // ── ★52 通道二·单次:进表 / 随 dissolve 全灭 / 响亮拒 ─────────────────

    @Test
    void recipeExtraJoinsSessionAndDiesWithDissolve() {
        List<String> log = new ArrayList<>();
        SessionHandle handle = harness.api.createSession(
                new SessionRecipe(MODE_ID, Optional.empty(), List.of(),
                        List.of(new SessionProbeComponent.Spec(log))));

        Session created = harness.service.session(new SessionId(handle.sessionId())).orElseThrow();
        assertTrue(created.sessionComponents().byId("SessionProbeComponent").isPresent(),
                "recipe.extra 进表(★52)");
        assertTrue(log.contains("SessionProbeComponent:awake")
                && log.contains("SessionProbeComponent:start"), "随装配生效: " + log);

        harness.service.dissolve(created.id(), DissolveReason.ADMIN);
        assertTrue(log.contains("SessionProbeComponent:stop"),
                "随 dissolve 全灭(D14:无运行期拆卸,逆序 stop 走既有回收): " + log);
    }

    @Test
    void duplicateComponentIdFailsLoudly() {
        SessionRecipe duplicate = new SessionRecipe(MODE_ID, Optional.empty(), List.of(),
                List.of(new SessionProbeComponent.Spec(new ArrayList<>()),
                        new SessionProbeComponent.Spec(new ArrayList<>())));
        // 两 Spec 同供给面类型 → 组件 id(类型简单名)撞名 → 装配闸 bind 响亮拒
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> harness.api.createSession(duplicate), "查重响亮拒(★52;§7.3 装配期失败口径)");
        assertTrue(thrown.getMessage().contains("重复绑定同 id"), "点名冲突: " + thrown.getMessage());
        assertTrue(harness.service.all().isEmpty(), "不留半装配态(会话创建失败不入表)");
    }

    @Test
    void missingRequiredDependencyFailsLoudly() {
        SessionRecipe missing = new SessionRecipe(MODE_ID, Optional.empty(), List.of(),
                List.of(new SessionProbeComponent.Spec(new ArrayList<>())
                        .requires(UnresolvedSessionComponent.class)));

        ComponentDependencyException thrown = assertThrows(ComponentDependencyException.class,
                () -> harness.api.createSession(missing), "缺必需依赖响亮拒(★52)");
        assertTrue(thrown.getMessage().contains("缺必需依赖"), "点名缺失类型: " + thrown.getMessage());
        assertTrue(harness.service.all().isEmpty(), "不留半装配态");
    }

    // ── ★53 贡献组件隔离:仅自身 quarantine、会话不解散 ──────────────────

    @Test
    void contributedComponentQuarantinesItselfOnly() {
        List<String> log = new ArrayList<>();
        SessionHandle handle = harness.api.createSession(
                new SessionRecipe(MODE_ID, Optional.empty(), List.of(),
                        List.of(new SessionProbeComponent.Spec(log).failOnUpdate("boom"))));

        Session created = harness.service.session(new SessionId(handle.sessionId())).orElseThrow();
        harness.ticker.tickAll();                     // ★v1.13/31 号:有图后首切在途,驱动两拍落位
        harness.ticker.tickAll();                     // (api.createSession 不驱拍,镜像 OfflineApi.createSession 惯例)
        created.tickSessionComponents();              // 运行期 update 抛 → D9 隔离

        SessionProbeComponent probe = created.sessionComponents().get(SessionProbeComponent.class);
        assertTrue(created.sessionComponents().isQuarantined(probe),
                "贡献组件被隔离(★30 扩展至会话组件,★53)");
        assertEquals(SessionState.RUNNING, created.state(), "会话不解散(★53)");
    }

    // ── 假件 ────────────────────────────────────────────────────────

    /** 无人提供的会话组件类型(制造缺依赖;镜像 ThirdPartyModeRegistrationTest 手法)。 */
    private abstract static class UnresolvedSessionComponent extends SessionComponent {

        UnresolvedSessionComponent(String id, SessionServices svc) {
            super(id, svc);
        }
    }
}
