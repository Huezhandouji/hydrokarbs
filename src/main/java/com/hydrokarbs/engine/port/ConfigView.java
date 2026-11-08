package com.hydrokarbs.engine.port;

/**
 * 惰性只读配置面(18 号修正案 §3)。
 *
 * <p>进程级服务的工厂经 {@code ServiceEnv#cfg()} 拿到本面读取配置。
 * 「惰性」= 每次读取穿透到底层配置源,不缓存首次结果——服主换掉底层数据后,
 * 后续 {@link #get} 自动读到新值(★39 以「换底层读新值」断言)。
 * 实现体 = L1 {@code ConfigRepository}(bridge/config)。
 */
public interface ConfigView {

    /**
     * 读配置项:path 不存在或不可解析时返回 fallback。
     * 类型由调用方按需断言(实现体保证与底层存储一致)。
     */
    <T> T get(String path, T fallback);

    /**
     * 全回落缺省视图:任何路径都返回 fallback。
     * L0 配置适配器(L1 {@code ConfigRepository})尚未接线时的占位;
     * 注:本接口的抽象方法是泛型方法,lambda 无法实现,故以此具名工厂提供缺省实现。
     */
    static ConfigView fallbackView() {
        return new ConfigView() {
            @Override
            public <T> T get(String path, T fallback) {
                return fallback;
            }
        };
    }
}
