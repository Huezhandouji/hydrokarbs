package com.hydrokarbs.engine.port;

import java.util.Objects;

import com.hydrokarbs.engine.session.SessionId;

/**
 * 入场裁决上下文(§3.2.1):触发点 + 所属会话。
 *
 * <p>本面为最小两字段(触发点 + 会话 id);新字段按消费方只增不改。
 */
public record JoinContext(Trigger trigger, SessionId session) {

    public JoinContext {
        Objects.requireNonNull(trigger, "trigger");
        Objects.requireNonNull(session, "session");
    }

    /** 裁决触发点(两个,§3.2.1 触发点表)。 */
    public enum Trigger {
        /** 初始摆放:切换阶段 B4 名册原地位移,成员成为参赛者之前逐人询问。 */
        INITIAL_PLACEMENT,
        /** 运行中加入:join 在名册登记后、并入当前实例前询问。 */
        MID_JOIN
    }
}
