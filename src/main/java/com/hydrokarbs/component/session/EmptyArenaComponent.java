package com.hydrokarbs.component.session;

import java.time.Duration;

import com.hydrokarbs.engine.component.SessionComponent;
import com.hydrokarbs.engine.event.SessionEvent;
import com.hydrokarbs.engine.port.SessionServices;
import com.hydrokarbs.engine.session.DissolveReason;

/**
 * 空场解散策略:会话连续无成员达 {@code after} 即请求解散
 * (06-共享组件 §6.4;03-会话与模式切换 §3.1)。会话策略可插拔:
 * 加/换行为不改 {@code SessionService};连接追踪与宽限 sweep 仍在内核。
 *
 * <p>到点 {@code requestDissolve(DissolveReason.EMPTY)} 幂等、恰一次;成员回归
 * 即重置计时;空场判定按在册成员为零(名册含宽限期成员);{@code stop} 与
 * {@code start} 严格对称(订阅句柄随框架回收)。不挂本组件 = 永不解散,
 * 隔离停用同样退回永不解散并留日志;由宿主组装时挂载。
 *
 * <p>{@code after} 以 {@link Duration} 声明,按标准节拍 20 tps(50ms/tick)折算
 * 为 tick 数(与最短驻留同一量纲);设计案未钉死折算口径,本版自定,待上游回填。
 */
public final class EmptyArenaComponent extends SessionComponent {

    private final long afterTicks;

    /** 自上次「成员在场」以来的连续空场 tick 数。 */
    private long emptyTicks;

    /** 解散请求是否已发出(恰一次护栏;请求口自身亦幂等,双保险)。 */
    private boolean dissolveRequested;

    private EmptyArenaComponent(String id, SessionServices svc, long afterTicks) {
        super(id, svc);
        if (afterTicks < 0) {
            throw new IllegalArgumentException("afterTicks 不得为负: " + afterTicks);
        }
        this.afterTicks = afterTicks;
    }

    // ── 钩子 ─────────────────────────────────────────────────────

    /** 生效:订阅成员进出事件——成员变化即时重置空场计时(与 update 的判定双保险)。 */
    @Override
    public void start() {
        svc().self().events().subscribe(SessionEvent.MemberJoined.class, event -> resetEmptyTicks());
        svc().self().events().subscribe(SessionEvent.MemberLeft.class, event -> resetEmptyTicks());
    }

    /**
     * 每 tick 推进空场计时:无成员 → 计数 +1,满 {@code afterTicks} 即恰一次请求解散;
     * 有成员 → 计数归零(下一空场从头起算)。
     */
    @Override
    public void update() {
        if (dissolveRequested) {
            return;                     // 已请求:恰一次,后续拍零成本
        }
        if (!svc().self().members().isEmpty()) {
            emptyTicks = 0L;            // 成员回归即重置(含宽限期在册成员)
            return;
        }
        emptyTicks++;
        if (emptyTicks >= afterTicks) {
            dissolveRequested = true;
            svc().self().requestDissolve(DissolveReason.EMPTY);
        }
    }

    /** 与 start 严格对称:订阅句柄由框架随本组件回收(登记在名下),无手写清理。 */
    @Override
    public void stop() {
    }

    // ── 读口(诊断/测试断言) ─────────────────────────────────────

    /** 连续空场 tick 数(测试断言口;成员在场恒为 0)。 */
    public long emptyTicks() {
        return emptyTicks;
    }

    /** 解散请求是否已发出(恰一次断言口)。 */
    public boolean dissolveRequested() {
        return dissolveRequested;
    }

    private void resetEmptyTicks() {
        emptyTicks = 0L;
    }

    // ── 装配描述符 ───────────────────────────────────────────────

    /**
     * 空场策略描述符:构造数据 {@code after}(空场持续时长;tick 折算口径见类注);
     * 由宿主组装 {@code SessionSpec.sessionComponents} 时构造。
     */
    public static final class Spec extends SessionComponent.Specification<EmptyArenaComponent> {

        /** 标准节拍(20 tps)的每 tick 毫秒数——Duration → tick 的折算基准(见类注)。 */
        private static final long MILLIS_PER_TICK = 50L;

        private final long afterTicks;

        public Spec(Duration after) {
            super("empty-arena");
            if (after == null || after.isNegative()) {
                throw new IllegalArgumentException("after 必须为非负时长: " + after);
            }
            this.afterTicks = after.toMillis() / MILLIS_PER_TICK;
        }

        /** 折算后的空场 tick 数(诊断与测试断言口)。 */
        public long afterTicks() {
            return afterTicks;
        }

        @Override
        public EmptyArenaComponent create(String id, SessionServices svc) {
            return new EmptyArenaComponent(id, svc, afterTicks);
        }
    }
}
