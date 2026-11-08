package com.example.their;

import java.util.Set;

import net.kyori.adventure.text.Component;

import org.bukkit.Material;

import com.hydrokarbs.engine.AttributeBag;
import com.hydrokarbs.engine.mode.MatchMode;
import com.hydrokarbs.engine.mode.MatchModeMetadata;
import com.hydrokarbs.engine.mode.MatchModeProvider;
import com.hydrokarbs.engine.session.MatchModeConfig;

/**
 * 第三方模式工厂:只有 Builder 声明,零生命周期代码(§13.4 落地版)。模式即产品:
 * 组件声明全是构造数据——本示例不挂阶段/结束判定组件,不自行发起结束;无显式
 * 去向的结束回落 {@code session.fallback-mode}(★v1.13),以外部 switch 为主用法。
 * meta 词料键:recordsHistory=false + 人数窗 1~16(经 {@link MatchModeMetadata}
 * 约定键);mapTags 用中性 tag {@code demo}
 * (按 tags 交集解析,§12.2);配置随框架不随插件(缺文件按空配置构建,§15.2)。
 */
public final class TheirModeFactory implements MatchModeProvider {

    /** 本样例模式 id(onDisable 显式 unregisterMode 与 onEnable registerMode 同源)。 */
    public static final String MODE_ID = "their_mode";

    @Override
    public String id() {
        return MODE_ID;
    }

    @Override
    public MatchMode build(MatchModeConfig cfg) {
        return new MatchMode.Builder(id())
                .displayName(Component.text("Their Mode"))
                .icon(Material.BELL)
                .mapTags(Set.of("demo"))
                .metadata(MatchModeMetadata.addTo(new AttributeBag.Builder(), false, 1, 16).build())
                .addComponent("banner", new TheirBannerComponent.Spec())
                .build();
    }
}
