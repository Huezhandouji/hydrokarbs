package com.hydrokarbs.engine.session;

/**
 * 成员离开原因(15-PaperMC 适配 §15.1 会话入口监听;进 {@code MemberLeft} 事件载荷)。
 * 封闭枚举,新增值须过设计评审。
 */
public enum LeaveReason {

    /** 主动退出(断线视同退出:即发 MemberLeft 并标记 DISCONNECTED 保留在册)。 */
    QUIT,
    /** 被踢。 */
    KICK
}
