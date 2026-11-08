package com.hydrokarbs.api.view;

import java.util.List;
import java.util.Set;

import org.bukkit.Material;

/**
 * 模式快照:api 侧不可变 record(07 §7.1/§7.2 值对象行;数据源 =
 * {@code MatchMode}/模式元数据词料(AttributeBag,经 MatchModeMetadata 约定键),§2.3/§2.5)。
 *
 * <p>{@code displayName}/{@code description} 为 MiniMessage <b>序列化串</b>
 * (消费方可再解析;玩家可控串不得直接喂 MiniMessage——§14.10 纪律随消费方);
 * {@code icon} 为 Bukkit {@link Material} 原样,未声明为 {@code null}(消费方自理)。
 *
 * <p>★v1.13 收缩:原 {@code defaultTransition} 分量已删(31 号修正案)——消费过
 * 该分量的旧编译物需按 v1.13 面重编译(不设双轨过渡期;API 版本协商面已于
 * ★v1.14/32 号退场,兼容性以版本链陈述为准)。
 *
 * <p>第三方模式经 {@code registerMode} 注册入目录,与其他模式同列、无特判(14.21);
 * id 词汇由提供方定义,引擎零具体模式词汇。快照纪律:集合构造期 {@code copyOf};
 * 不含组件描述符表(A9:build 后不可变)。
 */
public record MatchModeInfo(String id, String displayName, List<String> description,
                            Material icon, Set<String> mapTags, boolean recordsHistory,
                            int minParticipants, int maxParticipants) {

    public MatchModeInfo {
        description = List.copyOf(description);
        mapTags = Set.copyOf(mapTags);
    }
}
