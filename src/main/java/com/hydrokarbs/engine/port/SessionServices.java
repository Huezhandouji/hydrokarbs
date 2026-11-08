package com.hydrokarbs.engine.port;

/**
 * 会话组件的 svc 服务集(02-核心概念 §2.5.1)。
 *
 * <p>成员封闭(<b>2</b>):显示/持久化/角色/世界不在这里转发;要用就拿组件本身
 * ({@code svc().components().get(...)}),或走 {@code self()} 上的端口组
 * (含 {@code requestSwitch}/{@code requestDissolve} 两行动口,D8);
 * 由会话装配在构造期注入。
 *
 * <p>给本 record 加成员永远是错误答案(§2.8-决断五)——冻结测试
 * {@code SessionServicesMembersTest} 反射冻结两成员。
 */
public record SessionServices(SessionPort self, SessionComponentLookupPort components) {
}
