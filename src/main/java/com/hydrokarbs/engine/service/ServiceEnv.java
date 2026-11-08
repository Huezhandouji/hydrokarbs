package com.hydrokarbs.engine.service;

import java.util.Optional;

import com.hydrokarbs.engine.port.ConfigView;

/**
 * 服务工厂的取物口(18 号修正案 D3/§3)。
 *
 * <p>由 {@link ServiceRegistry} 装配期实现并传入工厂;注册表按拓扑序创建服务,
 * 工厂运行时其声明的依赖必然已就绪。服务不持服务集记录——「需要什么」经
 * requires/requiresOptional 声明、「取什么」经本面取用,二者不符即响亮失败。
 */
public interface ServiceEnv {

    /**
     * 取必需依赖:拓扑序保证就绪;取不到 = 声明与取用不符(未声明或提供者尚未创建),
     * 响亮抛 {@link IllegalStateException} 并点名类型。
     */
    <T> T dep(Class<T> type);

    /** 取可选依赖:缺席返回 {@link Optional#empty()},不抛。 */
    <T> Optional<T> depOpt(Class<T> type);

    /** 平台原语(日志/调度/数据目录);离线测试场景传 {@code null}。 */
    org.bukkit.plugin.Plugin plugin();

    /** 惰性只读配置面:每次读取穿透到底层,服主换底层数据后自动读新值。 */
    ConfigView cfg();
}
