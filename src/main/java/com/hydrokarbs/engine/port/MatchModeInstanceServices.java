package com.hydrokarbs.engine.port;

/**
 * 组件服务集(svc):模式组件的唯一平台入口(02-核心概念 §2.5)。
 *
 * <p>★ 成员封闭(恰 3 个:本实例面 / 组件查找 / 模式模板只读信息)。
 * 显示/持久化/角色/世界一律不在这里转发:要用就拿组件本身
 * ({@code svc().components().get(...)}),或走 {@code self()} 上的端口组。
 * <b>服务集不为任何具体领域增设成员</b>(★25;新增成员须过设计审查)。
 *
 * <p>由容器经工厂在构造期注入:组件在 awake() 之前即持有本记录,
 * 无「创建后再注入」的中间态。
 */
public record MatchModeInstanceServices(
        MatchModeInstancePort self,     // 本实例面:参赛者/tick/attrs/交接/事件 + end()/switchTo()
        ComponentLookupPort components, // 组件查找(按类型/按 id,含 getAll)
        MatchModeInfoPort modeInfo      // 模式模板只读信息(id/名称/描述/图标/tags/meta)
) {
}
