package com.hydrokarbs.engine.instance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;

import com.hydrokarbs.engine.AttributeBag;
import com.hydrokarbs.engine.component.MatchModeComponent;
import com.hydrokarbs.engine.event.MatchModeInstanceEvent;
import com.hydrokarbs.engine.mode.MatchMode;
import com.hydrokarbs.engine.mode.MatchModeMetadata;
import com.hydrokarbs.engine.port.EngineServices;
import com.hydrokarbs.engine.port.MatchModeInstanceServices;
import com.hydrokarbs.engine.port.PlatformPort;
import com.hydrokarbs.engine.session.EndResult;
import com.hydrokarbs.engine.session.MatchModeConfig;
import com.hydrokarbs.engine.session.Session;
import com.hydrokarbs.engine.session.SessionId;
import com.hydrokarbs.engine.session.SessionMember;
import com.hydrokarbs.engine.session.SessionPolicy;
import com.hydrokarbs.engine.session.SessionSpec;
import com.hydrokarbs.map.MapDefinition;

/**
 * 容器单元冒烟(施工计划 1.3「测试与验收」):
 * awake/start/update 派发序、名册位移、启动事件、结束请求只置位、
 * 故障隔离单次护栏、close 全链路。全生命周期真组件版在 1.5 扩展。
 * 离线可跑:零 Bukkit import(Bukkit 类型位置传 null/全限定)。
 */
class InstanceLifecycleSmokeTest {

    // ── 假件 ──────────────────────────────────────────────────────

    /** 记录钩子序的假组件(start/update 行为可注入)。 */
    static final class ProbeComponent extends MatchModeComponent {
        final MatchModeInstanceServices services;
        final List<String> log;
        final Consumer<ProbeComponent> onStart;
        final Consumer<ProbeComponent> onUpdate;
        final List<MatchModeInstanceEvent> received = new ArrayList<>();

        ProbeComponent(String id, MatchModeInstanceServices svc, List<String> log,
                       Consumer<ProbeComponent> onStart, Consumer<ProbeComponent> onUpdate) {
            super(id, svc);
            this.services = svc;
            this.log = log;
            this.onStart = onStart;
            this.onUpdate = onUpdate;
        }

        @Override
        public void awake() {
            log.add(id() + ":awake");
        }

        @Override
        public void start() {
            log.add(id() + ":start");
            if (onStart != null) {
                onStart.accept(this);
            }
        }

        @Override
        public void update() {
            log.add(id() + ":update");
            if (onUpdate != null) {
                onUpdate.accept(this);
            }
        }

        @Override
        public void stop() {
            log.add(id() + ":stop");
        }

        @Override
        public void onEvent(MatchModeInstanceEvent event) {
            received.add(event);
        }
    }

    /** 假描述符:create 时闭包传入钩子行为。 */
    static final class ProbeSpec extends MatchModeComponent.Specification<ProbeComponent> {
        private final List<String> log;
        private final Consumer<ProbeComponent> onStart;
        private final Consumer<ProbeComponent> onUpdate;

        ProbeSpec(List<String> log, Consumer<ProbeComponent> onStart, Consumer<ProbeComponent> onUpdate) {
            super("probe");
            this.log = log;
            this.onStart = onStart;
            this.onUpdate = onUpdate;
        }

        @Override
        public ProbeComponent create(String id, MatchModeInstanceServices svc) {
            return new ProbeComponent(id, svc, log, onStart, onUpdate);
        }
    }

    /** 平台假实现:记录并即时执行一次性任务;句柄 close 即计数。 */
    static final class FakePlatform implements PlatformPort {
        final List<Long> delays = new ArrayList<>();
        final List<Runnable> tasks = new ArrayList<>();
        int cancels;

        @Override
        public Logger logger() {
            return Logger.getLogger("probe");
        }

        @Override
        public org.bukkit.plugin.Plugin plugin() {   // Bukkit 位置:纯接口,传 null
            return null;
        }

