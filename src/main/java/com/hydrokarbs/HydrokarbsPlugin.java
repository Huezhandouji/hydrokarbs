package com.hydrokarbs;

import org.bukkit.plugin.java.JavaPlugin;

import com.hydrokarbs.util.StartupBanner;

/**
 * 主类:退化为几行委托(18 号修正案 D12)。
 * onEnable = {@code HydrokarbsAssembly.assemble(this)} 存 {@link HydrokarbsAssembly.Context},
 * 成功后输出启动横幅({@link StartupBanner});onDisable = {@code HydrokarbsAssembly.shutdown(ctx)}。
 * 装配失败自 assemble 上抛 → 服务端禁用本插件(不吞、不半启动)。
 */
public final class HydrokarbsPlugin extends JavaPlugin {

    private HydrokarbsAssembly.Context context;

    @Override
    public void onEnable() {
        context = HydrokarbsAssembly.assemble(this);
        StartupBanner.print(getLogger());   // 启动横幅(仅 assemble 成功后到达)
        getLogger().info("Hydrokarbs enabled");
    }

    @Override
    public void onDisable() {
        if (context != null) {
            HydrokarbsAssembly.shutdown(context);
            context = null;
        }
    }
}
