package com.hydrokarbs.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.hydrokarbs.engine.AttributeBag;
import com.hydrokarbs.engine.mode.MatchModeMetadata;
import com.hydrokarbs.engine.port.JoinDecision;
import com.hydrokarbs.engine.session.JoinResult;
import com.hydrokarbs.engine.session.LeaveReason;
import com.hydrokarbs.engine.session.Session;
import com.hydrokarbs.engine.session.SessionMember;
import com.hydrokarbs.testkit.AdmissionGateComponent;
import com.hydrokarbs.testkit.FakeWorldPort;
import com.hydrokarbs.testkit.MapTestKit;
import com.hydrokarbs.testkit.OfflineApi;

/**
 * 成员操作的 api 面端到端(★42;7.5 施工文档步骤 5;离线):
 * {@code joinSession}/{@code leaveSession} 完成「入局/出局/Rejected 兜底」全链
 * (与 /sm 同权的受控暴露,D2——委托 {@code SessionService#join/leave},含
 * {@code JoinAdmission} 裁决)。★v1.7(25 号修正案):原三个 D4 能力标签测试
 * (注册可见/幂等、按提供方摘除、空白拒绝)随能力标签机制全链退场删除。
 *
 * <p>真会话服务装配见 {@link OfflineApi}(假世界口/直通主线程;零 Bukkit 静态调用)。
 */
class MiniGameApiMembersTest {

    private static final String MODE_ID = "probe_mode";
    private static final String GATED_MODE_ID = "gated_mode";

    @TempDir
    Path tempDir;

    private OfflineApi harness;

    @BeforeEach
    void setUp() {
        // gated_mode:入场裁决恒拒(Rejected 兜底链路的实例侧门)
        harness = new OfflineApi(FakeWorldPort.immediate(),
                OfflineApi.mode(MODE_ID, OfflineApi.TAG),
                gatedMode(GATED_MODE_ID));
        // 注册式地图(标签 common):让首切实例就位——入场裁决在实例路径上才有门
        // (★文件式装载通道已删,造数走 MapTestKit 注册 fixture;★v1.8 无模板根)
        MapTestKit.registered(harness.maps, tempDir, "file_map", OfflineApi.TAG);
    }

    // ── ★42 joinSession/leaveSession 端到端 ──────────────────────────

    @Test
    void joinSessionRegistersMemberAndReturnsJoined() {
        harness.createSession("s1", MODE_ID);
        UUID alice = UUID.randomUUID();

        JoinResult result = harness.api.joinSession("s1", alice);

        assertInstanceOf(JoinResult.Joined.class, result, "空会话(无实例/名册空)入局成功");
        Session session = harness.service.session(new com.hydrokarbs.engine.session.SessionId("s1"))
                .orElseThrow();
        assertTrue(session.roster().contains(alice), "名册登记");
        assertEquals(List.of(alice),
                session.roster().all().stream().map(SessionMember::uuid).toList());
    }

    @Test
    void joinMissingSessionReturnsRejected() {
        JoinResult result = harness.api.joinSession("ghost", UUID.randomUUID());
        JoinResult.Rejected rejected = assertInstanceOf(JoinResult.Rejected.class, result,
                "会话不存在 → Rejected(引擎裁决原样透传,§3.2)");
        assertEquals("session-not-found", rejected.reason(), "缺会话拒绝码(引擎既有口径)");
        assertTrue(rejected.detail().contains("ghost"), "detail 点名");
    }

    @Test
    void joinRejectedByAdmissionGateBubblesThroughApi() {
        harness.createSession("s1", GATED_MODE_ID);
        UUID alice = UUID.randomUUID();

        JoinResult result = harness.api.joinSession("s1", alice);

        JoinResult.Rejected rejected = assertInstanceOf(JoinResult.Rejected.class, result,
                "实例入场裁决拒绝 → Rejected(★42 Rejected 兜底,D2 受控暴露)");
        assertEquals("rejected-by-mode", rejected.reason());
        assertTrue(rejected.detail().contains("测试拒绝"), "裁决原因透传: " + rejected.detail());
        harness.createSession("s2", MODE_ID);      // 框架照常(拒绝是裁决,不是故障)
        assertFalse(harness.api.sessions().isEmpty());
    }

    @Test
    void leaveSessionRemovesMemberAndIsIdempotent() {
        harness.createSession("s1", MODE_ID);
        UUID alice = UUID.randomUUID();
        harness.api.joinSession("s1", alice);

        harness.api.leaveSession(alice, LeaveReason.KICK);   // KICK = 立即移除(§3.2)
        Session session = harness.service.session(new com.hydrokarbs.engine.session.SessionId("s1"))
                .orElseThrow();
        assertFalse(session.roster().contains(alice), "被踢成员移出名册");

        assertDoesNotThrow(() -> harness.api.leaveSession(alice, LeaveReason.KICK),
                "不在任何会话 = no-op(幂等,D2)");
    }

    @Test
    void leaveWithQuitKeepsMemberInGraceWindow() {
        harness.createSession("s1", MODE_ID);
        UUID alice = UUID.randomUUID();
        harness.api.joinSession("s1", alice);

        harness.api.leaveSession(alice, LeaveReason.QUIT);

        Session session = harness.service.session(new com.hydrokarbs.engine.session.SessionId("s1"))
                .orElseThrow();
        assertTrue(session.roster().contains(alice),
                "QUIT = 断线宽限标记,成员保留在册(ConnectionTracker 口径,§3.1)");
    }

    /** 恒拒入场裁决的模式(Rejected 兜底链路;testkit AdmissionGateComponent)。 */
    private static com.hydrokarbs.engine.mode.MatchMode gatedMode(String id) {
        return new com.hydrokarbs.engine.mode.MatchMode.Builder(id)
                .mapTags(OfflineApi.TAG)
                .metadata(MatchModeMetadata.addTo(new AttributeBag.Builder(), false, 0, 100).build())
                .addComponent("gate", new AdmissionGateComponent.Spec(
                        player -> new JoinDecision(JoinDecision.Kind.REJECT, "测试拒绝")))
                .build();
    }
}
