package com.hydrokarbs;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;

/**
 * Paper 引导器(空实现/预留)。
 * 仅必须实现 {@code void bootstrap(BootstrapContext)};
 * {@code createPlugin(PluginProviderContext)} 为 default,不覆写。
 */
public final class Bootstrapper implements PluginBootstrap {

    @Override
    public void bootstrap(BootstrapContext context) {
        // 预留:引导期逻辑(当前无)
    }
}
