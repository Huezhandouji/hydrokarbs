package com.hydrokarbs.engine.instance;

import java.util.Objects;
import java.util.UUID;

/**
 * 参赛者:会话成员在某次模式实例内的视图(uuid、生命周期标记,§2.2)。
 *
 * <p>玩法语义字段(分组/职能等)不在参赛者上——那些状态归组件所有,
 * 经组件强类型读口查询。{@link ParticipantState#JOINING} 是名册原地位移时的
 * 初始标记(全体成员就地成为参赛者,§3.2-B4)。
 */
public final class Participant {

    /** 参赛者生命周期标记(实例概念;会话名册上没有这些)。 */
    public enum ParticipantState {
        /** 名册位移后的初始标记(入场安置进行中)。 */
        JOINING,
        /** 完成入场安置。 */
        ACTIVE,
        /** 已离开本实例。 */
        LEFT
    }

    private final UUID uuid;
    private final ParticipantState state;

    /** 便捷构造:初始标记 JOINING。 */
    public Participant(UUID uuid) {
        this(uuid, ParticipantState.JOINING);
    }

    public Participant(UUID uuid, ParticipantState state) {
        this.uuid = Objects.requireNonNull(uuid, "uuid");
        this.state = Objects.requireNonNull(state, "state");
    }

    public UUID uuid() {
        return uuid;
    }

    public ParticipantState state() {
        return state;
    }
}
