package com.hydrokarbs.bridge.world;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.World;

import com.hydrokarbs.engine.port.WorldHandle;
import com.hydrokarbs.engine.port.WorldPort;
import com.hydrokarbs.engine.session.SessionId;
import com.hydrokarbs.map.MapDefinition;

/**
 * 世界端口占位实现(装配垫片;接口不变,替换即生效)。
 *
 * <p>行为:获取 = 即时返回就绪句柄——不复制模板、不建副本目录,世界对象按
 * 声明路径派生的目录名查找,缺席时回落服务器主世界(副本语义/引用计数/还原
 * 策略全部留给真实现);释放 = 立即完成的空操作。链路与真实现同构,零改动换实现。
 */
public final class PlaceholderWorldPort implements WorldPort {

    @Override
    public CompletableFuture<WorldHandle> acquire(MapDefinition map, SessionId forSession) {
        World world = Bukkit.getWorld(map.id());    // 查找基准 = 声明路径派生的目录名
        if (world == null) {
            world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        }
        if (world == null) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "无可用的服务器世界(占位世界端口)"));
        }
        String instanceName = map.id() + "-" + forSession.shortCode();
        return CompletableFuture.completedFuture(new WorldHandle(world, instanceName, map,
                map.resetStrategy()));
    }

    @Override
    public CompletableFuture<Void> release(WorldHandle handle) {
        return CompletableFuture.completedFuture(null);    // 引用计数与副本处置归真实现
    }

    @Override
    public CompletableFuture<Void> discardSession(SessionId session) {
        Objects.requireNonNull(session, "session");
        // 无副本可销毁:立即完成空操作(语义与 release 同源)
        return CompletableFuture.completedFuture(null);
    }
}
