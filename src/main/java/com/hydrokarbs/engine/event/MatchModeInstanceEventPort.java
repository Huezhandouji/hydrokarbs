package com.hydrokarbs.engine.event;

import java.util.function.Consumer;

/**
 * 实例事件端口:组件对实例事件总线的读写面(§2.5 {@code events()} 行)。
 *
 * <p>订阅随组件 stop 自动回收;实现体 {@code MatchModeInstanceEventBus}
 * (主线程同步派发、逐订阅者异常隔离,§11.1)。
 */
public interface MatchModeInstanceEventPort {

    /** 发布事件(主线程同步派发)。 */
    void publish(MatchModeInstanceEvent event);

    /** 按事件类型订阅(默认优先级)。 */
    <E extends MatchModeInstanceEvent> void subscribe(Class<E> type, Consumer<E> subscriber);

    /** 按事件类型 + 优先级订阅(优先级降序,同级按订阅序)。 */
    <E extends MatchModeInstanceEvent> void subscribe(Class<E> type, int priority, Consumer<E> subscriber);
}
