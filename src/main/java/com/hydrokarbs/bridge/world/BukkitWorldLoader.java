package com.hydrokarbs.bridge.world;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;

/**
 * {@link WorldLoader} 的 Bukkit 生产实现:三方法直通 Bukkit API,零自有状态。
 *
 * <p>只经 {@link WorldPortImpl} 在主线程执行器内被调用——本类不自带线程判断。
 */
public final class BukkitWorldLoader implements WorldLoader {

    @Override
    public World createWorld(String instanceName, Path copiedDir) {
        Objects.requireNonNull(instanceName, "instanceName");
        return WorldCreator.name(instanceName).createWorld();
    }

    @Override
    public boolean unloadWorld(World world, boolean save) {
        Objects.requireNonNull(world, "world");
        return Bukkit.unloadWorld(world, save);
    }

    @Override
    public List<String> loadedWorldNames() {
        return Bukkit.getWorlds().stream().map(World::getName).toList();
    }
}
