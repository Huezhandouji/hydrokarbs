package com.hydrokarbs.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.hydrokarbs.engine.event.SessionEvent;
import com.hydrokarbs.engine.session.Session;
import com.hydrokarbs.service.GlobalBoard;
import com.hydrokarbs.testkit.FakeWorldPort;
import com.hydrokarbs.testkit.MapTestKit;
import com.hydrokarbs.testkit.OfflineApi;

/**
 * 会话事件订阅端到端(★43;D3;7.5 施工文档步骤 5,离线):快照不可变、
 * 句柄 close 后不再收到、订阅者异常被隔离且其余照常;全事件族映射
 * (枚举存 name/tick 承载毫秒时间戳,7.5 决策 13);镜像接线修复 ⑨ 悬空
 * (GlobalBoard 真正收到事件,7.5 决策 12)。
 *
 * <p>链路:会话总线发布 → 镜像点(SessionServiceImpl.create 挂接)→ ⑨ 汇聚总线 →
 * {@code HydrokarbsApiImpl#subscribeSessionEvents} 快照转换 → 消费者回调。
 * 真会话服务装配见 {@link OfflineApi}。
 */
class HydrokarbsApiEventsTest {

    private static final String MODE_ID = "probe_mode";

    @TempDir
    Path tempDir;

    private OfflineApi harness;

    @BeforeEach
    void setUp() {
        harness = new OfflineApi(FakeWorldPort.immediate(),
                OfflineApi.mode(MODE_ID, OfflineApi.TAG));
        // 注册式地图(标签 common):让首切实例就位(★文件式装载通道已删,造数走 MapTestKit;
        // ★v1.8 无模板根——声明目录落盘挂点即 @TempDir)
        MapTestKit.registered(harness.maps, tempDir, "file_map", OfflineApi.TAG);
    }

    // ── ★43 订阅三命题 ──────────────────────────────────────────────

    @Test
    void subscriptionDeliversSnapshotFamilyAcrossLifecycle() {
        List<SessionEventView> received = new ArrayList<>();
        EventSubscription handle = harness.api.subscribeSessionEvents(received::add);

        UUID alice = UUID.randomUUID();
        Session session = harness.createSession("s1", MODE_ID);
        harness.api.session("s1").orElseThrow().join(alice);   // ★v1.15:成员操作经句柄
        harness.api.sessionOf(alice).ifPresent(
                sessionHandle -> sessionHandle.leave(alice, com.hydrokarbs.engine.session.LeaveReason.KICK));
        session.events().publish(SessionEvent.SessionComponentQuarantined.of(
                session.id(), "probe-component", "update-boom"));   // 隔离事件直发布(覆盖第八族映射)

        List<String> typeNames = received.stream()
                .map(view -> view.getClass().getSimpleName()).toList();
        assertTrue(typeNames.containsAll(List.of("Created", "MemberJoined", "MemberLeft",
                        "ModeSwitchStarted", "ModeSwitchCompleted", "ComponentQuarantined")),
                "订阅面覆盖全事件族(建会话/成员/首切换经真管线,隔离事件直发布): " + typeNames);

        // 载荷映射(7.5 决策 13:枚举存 name;tick = occurredAtMillis 毫秒时间戳)
        SessionEventView.MemberLeft left = received.stream()
                .filter(SessionEventView.MemberLeft.class::isInstance)
                .map(SessionEventView.MemberLeft.class::cast).findFirst().orElseThrow();
        assertEquals("KICK", left.reason(), "枚举载荷存 name()");
        assertEquals(alice, left.player());
        SessionEventView.Created created = received.stream()
                .filter(SessionEventView.Created.class::isInstance)
                .map(SessionEventView.Created.class::cast).findFirst().orElseThrow();
        assertTrue(created.tick() > 0, "tick 承载 occurredAtMillis 毫秒时间戳(javadoc 冻结口径)");
        assertEquals(new com.hydrokarbs.engine.session.SessionId("s1"), created.session(),
                "SessionId 用 engine.session 白名单值类型(7.5 决策 13)");

        // 切换完成快照:ok 恒 true(引擎仅在 B5 落位后发布,SessionEventView 接口注)
        SessionEventView.ModeSwitchCompleted completed = received.stream()
                .filter(SessionEventView.ModeSwitchCompleted.class::isInstance)
                .map(SessionEventView.ModeSwitchCompleted.class::cast).findFirst().orElseThrow();
        assertTrue(completed.ok(), "ok 恒 true(引擎发布点语义)");
        assertEquals(MODE_ID, completed.to());
    }

