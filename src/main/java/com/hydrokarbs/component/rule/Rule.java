package com.hydrokarbs.component.rule;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

import com.hydrokarbs.engine.event.MatchModeInstanceEvent;

/**
 * 声明式规则:trigger/when/then 三段纯函数(06-共享组件 §6.1-#6)。
 * 求值序 = 声明序;规则是数据不是行为(14-陷阱清单 §14.12),可离线单测。
 *
 * <p>then 里跨组件读写一律走 {@code ctx.component(X)} 的强类型读口,
 * 不直接读写他人组件状态(§14.26);★ then 内禁止直接发起模式切换——
 * 结束只有 {@code end()} 一个口,切换由节拍器在结束后的下一拍代为发起(§3.5)。
 */
public record Rule(String id,
                   Predicate<MatchModeInstanceEvent> trigger,
                   Predicate<RuleContext> when,
                   Consumer<RuleContext> then) {

    public Rule {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("规则 id 不能为空");
        }
        Objects.requireNonNull(trigger, "trigger");
        Objects.requireNonNull(when, "when");
        Objects.requireNonNull(then, "then");
    }
}
