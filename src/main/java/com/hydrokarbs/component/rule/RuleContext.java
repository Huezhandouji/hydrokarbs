package com.hydrokarbs.component.rule;

import com.hydrokarbs.engine.event.MatchModeInstanceEvent;

/**
 * 规则求值上下文:实例只读读口 + 当前事件。
 *
 * <p>then 里经 {@code ctx.component(X)} 取对方组件的强类型读口、
 * 经 {@code ctx.tick()} 读实例节拍、经 {@link #eventAs} 类型化取回触发事件。
 */
public interface RuleContext extends com.hydrokarbs.engine.instance.MatchModeInstanceView {

    /** 触发本次求值的事件。 */
    MatchModeInstanceEvent event();

    /** 按类型取回触发事件(类型不符抛 ClassCastException,响亮失败)。 */
    default <E extends MatchModeInstanceEvent> E eventAs(Class<E> type) {
        return type.cast(event());
    }
}
