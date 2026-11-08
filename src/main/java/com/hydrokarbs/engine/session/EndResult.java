package com.hydrokarbs.engine.session;

import java.util.Optional;

/**
 * 引擎侧的结束货币(03-会话与模式切换 §3.4):只有「去向」、没有「剧情」——
 * 仅携带可选 {@link TransitionIntent};胜负、原因等结局词汇归内容层。结束的唯一
 * 请求口是 {@code svc().self().end(result)}(组件主动声明,不被轮询)。无显式去向
 * 回落 session.fallback-mode(默认 = 出厂空模式兜底);引擎自兜底结束也用 {@link #nowhere()}。
 */
public record EndResult(Optional<TransitionIntent> transition) {

    /** 无显式去向工厂:回落 session.fallback-mode。 */
    public static EndResult nowhere() {
        return new EndResult(Optional.empty());
    }
}
