package com.hydrokarbs.engine.port;

import java.util.UUID;

/**
 * 实例入场裁决端口(★v3.2,§3.2.1)。
 *
 * <p>L3 组件可实现本接口;会话服务的 join 经当前实例<b>按组件声明序查询第一个实现者</b>。
 * 引擎只认识裁决的结构词汇({@link JoinDecision.Kind} 的 ADMIT/REJECT),不认识
 * 任何具体入场策略——入场路由是行为(可能依赖阶段、人数、队伍状态),归组件。
 *
 * <p><b>不挂任何实现 = 零配置即全接纳</b>(默认 ADMIT)。L3 标准实现是入场控制
 * 组件(§6.1-#24)。
 */
public interface JoinAdmission {

    /**
     * 裁决一名玩家的入场安置。
     *
     * @param player 待入场玩家
     * @param ctx    入场上下文(触发点 + 所属会话)
     * @return 裁决(ADMIT 参赛 / REJECT 拒收)
     */
    JoinDecision admit(UUID player, JoinContext ctx);
}
