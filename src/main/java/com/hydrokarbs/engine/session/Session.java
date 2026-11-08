package com.hydrokarbs.engine.session;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.hydrokarbs.engine.component.SessionComponent;
import com.hydrokarbs.engine.component.SessionComponentRegistry;
import com.hydrokarbs.engine.event.SessionEvent;
import com.hydrokarbs.engine.event.SessionEventBus;
import com.hydrokarbs.engine.instance.MatchModeInstance;
import com.hydrokarbs.engine.port.SessionEventPort;
import com.hydrokarbs.engine.port.SessionView;
import com.hydrokarbs.engine.port.WorldHandle;

/**
 * 会话:跨模式常驻的玩家容器与切换编排骨架(§3.1)。
 *
 * <p><b>字段内建</b>:id/spec/policy/名册/连接追踪/事件总线/四态状态机/
 * 0..1 当前实例(结束与切换中间态期间仍持有旧实例直到 close 完成);
 * 空场策略住宿级组件(§6.4),连接追踪与宽限清点仍内核。
 *
 * <p>会话组件注册表每会话一份(非进程级静态),装配由会话服务 create 驱动,
 * 派发经 {@link #tickSessionComponents()}(每 tick 必调、与实例有无无关,§3.8);
 * <b>会话容器不持有会话组件的状态</b>——注册表只持描述符与实例引用。
 *
 * <p><b>状态机写点收敛</b>:迁移唯一入口 {@link #transitionTo(SessionState)},
 * 会话服务是唯一调用者;对外只暴露查询与守卫。方法一律非 final,供测试桩覆写计数。
 */
public class Session {

    /** {@link #lastSwitchTick} 的「从未切换」哨兵值(首次切换不受最短驻留限制,§3.5)。 */
    static final long NO_SWITCH_YET = -1L;

    private static final Logger LOG = Logger.getLogger(Session.class.getName());

    private final SessionId id;
    private final SessionSpec spec;
    private final SessionPolicy policy;
    private final SessionRoster roster;
    private final ConnectionTracker tracker;
    private final SessionEventBus events;
    private final SessionComponentRegistry sessionComponents;   // 每会话一份
    private final SessionViewImpl view = new SessionViewImpl();
    private final SessionEventPortImpl eventsPort = new SessionEventPortImpl();

    private volatile SessionState state = SessionState.RUNNING;
    private volatile Optional<MatchModeInstance> current = Optional.empty();   // 写点:切换执行段唯一

    /** 实例结束请求记录(诊断用;处置归切换执行段的自动驱动)。 */
    private final List<EndNotice> endNotices = new ArrayList<>();

    /** 挂起执行计划(阶段 A 完成与阶段 B 执行之间的唯一交接物;null = 无挂起)。 */
    private PendingSwitch pending;

    /** 会话驱动钩子(由会话服务实现并注入;未注入时空转)。 */
    private Drive drive;

    /** 最近一次切换完成时的会话 tick(最短驻留的计量基准;-1 = 从未切换,首切不设限)。 */
    private long lastSwitchTick = NO_SWITCH_YET;

    /** 会话 tick 计数(最短驻留的计量基准,§3.5)。 */
    private long ticks;

    /**
     * @param spec   会话声明(id/初始模式/地图池/会话组件表)
     * @param policy 会话策略(拓扑/驻留/宽限;空场策略在会话组件表里)
     */
    public Session(SessionSpec spec, SessionPolicy policy) {
        this.spec = Objects.requireNonNull(spec, "spec");
        this.id = spec.id();
        this.policy = Objects.requireNonNull(policy, "policy");
        this.roster = new SessionRoster();
        this.tracker = new ConnectionTracker(policy.disconnectGraceMillis());
        this.events = new SessionEventBus();
        this.sessionComponents = new SessionComponentRegistry(id, events);
    }

    // ── 只读面 ────────────────────────────────────────────────────

    public SessionId id() {
        return id;
    }

    public SessionSpec spec() {
        return spec;
    }

    public SessionPolicy policy() {
        return policy;
    }

    public SessionRoster roster() {
        return roster;
    }

    public ConnectionTracker tracker() {
        return tracker;
    }

    public SessionEventBus events() {
        return events;
    }

    /** 会话组件注册表只读口(诊断与测试断言消费;装配写点在会话服务,本类不放写口)。 */
    public SessionComponentRegistry sessionComponents() {
        return sessionComponents;
    }

    /** 实例侧只读会话视图(SessionView 的实现体,经实例端口 session() 暴露)。 */
    public SessionView view() {
        return view;
    }

    /** 会话事件端口(SessionPort#events() 的实现体,订阅句柄随组件 stop 回收)。 */
    SessionEventPort eventsPort() {
        return eventsPort;
    }

    /** 当前实例(0..1;无实例或切换中间态期间为 empty)。 */
    public Optional<MatchModeInstance> currentInstance() {
        return current;
    }

