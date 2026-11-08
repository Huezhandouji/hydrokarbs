package com.hydrokarbs.bridge.config;

/**
 * 配置装载异常(§15.2)。
 *
 * <p>消息必须带文件名与行号(底层解析器定位信息原样并入)。属单文件局部失败:
 * 装载编排层捕获后跳过该文件并记 SEVERE,不中断其余文件装载(铁律 3)。
 */
public class ConfigException extends RuntimeException {

    public ConfigException(String message) {
        super(message);
    }

    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
