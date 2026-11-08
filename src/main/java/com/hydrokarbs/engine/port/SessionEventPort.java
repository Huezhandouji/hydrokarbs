package com.hydrokarbs.engine.port;

import java.util.function.Consumer;

import com.hydrokarbs.engine.event.SessionEvent;

/**
 * 会话事件端口:会话组件对会话事件总线的读写面(02-核心概念 §2.5.1)。
 *
 * <p>订阅句柄随组件 {@code stop} 自动回收:登记到<b>发起组件</b>名下,
 * dissolve/隔离时框架统一注销;返回的 {@link AutoCloseable} 供显式提前注销(幂等,不抛)。
 */
public interface SessionEventPort {

    /** 发布会话事件(主线程同步派发,逐订阅者异常隔离)。 */
    void publish(SessionEvent event);

    /** 按事件类型订阅(默认优先级);返回句柄(close 注销,幂等)。 */
    <E extends SessionEvent> AutoCloseable subscribe(Class<E> type, Consumer<E> subscriber);

    /** 按事件类型 + 优先级订阅(优先级降序,同级按订阅序);返回句柄(close 注销,幂等)。 */
    <E extends SessionEvent> AutoCloseable subscribe(Class<E> type, int priority, Consumer<E> subscriber);
}
