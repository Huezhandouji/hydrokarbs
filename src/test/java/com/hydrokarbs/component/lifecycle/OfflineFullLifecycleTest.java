package com.hydrokarbs.component.lifecycle;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import com.hydrokarbs.component.endgate.EndCondition;
import com.hydrokarbs.component.endgate.EndGateComponent;
import com.hydrokarbs.component.endgate.GateVerdict;
import com.hydrokarbs.component.endgate.MatchOutcome;
import com.hydrokarbs.component.phase.PhaseChanged;
import com.hydrokarbs.component.phase.PhaseComponent;
import com.hydrokarbs.component.phase.PhaseSpec;
import com.hydrokarbs.component.rule.Rule;
import com.hydrokarbs.component.rule.RuleBookComponent;
import com.hydrokarbs.engine.component.MatchModeComponent;
import com.hydrokarbs.engine.event.MatchModeInstanceEvent;
import com.hydrokarbs.engine.instance.LifecycleDriver;
import com.hydrokarbs.engine.instance.MatchModeInstance;
import com.hydrokarbs.engine.instance.MatchModeInstanceView;
import com.hydrokarbs.engine.mode.MatchMode;
import com.hydrokarbs.engine.session.Handoff;
import com.hydrokarbs.engine.session.MatchModeSelection;
import com.hydrokarbs.engine.session.SessionMember;
import com.hydrokarbs.engine.session.TransitionIntent;
import com.hydrokarbs.testkit.OfflineAssembly;
import com.hydrokarbs.testkit.TestComponent;

/**
 * 全生命周期离线测试——P0 出口闸门(§16.1-P0):假模式挂阶段/结束门/规则簿/测试件
 * 四件套,真容器驱动 awake → start → update×N → onHandoff → stop 全链,断言钩子序
 * 与派发序、启动事件发布、计时型到点转场、结束请求收口一次(幂等)、交接写自己
 * 命名空间、close 后回收不再派发、close 幂等。
 *
 * <p>住 component.lifecycle 包:{@code awakeAll}/{@code startAll} 包私有须同包直调,
 * 且本测试 import 组件——住 engine.instance 会判红(★1 含测试类)。零 Bukkit import。
 * 派发序经测试件交叉观察「阶段/结束门先于测试件更新」;容器启动事件推进一拍时钟,
 * 计时型条目第一拍到点转场属预期。
 */
class OfflineFullLifecycleTest {

    /** 结束门假判定器:末阶段「P2」走完即命中(优先级 100),带显式去向。 */
    private static final class P2ExpiredWins implements EndCondition {

        @Override
        public int priority() {
            return 100;
        }

        @Override
        public Optional<GateVerdict> evaluate(MatchModeInstanceView ctx) {
            PhaseComponent phases = ctx.component(PhaseComponent.class);
            if (phases == null || !"P2".equals(phases.currentId()) || !phases.currentExpired()) {
                return Optional.empty();
            }
            return Optional.of(new GateVerdict(
                    new MatchOutcome(MatchOutcome.Kind.WIN, "winner-1", "p2-finished"),
                    Optional.of(new TransitionIntent(
                            MatchModeSelection.of("test_next"), Handoff.empty()))));
        }
    }

    /** 测试件的 update 交叉观察:记录它看到的阶段与结局(派发序的证据)。 */
    private static Consumer<TestComponent> observer(List<String> log) {
        return c -> {
            PhaseComponent phases = c.sibling(PhaseComponent.class);
            EndGateComponent gate = c.sibling(EndGateComponent.class);
            log.add("observe[phase=" + phases.currentId() + ",outcome=" + gate.outcome().isPresent() + "]");
        };
    }

    private record Assembly(MatchModeInstance instance, OfflineAssembly.RecordingSession session,
                            OfflineAssembly.FakePlatform platform, List<String> log) {
    }

    private static Assembly assemble() {
        List<String> log = new ArrayList<>();
        LinkedHashMap<String, MatchModeComponent.Specification<?>> specs = new LinkedHashMap<>();
        specs.put("phases", new PhaseComponent.Spec(List.of(
                new PhaseSpec("P1", PhaseSpec.Kind.TIMED, null, 2),
                new PhaseSpec("P2", PhaseSpec.Kind.TIMED, null, 3))));
        specs.put("endgate", new EndGateComponent.Spec(List.of(new P2ExpiredWins())));
        specs.put("rules", new RuleBookComponent.Spec(List.of(
                new Rule("mark.transition",
                        ev -> ev.is(PhaseChanged.class),
                        ctx -> true,
                        ctx -> log.add("rules:mark:" + ctx.eventAs(PhaseChanged.class).toPhaseId())))));
        specs.put("test", TestComponent.spec(log,
                c -> c.recordLater(5, () -> { }),     // start 时登记一个延迟任务(资源回收的哨兵)
                observer(log)));

        MatchMode mode = OfflineAssembly.mode("test_mode", specs);
        OfflineAssembly.RecordingSession session =
                new OfflineAssembly.RecordingSession("s1",
                        new SessionMember(UUID.randomUUID(), SessionMember.ConnectionStatus.ONLINE, 1L));
        OfflineAssembly.FakePlatform platform = new OfflineAssembly.FakePlatform();
        return new Assembly(OfflineAssembly.instance(mode, session, platform), session, platform, log);
    }

