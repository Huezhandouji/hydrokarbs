package com.hydrokarbs.api;

/**
 * 会话事件订阅句柄(★v1.1-D3;07 §7.1):{@link #close()} 注销本订阅,
 * 注销后不再收到任何会话事件。
 *
 * <p><b>句柄纪律(14.38)</b>:句柄只在插件实例字段中持有,{@code onDisable} 显式
 * close——reload 后旧句柄残留 = 重复事件与句柄泄漏;框架 disable 兜底只摘注册物,
 * <b>订阅句柄的回收是消费方自己的义务</b>。
 *
 * <p>幂等:重复 {@code close()} 无害、不抛。
 */
@FunctionalInterface
public interface EventSubscription extends AutoCloseable {

    /** 注销本订阅(幂等;重复 close 无害,不抛;14.38)。 */
    @Override
    void close();
}
