package com.hydrokarbs.component.endgate;

import java.util.Objects;
import java.util.Optional;

import com.hydrokarbs.engine.session.TransitionIntent;

/**
 * 判定器的产物:结局(内容)+ 可选去向(框架货币)(03-会话与模式切换 §3.4)。
 *
 * <p>{@code outcome} 留在内容层供统计/展示消费;{@code intent} 为空时结束回落
 * {@code session.fallback-mode}——引擎不认识任何具体模式 id。
 */
public record GateVerdict(MatchOutcome outcome, Optional<TransitionIntent> intent) {

    public GateVerdict {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(intent, "intent");
    }
}
