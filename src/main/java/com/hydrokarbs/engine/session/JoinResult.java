package com.hydrokarbs.engine.session;

/**
 * 入场结果(03-会话与模式切换 §3.2.1;与 {@link SwitchResult} 同一种结果格式):
 * 拒绝原因走 reason/detail,不是可编程契约。REJECT 下会话不收(名册不登记),
 * ADMIT 登记名册并成为参赛者;观战安置归组件层。
 */
public sealed interface JoinResult {

    /** 已加入(携带会话 id)。 */
    record Joined(SessionId session) implements JoinResult {
    }

    /** 被拒:reason 是简短归类(人读),detail 是人读原因清单(如「对局进行中,回合结束后可进」)。 */
    record Rejected(String reason, String detail) implements JoinResult {
    }
}
