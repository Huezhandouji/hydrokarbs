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
 * 句柄 {@code join}/{@code leave}(★v1.15/33 号自 {@code joinSession}/
 * {@code leaveSession} 迁移)完成「入局/出局/Rejected 兜底」全链(与 /sm 同权的
 * 受控暴露,D2——委托 {@code SessionService#join/leave},含 {@code JoinAdmission}
 * 裁决)。★v1.7(25 号修正案):原三个 D4 能力标签测试(注册可见/幂等、按提供方
 * 摘除、空白拒绝)随能力标签机制全链退场删除。
 *
 * <p>真会话服务装配见 {@link OfflineApi}(假世界口/直通主线程;零 Bukkit 静态调用)。
 */
class HydrokarbsApiMembersTest {

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

    // ── ★42 句柄 join/leave 端到端(★v1.15/33 号:joinSession/leaveSession 迁移) ──

    @Test
    void joinRegistersMemberAndReturnsJoined() {
        harness.createSession("s1", MODE_ID);
        UUID alice = UUID.randomUUID();

        JoinResult result = harness.api.session("s1").orElseThrow().join(alice);

        assertInstanceOf(JoinResult.Joined.class, result, "空会话(无实例/名册空)入局成功");
        Session session = harness.service.session(new com.hydrokarbs.engine.session.SessionId("s1"))
                .orElseThrow();
        assertTrue(session.roster().contains(alice), "名册登记");
        assertEquals(List.of(alice),
                session.roster().all().stream().map(SessionMember::uuid).toList());
    }

    @Test
    void joinOnDissolvedSessionReturnsRejected() {
        harness.createSession("ghost", MODE_ID);
        SessionHandle ghost = harness.api.session("ghost").orElseThrow();
        harness.service.dissolve(new com.hydrokarbs.engine.session.SessionId("ghost"),
                com.hydrokarbs.engine.session.DissolveReason.ADMIN);   // 句柄在手后会话消亡

        // 33 号语义迁移:「id 不存在」前移为解析点 empty(同步早失败)——旧
        // joinMissingSessionReturnsRejected 的「从未存在的 id」输入形态在此承接:
        // 调用方先解析即得 empty,拿不到句柄、join 不会被调。
        assertTrue(harness.api.session("ghost").isEmpty(), "消亡会话不再解析出句柄(解析点早失败)");
        assertTrue(harness.api.session("never-existed").isEmpty(), "从未存在的 id → empty");

        JoinResult result = ghost.join(UUID.randomUUID());

        JoinResult.Rejected rejected = assertInstanceOf(JoinResult.Rejected.class, result,
                "会话消亡 → Rejected(引擎裁决原样透传,§3.2;★v1.15 句柄失效语义——"
                        + "「id 不存在」已前移为解析点 empty,33 号语义迁移)");
        assertEquals("session-not-found", rejected.reason(), "缺会话拒绝码(引擎既有口径)");
        assertTrue(rejected.detail().contains("ghost"), "detail 点名");
    }

    @Test
    void joinRejectedByAdmissionGateBubblesThroughApi() {
        harness.createSession("s1", GATED_MODE_ID);
        UUID alice = UUID.randomUUID();

        JoinResult result = harness.api.session("s1").orElseThrow().join(alice);

        JoinResult.Rejected rejected = assertInstanceOf(JoinResult.Rejected.class, result,
                "实例入场裁决拒绝 → Rejected(★42 Rejected 兜底,D2 受控暴露)");
        assertEquals("rejected-by-mode", rejected.reason());
        assertTrue(rejected.detail().contains("测试拒绝"), "裁决原因透传: " + rejected.detail());
        harness.createSession("s2", MODE_ID);      // 框架照常(拒绝是裁决,不是故障)
        assertFalse(harness.api.sessions().isEmpty());
    }

    @Test
    void leaveRemovesMemberAndIsIdempotent() {
        harness.createSession("s1", MODE_ID);
        UUID alice = UUID.randomUUID();
        harness.api.session("s1").orElseThrow().join(alice);

        SessionHandle handle = harness.api.sessionOf(alice).orElseThrow();
        handle.leave(alice, LeaveReason.KICK);
        Session session = harness.service.session(new com.hydrokarbs.engine.session.SessionId("s1"))
                .orElseThrow();
        assertFalse(session.roster().contains(alice), "被踢成员移出名册");

        assertDoesNotThrow(() -> handle.leave(alice, LeaveReason.KICK),
                "句柄在手、玩家已移出 → 域守卫 no-op(旧 leaveSession 重复调用幂等的"
                        + "句柄化,33 号裁决二;玩家现属 ≠ 本句柄不委托)");
        assertDoesNotThrow(() -> harness.api.sessionOf(alice)
                .ifPresent(h -> h.leave(alice, LeaveReason.KICK)),
                "不在任何会话 = 解析点 empty,调用方惯用法同样 no-op(幂等,D2)");
    }

    @Test
    void leaveOnForeignSessionHandleIsNoOp() {
        harness.createSession("s1", MODE_ID);
        harness.createSession("s2", MODE_ID);
        UUID alice = UUID.randomUUID();
        harness.api.session("s2").orElseThrow().join(alice);   // alice 现属 s2

        // 33 号裁决二唯一行为差分支(★72①):玩家现属 s2、持 s1 句柄调 leave →
        // 域守卫判「现属 ≠ 本句柄」不委托——旧 leaveSession「按玩家全局移出」语义
        // 会跨会话误踢,新语义 no-op(主线程内查后调,无竞态)
        harness.api.session("s1").orElseThrow().leave(alice, LeaveReason.KICK);

        Session s2 = harness.service.session(new com.hydrokarbs.engine.session.SessionId("s2"))
                .orElseThrow();
        assertTrue(s2.roster().contains(alice), "他会话句柄 leave 不误踢现属成员(域守卫 no-op)");
        assertEquals("s2", harness.api.sessionOf(alice).orElseThrow().sessionId(),
                "alice 现属会话仍为 s2(未被跨会话移出)");
        Session s1 = harness.service.session(new com.hydrokarbs.engine.session.SessionId("s1"))
                .orElseThrow();
        assertTrue(s1.roster().all().isEmpty(), "本句柄会话(s1)名册无扰动");
    }

    @Test
    void leaveWithQuitKeepsMemberInGraceWindow() {
        harness.createSession("s1", MODE_ID);
        UUID alice = UUID.randomUUID();
        harness.api.session("s1").orElseThrow().join(alice);

        harness.api.sessionOf(alice).ifPresent(handle -> handle.leave(alice, LeaveReason.QUIT));

        Session session = harness.service.session(new com.hydrokarbs.engine.session.SessionId("s1"))
                .orElseThrow();
        assertTrue(session.roster().contains(alice),
                "QUIT = 断线宽限标记,成员保留在册(ConnectionTracker 口径,§3.1)");
    }

    // ── ★v1.16/34 号:玩家域 leaveSession(UUID) 端到端(boolean 收口) ────────

    @Test
    void leaveSessionRemovesMemberAndReturnsTrue() {
        harness.createSession("s1", MODE_ID);
        UUID alice = UUID.randomUUID();
        harness.api.session("s1").orElseThrow().join(alice);

        boolean removed = harness.api.leaveSession(alice);

        assertTrue(removed, "在册成员移出成功 → true");
        Session session = harness.service.session(new com.hydrokarbs.engine.session.SessionId("s1"))
                .orElseThrow();
        assertFalse(session.roster().contains(alice), "KICK 立即移除(返回 true 时已不在名册)");
        assertTrue(harness.api.sessionOf(alice).isEmpty(), "玩家域解析随移出归空");
    }

    @Test
    void leaveSessionOnSessionlessPlayerReturnsFalse() {
        assertFalse(harness.api.leaveSession(UUID.randomUUID()),
                "不在任何会话 → false(幂等失败,同步早失败口径同 33 号)");
    }

    @Test
    void leaveSessionRemovesGraceWindowMember() {
        harness.createSession("s1", MODE_ID);
        UUID alice = UUID.randomUUID();
        harness.api.session("s1").orElseThrow().join(alice);
        harness.api.sessionOf(alice).ifPresent(h -> h.leave(alice, LeaveReason.QUIT));   // 宽限在册

        boolean removed = harness.api.leaveSession(alice);

        assertTrue(removed, "宽限期成员仍在册(sessionOf 名册口径)→ 可移出");
        Session session = harness.service.session(new com.hydrokarbs.engine.session.SessionId("s1"))
                .orElseThrow();
        assertFalse(session.roster().contains(alice), "KICK 立即移除(宽限在册同口径,boolean 不说谎)");
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
