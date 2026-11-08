package com.hydrokarbs.engine.session;

/**
 * 会话解散原因(§3.1 的三个触发源与 §3.2 二级回退;SHUTDOWN 由 12-代码骨架 §12.2
 * 关停驱动)。封闭枚举,进事件载荷({@code SessionDissolved})与日志;新增值须过设计评审。
 */
public enum DissolveReason {

    /** 空场(由空场策略组件请求)。 */
    EMPTY,
    /** 管理命令。 */
    ADMIN,
    /** 服务端/插件关停(§12.2 onDisable 驱动)。 */
    SHUTDOWN,
    /** 切换三级回退的二级:fallback 模式也装配失败,解散兜底(§3.2)。 */
    FALLBACK_DISSOLVE
}
