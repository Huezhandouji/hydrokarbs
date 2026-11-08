package com.hydrokarbs.api;

import java.util.UUID;

import com.hydrokarbs.engine.session.SessionId;

/**
 * 会话事件快照族(★v1.1-D3;07 §7.1):不可变 record,八事件各一(Created/
 * MemberJoined/MemberLeft/MemberReconnected/ModeSwitchStarted/ModeSwitchCompleted/
 * Dissolved/ComponentQuarantined),与引擎 {@code SessionEvent} 一一对应。
 *
 * <p><b>快照纪律</b>:不含活会话/活组件引用;枚举载荷({@code LeaveReason}/
 * {@code DissolveReason})存 {@code name()} 字符串——冻结面不被引擎枚举演进绑死
 * (§0.5.3);{@code SessionId} 直接用引擎值类型(§7.2 白名单)。
 *
 * <p>{@code tick} 承载发布时刻毫秒时间戳({@code occurredAtMillis})——非引擎
 * tick 计数;{@code ModeSwitchCompleted#ok} 快照恒 true——引擎仅在切换成功后发布
 * (字段在册,语义演进留位)。
 *
 * <p>顺序承诺:单会话内事件有序;跨插件订阅者间不承诺送达顺序;实例事件不经本面暴露。
 */
public sealed interface SessionEventView {

    /** 事件时间戳(毫秒;引擎发布时刻 {@code occurredAtMillis}——字段名 {@code tick} 见接口注)。 */
    long tick();

    /** 会话创建完成(引擎 {@code SessionCreated};初始模式 id 不入快照——快照经 {@code session(id)} 现取)。 */
    record Created(SessionId session, long tick) implements SessionEventView {
    }

    /** 成员加入名册(引擎 {@code MemberJoined})。 */
    record MemberJoined(SessionId session, UUID player, long tick) implements SessionEventView {
    }

    /** 成员离开(引擎 {@code MemberLeft});reason = {@link com.hydrokarbs.engine.session.LeaveReason} 的 name():QUIT/KICK;宽限期满移除不发本事件。 */
    record MemberLeft(SessionId session, UUID player, String reason, long tick)
            implements SessionEventView {
    }

    /** 断线成员宽限期内重连(引擎 {@code MemberReconnected})。 */
    record MemberReconnected(SessionId session, UUID player, long tick) implements SessionEventView {
    }

    /** 模式切换开始(引擎 {@code ModeSwitchStarted};from/to 为模式 id,空串 = 无实例)。 */
    record ModeSwitchStarted(SessionId session, String from, String to, long tick)
            implements SessionEventView {
    }

    /** 模式切换完成(引擎 {@code ModeSwitchCompleted};{@code ok} 口径见接口注——快照恒 true)。 */
    record ModeSwitchCompleted(SessionId session, String from, String to, boolean ok, long tick)
            implements SessionEventView {
    }

    /** 会话解散(引擎 {@code SessionDissolved});reason = {@link com.hydrokarbs.engine.session.DissolveReason} 的 name()。 */
    record Dissolved(SessionId session, String reason, long tick) implements SessionEventView {
    }

    /** 会话组件故障隔离(引擎 {@code SessionComponentQuarantined};§3.8——组件停用、会话不解散;cause = 引擎事件的 reason)。 */
    record ComponentQuarantined(SessionId session, String componentId, String cause, long tick)
            implements SessionEventView {
    }
}
