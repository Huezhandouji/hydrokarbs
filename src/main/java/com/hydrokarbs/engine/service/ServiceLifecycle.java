package com.hydrokarbs.engine.service;

/**
 * 进程级服务的可选生命周期(18 号修正案 D4/§3):纯无状态服务不必实现,
 * 典型用途是 start 订阅会话事件总线、stop 注销订阅。
 *
 * <p><b>硬义务:{@link #stop()} 与 {@link #start()} 严格对称</b>——
 * start 建立的一切由 stop 全部撤销;关停的逆序、异常隔离、幂等由注册表统一保证。
 */
public interface ServiceLifecycle {

    /** 启动:装配成功后由注册表按拓扑序调用(每个服务至多一次)。 */
    default void start() {
    }

    /** 停止:关停/装配失败回滚时由注册表逆序调用;与 start 严格对称。 */
    default void stop() {
    }
}
