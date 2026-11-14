package com.hydrokarbs.listener;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;

import com.hydrokarbs.api.HydrokarbsApiImpl;

/**
 * 插件卸载兜底监听({@code PluginDisableEvent};14.38):任何插件 disable 时按
 * 提供方摘除其经 api 面注册的全部注册物——消费方无需自建善后,但句柄(订阅/
 * 注册物)仍应在 onDisable 显式反注册(防御性)。
 *
 * <p>三个职责块(同一监听器落位):
 * <ol>
 *   <li>显式注册模式:逐 id 走与 unregisterMode 同一回退编排(§3.2);</li>
 *   <li>注册式地图:逐张走与 unregisterMap 同一守卫——正用拒绝 = 保留并 WARN
 *       (摘除被拒是特性,20-D4),不因下线强拆存活对局;</li>
 *   <li>会话组件贡献:后续会话不再合并(已装配会话随 dissolve 自然消亡,D14)。</li>
 * </ol>
 */
public final class ProviderUnloadListener implements Listener {

    private final HydrokarbsApiImpl api;

    public ProviderUnloadListener(HydrokarbsApiImpl api) {
        this.api = Objects.requireNonNull(api, "api");
    }

    /** 插件禁用:按提供方摘除其注册物(三职责块幂等,重复派发无害)。 */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPluginDisable(PluginDisableEvent event) {
        Plugin plugin = event.getPlugin();
        api.unregisterModesOf(plugin);              // ① ★v1.6 显式注册模式(回退编排同 unregisterMode)
        api.unregisterMapsOf(plugin);               // ② ★v1.2 注册式地图(20-D4)
        api.unregisterSessionComponentsOf(plugin);  // ③ D14 贡献(后续会话不再合并)
    }
}
