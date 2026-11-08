package com.hydrokarbs;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;

/**
 * Paper 插件加载器(空实现/预留):唯一抽象方法 {@code void classloader(PluginClasspathBuilder)}。
 */
public final class HydrokarbsPluginLoader implements PluginLoader {

    @Override
    public void classloader(PluginClasspathBuilder builder) {
        // 预留:需要动态加载的依赖库在此声明(当前无)
    }
}
