package com.hydrokarbs.mode.empty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.hydrokarbs.engine.mode.MatchMode;
import com.hydrokarbs.engine.mode.MatchModeMetadata;
import com.hydrokarbs.engine.mode.MatchModeProvider;
import com.hydrokarbs.testkit.OfflineAssembly;

/**
 * 出厂空模式工厂(★v1.13/31 号):id=limbo、零组件、空 mapTags(通配)、
 * meta=(false, 0, MAX)——回退落点的全部形状逐项断言;SPI 接线以
 * {@code ServiceLoader} 在测试类路径恰发现本工厂实代测(离线零 Bukkit)。
 */
class EmptyMatchModeFactoryTest {

    /** 形状断言:build 忽略 cfg(带值配置喂入亦不改变形状),零组件 + 通配 + 全开人数窗。 */
    @Test
    void buildsZeroComponentWildcardModeWithOpenWindowMeta() {
        EmptyMatchModeFactory factory = new EmptyMatchModeFactory();
        assertEquals("limbo", factory.id(), "id = limbo(31 号;empty 与 Java 惯用词碰撞不可入禁词表)");

        MatchMode mode = factory.build(OfflineAssembly.cfg(Map.of("anything", 1)));   // cfg 被忽略
        assertEquals("limbo", mode.id());
        assertTrue(mode.components().isEmpty(), "零组件表(占位模式无玩法行为)");
        assertTrue(mode.mapTags().isEmpty(), "mapTags 空 = 通配任意地图(31 号决策 B)");
        assertFalse(MatchModeMetadata.recordsHistory(mode.meta()), "占位等待不落史");
        assertEquals(0, MatchModeMetadata.minParticipants(mode.meta()), "下限 0:空名册可泊(force 豁免无需动用)");
        assertEquals(Integer.MAX_VALUE, MatchModeMetadata.maxParticipants(mode.meta()),
                "上限全开:兜任意名册(上限永不豁免,A4/防护③)");
        mode.verifyDependencies();      // 空表依赖校验天然通过(不抛即断言)
    }

    /** SPI 接线代测:测试类路径(= src/main/resources 清单)恰发现本工厂。 */
    @Test
    void spiDiscoversExactlyThisFactory() {
        Set<String> ids = ServiceLoader.load(MatchModeProvider.class).stream()
                .map(provider -> provider.get().id())
                .collect(Collectors.toSet());
        assertEquals(Set.of("limbo"), ids,
                "SPI 清单恰一行 = EmptyMatchModeFactory(31 号;发现即注册闸前置形状正确)");
    }
}
