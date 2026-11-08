package com.hydrokarbs.engine.port;

import com.hydrokarbs.engine.AttributeBag;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;

import java.util.List;
import java.util.Set;

/**
 * 模式模板只读信息面:实例组件经 {@code svc().modeInfo()} 读本实例的模式声明
 * (id/名称/描述/图标/mapTags/meta(AttributeBag,缺席键 = 中性缺省))(02-核心概念 §2.5)。
 *
 * <p>成员与 {@code MatchMode} 的只读访问器同名对齐;只读,不暴露组件表。
 */
public interface MatchModeInfoPort {

    String id();

    Component displayName();

    List<Component> description();

    /** 入口牌子/GUI 图标;未声明时为 null。 */
    Material icon();

    Set<String> mapTags();

    AttributeBag meta();
}