        @Override
        public AutoCloseable scheduleOnce(long delayTicks, Runnable task) {
            delays.add(delayTicks);
            tasks.add(task);
            task.run();
            return () -> cancels++;
        }
    }

    /** 会话测试桩:记录结束请求(覆写非 final 方法)。 */
    static final class RecordingSession extends Session {
        final List<String> endNotices = new ArrayList<>();

        RecordingSession(String id, SessionMember... members) {
            super(new SessionSpec(new SessionId(id), "lifecycle-probe", java.util.List.of()),
                    SessionPolicy.defaults(SessionPolicy.Topology.ROTATION));
            for (SessionMember member : members) {
                roster().join(member.uuid(), member.joinedAtMillis());
            }
        }

        @Override
        public void instanceEnded(MatchModeInstance instance, EndResult result) {
            endNotices.add(instance.id().value() + "/" + (result.transition().isEmpty() ? "nowhere" : "intent"));
        }
    }

    // ── 构造工具 ──────────────────────────────────────────────────

    private static MatchMode mode(String id, MatchModeComponent.Specification<?>... specs) {
        MatchMode.Builder builder = new MatchMode.Builder(id)
                .metadata(MatchModeMetadata.addTo(new AttributeBag.Builder(), false, 0, 100).build());
        int index = 1;
        for (MatchModeComponent.Specification<?> spec : specs) {
            builder.addComponent("c" + index++, spec);
        }
        return builder.build();
    }

    /** 全回落缺省配置(2.2 接口扩员后的具名假实现)。 */
    private static final class FallbackConfig implements MatchModeConfig {

        @Override
        public int getInt(String path, int def) {
            return def;
        }

        @Override
        public String getString(String path, String def) {
            return def;
        }

        @Override
        public List<String> getStringList(String path, List<String> def) {
            return def;
        }

        @Override
        public List<String> keys(String section) {
            return List.of();
        }
    }

    private static MatchModeConfig cfg() {
        return new FallbackConfig();
    }

    private static MapDefinition map() {
        return new MapDefinition(java.nio.file.Path.of("map1"), AttributeBag.empty());
    }

    private static MatchModeInstance instance(MatchMode mode, RecordingSession session, FakePlatform platform) {
        return mode.createInstance(session, map(), cfg(), null,
                com.hydrokarbs.engine.session.Handoff.empty(), new EngineServices(platform));
    }

    // ── 用例 ──────────────────────────────────────────────────────

    @Test
    void awakeStartUpdateDispatchInDeclarationOrder() {
        List<String> log = new ArrayList<>();
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();
        RecordingSession session = new RecordingSession("s1",
                new SessionMember(u1, SessionMember.ConnectionStatus.ONLINE, 1L),
                new SessionMember(u2, SessionMember.ConnectionStatus.ONLINE, 2L));
        MatchModeInstance instance = instance(mode("m1",
                new ProbeSpec(log, null, null),
                new ProbeSpec(log, null, null)), session, new FakePlatform());

        instance.awakeAll();
        assertEquals(List.of("c1:awake", "c2:awake"), log, "awake 按声明序");

        instance.startAll();
        assertEquals(List.of("c1:awake", "c2:awake", "c1:start", "c2:start"), log, "start 按声明序");

        // 名册原地位移:全体成员成为参赛者(初始标记 JOINING)
        ProbeComponent first = instance.component(ProbeComponent.class);
        assertNotNull(first);
        List<Participant> participants = first.services.self().participants();
        assertEquals(2, participants.size());
        assertEquals(Set.of(u1, u2), new HashSet<>(participants.stream().map(Participant::uuid).toList()));
        assertTrue(participants.stream().allMatch(p -> p.state() == Participant.ParticipantState.JOINING));

        // 启动事件:startAll 尾发布,经装配期挂上的事件钩子收到
        MatchModeInstanceEvent started = first.received.stream()
                .filter(e -> e instanceof MatchModeInstanceEvent.MatchModeInstanceStarted)
                .findFirst().orElseThrow();
        assertEquals(1, started.tick(), "启动事件携带推进后的 tick");
        assertTrue(started.occurredAtMillis() > 0);

        instance.tick();
        instance.tick();
        instance.tick();
        assertEquals(List.of("c1:awake", "c2:awake", "c1:start", "c2:start",
                "c1:update", "c2:update", "c1:update", "c2:update", "c1:update", "c2:update"), log,
                "update 每拍按声明序派发");
        assertEquals(4, first.services.self().tick(), "时钟随拍推进(startAll 1 + tick 3)");
    }

