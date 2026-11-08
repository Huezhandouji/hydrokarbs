package com.hydrokarbs.bridge.world;

import java.nio.file.Path;
import java.util.List;

import org.bukkit.World;

/**
 * 主线程世界装卸的抽象缝(§3.7)。
 *
 * <p>线程契约(Bukkit 加载/卸载世界 = 主线程)与可测性收敛在这一个接口:
 * {@link WorldPortImpl} 经注入的主线程执行器调用本接口,装卸调用
 * 只允许出现在本接口的实现内(§14.1)。
 */
public interface WorldLoader {

    /**
     * 以副本目录为母本加载世界({@code WorldCreator.name(instanceName).createWorld()};
     * Bukkit 以目录名识别世界,instanceName = 副本目录名)。
     *
     * @return 加载好的世界;加载失败由实现抛出或返回 null(调用方按失败路径处置)
     */
    World createWorld(String instanceName, Path copiedDir);

    /** 卸载世界({@code Bukkit.unloadWorld(world, save)});返回是否卸载成功。 */
    boolean unloadWorld(World world, boolean save);

    /** 当前已加载世界的名字清单({@code Bukkit.getWorlds()};残留副本断言用)。 */
    List<String> loadedWorldNames();
}
