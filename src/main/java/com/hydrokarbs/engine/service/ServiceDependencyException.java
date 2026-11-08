package com.hydrokarbs.engine.service;

/**
 * 服务装配期失败专用异常(18 号修正案 §3):缺必需依赖(点名双方)或依赖成环(点名环上服务)。
 *
 * <p>由 {@link ServiceRegistry#seal()} 在依赖校验阶段抛出;工厂/启动失败不走本异常。
 */
public final class ServiceDependencyException extends RuntimeException {

    public ServiceDependencyException(String message) {
        super(message);
    }

    public ServiceDependencyException(String message, Throwable cause) {
        super(message, cause);
    }
}
