package com.hydrokarbs.engine.event;

import java.util.UUID;

import com.hydrokarbs.engine.session.DissolveReason;
import com.hydrokarbs.engine.session.LeaveReason;
import com.hydrokarbs.engine.session.SessionId;

/**
 * 会话事件根:会话生命周期的公开信号(§3.1)。八个事件:创建/成员进出与重连/
 * 切换起讫/解散/组件故障隔离;给外部订阅者与跨切面逻辑用(不是 Bukkit Event、
 * 不进服务端总线)。{@code occurredAtMillis} 供「时间戳单调」类断言与日志,
 * 语义字段以各 record 的业务载荷为准。
 *
 * <p>与实例事件族({@link MatchModeInstanceEvent})分属会话层/实例层事实——
 * 两族不互转、不共用类型,要跨层就同时订两条总线。
 * 枚举载荷({@link LeaveReason}/{@link DissolveReason})的取值集变更须过设计评审
 * (它们是事实上的对外词汇)。
 */
public sealed interface SessionEvent {

    /** 事件时间戳(毫秒;发布时刻)。 */
    long occurredAtMillis();

    /** 会话创建完成(首个实例装配之前;携带初始模式 id)。 */
    record SessionCreated(SessionId session, String initialModeId, long occurredAtMillis)
            implements SessionEvent {

        /** 便捷工厂(时间戳取系统时钟)。 */
        public static SessionCreated of(SessionId session, String initialModeId) {
            return new SessionCreated(session, initialModeId, System.currentTimeMillis());
        }
    }

    /** 成员加入名册。 */
    record MemberJoined(SessionId session, UUID member, long occurredAtMillis) implements SessionEvent {

        /** 便捷工厂(时间戳取系统时钟)。 */
        public static MemberJoined of(SessionId session, UUID member) {
            return new MemberJoined(session, member, System.currentTimeMillis());
        }
    }

    /**
     * 成员离开(退出/被踢):quit/kick 即发本事件并把成员标记 DISCONNECTED 保留在册;
     * 宽限期满由清点移除,不再单独发事件;宽限期内重连发 {@link MemberReconnected}。
     */
    record MemberLeft(SessionId session, UUID member, LeaveReason reason, long occurredAtMillis)
            implements SessionEvent {

        /** 便捷工厂(时间戳取系统时钟)。 */
        public static MemberLeft of(SessionId session, UUID member, LeaveReason reason) {
            return new MemberLeft(session, member, reason, System.currentTimeMillis());
        }
    }

    /** 断线成员在宽限期内重连。 */
    record MemberReconnected(SessionId session, UUID member, long occurredAtMillis) implements SessionEvent {

        /** 便捷工厂(时间戳取系统时钟)。 */
        public static MemberReconnected of(SessionId session, UUID member) {
            return new MemberReconnected(session, member, System.currentTimeMillis());
        }
    }

    /** 模式切换开始(携带 from/to 模式 id,值由发布方传入)。 */
    record ModeSwitchStarted(SessionId session, String fromMode, String toMode, long occurredAtMillis)
            implements SessionEvent {

        /** 便捷工厂(时间戳取系统时钟)。 */
        public static ModeSwitchStarted of(SessionId session, String fromMode, String toMode) {
            return new ModeSwitchStarted(session, fromMode, toMode, System.currentTimeMillis());
        }
    }

    /** 模式切换完成(携带 from/to 模式 id,值由发布方传入;§3.2-B5)。 */
    record ModeSwitchCompleted(SessionId session, String fromMode, String toMode, long occurredAtMillis)
            implements SessionEvent {

        /** 便捷工厂(时间戳取系统时钟)。 */
        public static ModeSwitchCompleted of(SessionId session, String fromMode, String toMode) {
            return new ModeSwitchCompleted(session, fromMode, toMode, System.currentTimeMillis());
        }
    }

    /** 会话解散(终态;携带解散原因)。 */
    record SessionDissolved(SessionId session, DissolveReason reason, long occurredAtMillis)
            implements SessionEvent {

        /** 便捷工厂(时间戳取系统时钟)。 */
        public static SessionDissolved of(SessionId session, DissolveReason reason) {
            return new SessionDissolved(session, reason, System.currentTimeMillis());
        }
    }

    /**
     * 会话组件故障隔离(D9/§3.8/§11.4):组件在 update/onEvent 抛异常后被停用
     * (此后跳过其派发),<b>会话不解散</b>、其余组件照常。
     * 发布点在 {@code SessionComponentRegistry} 的隔离路径。
     */
    record SessionComponentQuarantined(SessionId session, String componentId, String reason,
                                       long occurredAtMillis) implements SessionEvent {

        /** 便捷工厂(时间戳取系统时钟)。 */
        public static SessionComponentQuarantined of(SessionId session, String componentId, String reason) {
            return new SessionComponentQuarantined(session, componentId, reason, System.currentTimeMillis());
        }
    }
}
