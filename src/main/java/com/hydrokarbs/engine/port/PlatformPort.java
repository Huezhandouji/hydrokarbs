package com.hydrokarbs.engine.port;

import java.util.logging.Logger;

import org.bukkit.plugin.Plugin;

/**
 * 平台端口:宿主平台原语的最小落点(节拍器/调度依赖 Plugin 类型)。
 *
 * <p>成员只增不改,扩充须过设计审查。
 */
public interface PlatformPort {

    /** 框架日志口。 */
    Logger logger();

    /** 宿主插件句柄(调度/资源/数据目录的原语入口)。 */
    Plugin plugin();

    /**
     * 一次性主线程延迟任务;返回可取消句柄({@code close} 等价取消),
     * 由实例容器登记到发起组件名下、随实例 close 统一回收。
     */
    AutoCloseable scheduleOnce(long delayTicks, Runnable task);
}