    @Test
    void requestEndOnlyFlagsOnceAndNotifiesSession() {
        List<String> log = new ArrayList<>();
        RecordingSession session = new RecordingSession("s1");
        // 每拍都请求结束:第一次通知会话,后续被护栏拦住
        MatchModeInstance instance = instance(mode("m1",
                new ProbeSpec(log, null, c -> c.services.self().end(EndResult.nowhere())),
                new ProbeSpec(log, null, null)), session, new FakePlatform());

        instance.awakeAll();
        instance.startAll();
        instance.tick();
        instance.tick();
        instance.tick();

        assertEquals(1, session.endNotices.size(), "结束请求只通知会话一次");
        assertTrue(session.endNotices.get(0).endsWith("/nowhere"));
    }

    @Test
    void throwingUpdateQuarantinesOnceAndEndsNowhere() {
        List<String> log = new ArrayList<>();
        RecordingSession session = new RecordingSession("s1");
        MatchModeInstance instance = instance(mode("m1",
                new ProbeSpec(log, null, c -> {
                    throw new RuntimeException("boom");
                }),
                new ProbeSpec(log, null, null)), session, new FakePlatform());

        instance.awakeAll();
        instance.startAll();
        log.clear();

        instance.tick();   // 不抛:tick 捕获并隔离
        assertEquals(1, session.endNotices.size(), "隔离即请求结束(无显式去向)");
        assertEquals(List.of("c1:update"), log, "c1 抛出后本拍剩余派发中止");

        instance.tick();   // 再次抛:同实例只隔离一次
        assertEquals(1, session.endNotices.size(), "单次护栏:不重复通知");
        assertEquals(List.of("c1:update", "c1:update"), log, "closed 前组件照常被驱动");
    }

    @Test
    void closeStopsReverseAndFreezesTicking() {
        List<String> log = new ArrayList<>();
        RecordingSession session = new RecordingSession("s1");
        MatchModeInstance instance = instance(mode("m1",
                new ProbeSpec(log, null, null),
                new ProbeSpec(log, null, null),
                new ProbeSpec(log, null, null)), session, new FakePlatform());

        instance.awakeAll();
        instance.startAll();
        instance.close();

        List<String> stops = log.stream().filter(e -> e.endsWith(":stop")).toList();
        assertEquals(List.of("c3:stop", "c2:stop", "c1:stop"), stops, "stop 逆声明序");

        int sizeBefore = log.size();
        instance.tick();
        assertEquals(sizeBefore, log.size(), "closed 后不再派发");

        // 只读读口与容器直读口
        MatchModeInstanceView view = instance.view();
        assertEquals(session.id(), view.session().id(), "只读会话视图携带所属会话 id(2.6 收窄,记 deviations)");
        assertEquals(session.roster().all(), view.session().members(), "视图成员快照与名册一致");
        assertEquals("map1", view.map().id());
        assertEquals(1, view.tick(), "startAll 推进 1 拍");
        assertNotNull(view.component(ProbeComponent.class), "读口取第一个可赋值者");
        assertNotNull(instance.component(ProbeComponent.class), "容器直读口取第一个可赋值者");
        assertFalse(instance.handoff().has(com.hydrokarbs.engine.session.Handoff.Key.of(
                "x", "y", String.class)), "入口交接为空载荷");
    }
}