    /** 会话 tick 计数(每 tick 经 {@link #idleTimers(long)} 递增;min-dwell 驻留基准)。 */
    public long ticks() {
        return ticks;
    }

    // ── 状态机:对外查询 ───────────────────────────────────────────

    /** 当前状态(查询口,返回值表达、不抛)。 */
    public SessionState state() {
        return state;
    }

    /** 是否处于任一给定态(布尔判定口,与断言式守卫分开;无参即恒 false)。 */
    public boolean in(SessionState first, SessionState... rest) {
        if (state == first) {
            return true;
        }
        for (SessionState candidate : rest) {
            if (state == candidate) {
                return true;
            }
        }
        return false;
    }

    // ── 状态机:写点收敛(会话服务是唯一调用者) ───

    /**
     * 断言式守卫:当前处于任一禁入态即抛 {@link IllegalStateException}(响亮失败;
     * 写点内部用,调用方不得吞)。对外语义判定请走 {@link #in}。
     */
    void requireNotIn(SessionState... forbidden) {
        for (SessionState candidate : forbidden) {
            if (state == candidate) {
                throw new IllegalStateException(
                        "会话 " + id.value() + " 处于 " + state + ",禁入态 " + candidate + " 命中,拒绝操作");
            }
        }
    }

    /**
     * 迁移写点(包级收敛,唯一入口):校验合法迁移矩阵
     * ({@link SessionState#canTransitionTo})后写 volatile 状态;非法迁移响亮失败。
     * 会话服务是唯一调用者。
     */
    void transitionTo(SessionState target) {
        Objects.requireNonNull(target, "target");
        if (state == target) {
            throw new IllegalStateException("会话 " + id.value() + " 禁止自迁移: " + target);
        }
        if (!state.canTransitionTo(target)) {
            throw new IllegalStateException(
                    "会话 " + id.value() + " 非法迁移: " + state + " → " + target);
        }
        state = target;
    }

    // ── 节拍器四钩子(§12.2 tickAll 驱动序①②③④) ───────

    /**
     * ① 待处理的模式切换/结束处置(切换的唯一驱动点,§3.5 防护②——事件派发栈内不发切换)。
     * 逻辑住在驱动钩子里,经 {@link #bindDrive} 注入;未注入时空转。
     */
    public void runPendingSwitch() {
        Drive hook = drive;
        if (hook != null) {
            hook.onDrive(this);
        }
    }

    /** ② 驱动当前实例一拍(无实例为 no-op;实例内部自带关闭护栏)。 */
    public void tickCurrentInstance() {
        current.ifPresent(MatchModeInstance::tick);
    }

    /**
     * ③ 会话组件派发(每 tick 必调,<b>与实例有无无关</b>——切换间隙照常,§3.8)。
     * 隔离语义在内:逐组件 try/catch → 停用单个组件、会话不解散,不外抛到节拍器。
     * 偏差:文档写「包级」,但驱动方 EngineTicker 跨包,与其余三钩子同口径提 public。
     */
    public void tickSessionComponents() {
        sessionComponents.tickAll();
    }

    /**
     * ④ 内核节律(无参版:节拍器入口;离线测试用 {@link #idleTimers(long)} 手动时钟)。
     * 内容:tick 计数推进 + 断线宽限清点(空场计时已下放组件)。
     */
    public void idleTimers() {
        idleTimers(System.currentTimeMillis());
    }

    /**
     * ④ 的可控时钟版。每 tick:tick 计数递增(最短驻留基准)+ 宽限清点
     * (到期成员移出名册;MemberLeft 在断线时已发,期满不再单独发事件)。
     */
    public void idleTimers(long nowMillis) {
        ticks++;
        sweepDisconnectGrace(nowMillis);
    }

    /** 宽限清点:到期者移出名册并记日志(名册增删执行权在会话——本钩子即清点执行点)。 */
    private void sweepDisconnectGrace(long nowMillis) {
        for (UUID expired : tracker.sweep(nowMillis)) {
            roster.remove(expired);
            LOG.log(Level.INFO, "Disconnect grace expired, member {0} removed (session {1})",
                    new Object[] { expired, id.value() });
        }
    }

    /** 会话故障隔离(节拍器的兜底路径):委托驱动钩子强切回退模式;未注入为 no-op。 */
    public void quarantine() {
        Drive hook = drive;
        if (hook != null) {
            hook.onQuarantine(this);
        }
    }

    // ── 实例结束请求的收口(§12.2 requestEnd 的通知落点) ──────────

    /**
     * 实例结束请求的收口:置 {@code ENDING}(结束判定不立即清场,清场由节拍器在
     * 下一 tick 驱动,§3.1)并记录请求。已处于 {@code ENDING}(同实例幂等护栏外的
     * 重复通知)仅追加记录;{@code DISSOLVING} 期间忽略并记日志(会话已在终结)。
     */
    public void instanceEnded(MatchModeInstance instance, EndResult result) {
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(result, "result");
        if (state == SessionState.DISSOLVING) {
            LOG.log(Level.WARNING, "Instance end notice ignored, session {0} dissolving", id.value());
            return;
        }
        if (state == SessionState.RUNNING) {
            transitionTo(SessionState.ENDING);
        }
        endNotices.add(new EndNotice(instance, result));
    }

