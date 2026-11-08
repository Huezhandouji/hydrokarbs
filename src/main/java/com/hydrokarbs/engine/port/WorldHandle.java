package com.hydrokarbs.engine.port;

import java.util.Objects;

import org.bukkit.World;

import com.hydrokarbs.map.MapDefinition;

/**
 * 世界句柄:一次「地图 × 会话」的世界副本引用(03-会话与模式切换 §3.7)。
 *
 * <p>由世界端口发放;组件只消费 {@code map()} 携带的地图声明只读数据,
 * 不关心世界来源与打完如何还原。
 */
public record WorldHandle(World world, String instanceName, MapDefinition map, ResetStrategy reset) {

    public WorldHandle {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(instanceName, "instanceName");
        Objects.requireNonNull(map, "map");
        Objects.requireNonNull(reset, "reset");
    }
}