    @Test
    void eventViewsAreImmutableRecordsWithoutLiveReferences() {
        List<SessionEventView> received = new ArrayList<>();
        harness.api.subscribeSessionEvents(received::add);
        UUID alice = UUID.randomUUID();
        harness.createSession("s1", MODE_ID);
        harness.api.session("s1").orElseThrow().join(alice);   // ★v1.15:成员操作经句柄

        assertFalse(received.isEmpty());
        // 允许分量类型 = 白名单值(SessionId/UUID/String/long/boolean)——零引擎活类型
        Set<Class<?>> allowed = Set.of(com.hydrokarbs.engine.session.SessionId.class,
                UUID.class, String.class, Long.TYPE, Boolean.TYPE);
        for (SessionEventView view : received) {
            assertTrue(view.getClass().isRecord(), "快照必为 record(不可变值): " + view.getClass());
            for (var component : view.getClass().getRecordComponents()) {
                assertTrue(allowed.contains(component.getType()),
                        view.getClass().getSimpleName() + "#" + component.getName()
                                + " 携带非白名单类型 " + component.getType() + "(快照不泄漏活引用,★29)");
            }
        }
    }

    @Test
    void closedHandleStopsDeliveryAndCloseIsIdempotent() {
        List<SessionEventView> received = new ArrayList<>();
        EventSubscription handle = harness.api.subscribeSessionEvents(received::add);
        harness.createSession("s1", MODE_ID);
        int before = received.size();
        assertTrue(before > 0, "前置:close 前正常收到");

        handle.close();
        handle.close();                                    // 幂等(14.38 口径)
        harness.createSession("s2", MODE_ID);

        assertEquals(before, received.size(), "句柄 close 后不再收到任何会话事件(★43)");
    }

    @Test
    void failingSubscriberIsIsolatedAndOthersKeepReceiving() {
        List<SessionEventView> healthy = new ArrayList<>();
        harness.api.subscribeSessionEvents(view -> {
            throw new IllegalStateException("bad-subscriber");
        });
        harness.api.subscribeSessionEvents(healthy::add);

        harness.createSession("s1", MODE_ID);
        harness.api.session("s1").orElseThrow().join(UUID.randomUUID());

        assertFalse(healthy.isEmpty(), "坏订阅者不拖累其他订阅者(镜像总线既有隔离,★43)");
    }

    // ── 7.5 决策 12:镜像接线修复 ⑨ 悬空(全局汇聚总线真正喂到服务) ──────

    @Test
    void globalAggregateBusFeedsServicesAgain() {
        GlobalBoard board = new GlobalBoard(harness.globalEvents);
        board.start();                                     // 服务订阅 ⑨(既有装配语义)

        harness.createSession("s1", MODE_ID);
        assertTrue(board.liveSessions().contains(
                        new com.hydrokarbs.engine.session.SessionId("s1")),
                "SessionCreated 经镜像到达 ⑨,GlobalBoard 登记(⑨ 悬空修复)");

        harness.service.dissolve(new com.hydrokarbs.engine.session.SessionId("s1"),
                com.hydrokarbs.engine.session.DissolveReason.ADMIN);
        assertTrue(board.liveSessions().isEmpty(), "SessionDissolved 同路到达,看板除名");
    }

}