    /** 已收到的结束请求记录(诊断用)。 */
    List<EndNotice> endNotices() {
        return List.copyOf(endNotices);
    }

    /** 取走全部未处置的结束请求(自动切换处置入口;取走即清空)。 */
    List<EndNotice> drainEndNotices() {
        List<EndNotice> drained = List.copyOf(endNotices);
        endNotices.clear();
        return drained;
    }

    /** 回填结束请求(自动切换被防护拒绝时:保留请求,下一拍重试直至放行)。 */
    void restoreEndNotices(List<EndNotice> notices) {
        endNotices.addAll(notices);
    }

    /** 结束请求记录:(实例, 结果)。 */
    record EndNotice(MatchModeInstance instance, EndResult result) {
    }

    // ── 驱动钩子与切换交接物(会话服务是唯一注入/写点) ────────

    /** 会话驱动钩子:切换执行/结束处置/故障隔离的落点(由会话服务实现注入)。 */
    public interface Drive {

        /** 节拍器每 tick 驱动:执行挂起的切换 B 段、处置 ENDING 的自动切换。 */
        void onDrive(Session session);

        /** 故障隔离:当前实例异常驱动时强切回退模式。 */
        void onQuarantine(Session session);
    }

    /** 绑定驱动钩子(会话创建时由 {@code SessionServiceImpl} 调用,装配期唯一写点)。 */
    public void bindDrive(Drive drive) {
        this.drive = Objects.requireNonNull(drive, "drive");
    }

    /** 当前挂起的切换计划(null = 无;占位/就绪两态见 {@link PendingSwitch})。 */
    PendingSwitch pendingSwitch() {
        return pending;
    }

    /** 挂起切换计划(占位守卫:同步段通过即挂上,阻止切换窗口内的并发请求)。 */
    void setPendingSwitch(PendingSwitch plan) {
        this.pending = Objects.requireNonNull(plan, "plan");
    }

    /** 清除挂起计划(B 段执行取走/作废时)。 */
    void clearPendingSwitch() {
        this.pending = null;
    }

    /** 替换当前实例(B4/B5 的落点;切换执行段唯一写点)。 */
    void setCurrentInstance(MatchModeInstance instance) {
        this.current = Optional.ofNullable(instance);
    }

    /** 最近一次切换完成时的会话 tick(-1 = 从未切换)。 */
    long lastSwitchTick() {
        return lastSwitchTick;
    }

    /** 记录切换完成时刻(B5 落点;最短驻留自此重新计量)。 */
    void recordSwitchTick(long atTick) {
        this.lastSwitchTick = atTick;
    }

    // ── 世界句柄持有(内核职责:实例用图由会话持句柄,§3.1;释放接缝待实装) ────

    private volatile WorldHandle worldHandle;

    /** 当前世界句柄(null = 未持有;切换完成/解散时改写)。 */
    WorldHandle worldHandle() {
        return worldHandle;
    }

    /** 替换世界句柄并返回旧值(切换执行段 B5 的落点;返回值供释放接缝消费)。 */
    WorldHandle replaceWorldHandle(WorldHandle newHandle) {
        WorldHandle previous = this.worldHandle;
        this.worldHandle = newHandle;
        return previous;
    }

    // ── 只读视图的实现体(跨级只读,架构纪律) ────

    /** 实例侧只读会话视图:委托名册/会话组件注册表(现取,不缓存)。 */
    private final class SessionViewImpl implements SessionView {

        @Override
        public SessionId id() {
            return id;
        }

        @Override
        public List<SessionMember> members() {
            return roster.all();
        }

        @Override
        public <T extends SessionComponent> T component(Class<T> type) {
            return sessionComponents.get(type);
        }

        @Override
        public <T extends SessionComponent> List<T> components(Class<T> type) {
            return sessionComponents.getAll(type);
        }
    }

    /**
     * 会话事件端口实现体:发布直通总线;订阅句柄登记到发起组件名下
     * (调用栈反查,随 stop/隔离统一回收),返回句柄供显式提前注销。
     */
    private final class SessionEventPortImpl implements SessionEventPort {

        @Override
        public void publish(SessionEvent event) {
            events.publish(event);
        }

        @Override
        public <E extends SessionEvent> AutoCloseable subscribe(Class<E> type, Consumer<E> subscriber) {
            return subscribe(type, SessionEventBus.DEFAULT_PRIORITY, subscriber);
        }

        @Override
        public <E extends SessionEvent> AutoCloseable subscribe(Class<E> type, int priority,
                                                                Consumer<E> subscriber) {
            SessionEventBus.Subscription handle = events.subscribe(type, priority, subscriber);
            sessionComponents.registerResource(sessionComponents.callerOnStack(), handle);
            return handle;
        }
    }
}