    @Test
    void fullLifecycleDispatchesInOrderAndEndsOnce() {
        Assembly a = assemble();
        MatchModeInstance instance = a.instance();
        TestComponent test = instance.component(TestComponent.class);

        // ── awake → start:钩子序(测试件视角)与启动事件 ──────────────
        LifecycleDriver.begin(instance);
        // 阶段件 start 发首阶段转场事件 → 总线按声明序同步派发(规则簿先于测试件)
        // → 轮到测试件 start → 最后容器发布实例启动事件(同时推进一拍时钟)
        assertEquals(List.of(
                "test:awake",
                "rules:mark:P1",
                "test:onEvent:PhaseChanged",
                "test:start",
                "test:onEvent:MatchModeInstanceStarted"), a.log(),
                "start 段钩子序 = 声明序(阶段件的转场事件先于测试件的 start)");
        assertTrue(test.received.stream().anyMatch(e -> e instanceof MatchModeInstanceEvent.MatchModeInstanceStarted),
                "实例启动事件已发布且订阅者收到");
        assertTrue(test.received.stream().anyMatch(e -> e instanceof PhaseChanged),
                "首阶段进入的转场事件已发布且订阅者收到");
        assertEquals("P1", instance.component(PhaseComponent.class).currentId());
        assertEquals(1, a.platform().tasks.size(), "start 里登记的延迟任务已记账(哨兵)");

        // ── update ×N:派发序(交叉观察)+ 计时到点转场 + 结束门命中 ────
        a.log().clear();
        instance.tick();   // 拍2:P1 停留满 2 拍 → 转 P2(阶段件先于测试件更新:观察看到新 id)
        assertEquals(List.of(
                "rules:mark:P2",
                "test:onEvent:PhaseChanged",
                "test:update",
                "observe[phase=P2,outcome=false]"), a.log(),
                "计时型到点发转场事件;测试件的 update 观察到转场后的阶段 id");
        PhaseChanged transition = test.received.stream()
                .filter(e -> e instanceof PhaseChanged)
                .map(e -> (PhaseChanged) e)
                .skip(1).findFirst().orElseThrow();
        assertEquals("P1", transition.fromPhaseId());
        assertEquals("P2", transition.toPhaseId());
        assertEquals(2L, transition.elapsedTicks(), "在 P1 停留 2 拍(事件三字段正确)");

        a.log().clear();
        instance.tick();   // 拍3:P2 停留 1 拍
        instance.tick();   // 拍4:P2 停留 2 拍
        assertEquals(List.of(
                "test:update", "observe[phase=P2,outcome=false]",
                "test:update", "observe[phase=P2,outcome=false]"), a.log(),
                "未到点不转场、未命中无结束");

        a.log().clear();
        instance.tick();   // 拍5:P2 走完(末阶段停留);结束门命中(先于测试件:观察看到结局已就绪)
        assertEquals(List.of(
                "test:update",
                "observe[phase=P2,outcome=true]"), a.log(),
                "结束门命中拍:测试件的 update 观察到结局已就绪(声明序派发)");
        assertEquals(1, a.session().endNotices.size(), "命中即请求结束一次");
        assertTrue(a.session().endNotices.get(0).endsWith("intent:test_next"),
                "结束货币携带显式去向");

        instance.tick();   // 拍6:结束门再次命中调 end —— 容器护栏挡下(不重复处置)
        instance.tick();
        assertEquals(1, a.session().endNotices.size(), "重复命中不重复通知(requestEnd 幂等)");

        // close 前实例仍在:组件可读、结局可读
        EndGateComponent gate = instance.component(EndGateComponent.class);
        assertEquals(MatchOutcome.Kind.WIN, gate.outcome().orElseThrow().kind());
        assertEquals("winner-1", gate.outcome().orElseThrow().winnerId());
        assertEquals("P2", instance.component(PhaseComponent.class).currentId());

        // ── onHandoff:写自己命名空间的交接键,merge 后可读 ─────────────
        Handoff.Builder out = new Handoff.Builder();
        instance.component(TestComponent.class).onHandoff(out);
        instance.component(EndGateComponent.class).onHandoff(out);
        Handoff merged = Handoff.merge(out.build(), Handoff.empty());
        assertEquals(Optional.of("token-of-test"),
                merged.get(Handoff.Key.of("test", TestComponent.TOKEN_NAME, String.class)),
                "测试件写入自己命名空间(组件实例 id)的键 merge 后可读");
        assertEquals(MatchOutcome.Kind.WIN,
                merged.get(EndGateComponent.HANDOFF_OUTCOME).map(MatchOutcome::kind).orElseThrow(),
                "结束门把结局写进自己命名空间(namespace endgate)的交接键");

        // ── close:stop、资源回收、不再派发、幂等 ─────────────────────
        int logSizeBeforeClose = a.log().size();
        assertDoesNotThrow(instance::close);
        assertDoesNotThrow(instance::close, "close 幂等:连调只清一次");
        assertEquals(logSizeBeforeClose + 1, a.log().size(), "close 后测试件的 stop 恰被记录一次");
        assertTrue(a.log().get(a.log().size() - 1).endsWith(":stop"));
        assertEquals(1, a.platform().cancels, "登记资源统一回收:延迟任务句柄被 close 恰一次");

        int afterClose = a.log().size();
        instance.tick();
        assertEquals(afterClose, a.log().size(), "close 后不再派发(总线清空、时钟停走)");

        assertFalse(test.received.isEmpty(), "订阅历史在 close 前一直可积累(bus 清空前的事件已收到)");
    }
}
