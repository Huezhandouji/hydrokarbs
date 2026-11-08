package com.hydrokarbs.testkit;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.bukkit.World;

import com.hydrokarbs.engine.port.WorldHandle;
import com.hydrokarbs.engine.port.WorldPort;
import com.hydrokarbs.engine.session.SessionId;
import com.hydrokarbs.map.MapDefinition;

/**
 * 世界端口假件:获取行为可注入(即时成功/注入失败——超时与失败共用同一完成回调
 * 分支,注入 {@code TimeoutException} 即覆盖超时路径);释放为空操作;会话销毁计数
 * 并按序记录被销毁的会话 id。世界对象用动态代理占位(不碰 Bukkit 静态)。
 *
 * <p>★v1.9 增补(离线清场断言前提):①{@link #sticky(List)} 工厂——同 (mapId, 会话)
 * 键幂等返回同句柄(镜像真端口表命中;{@link #immediate()} 原样不动);②接缝时序录
 * {@code seamLog}——release/discardSession 按调用序追加,主线程直跑时列表顺序即真实调用序。
 */
public final class FakeWorldPort implements WorldPort {

    /** 动态代理世界(句柄与实例只持引用;方法默认应答零开销)。 */
    public static World proxyWorld() {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[] {World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> "fake-world";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "fake-world-proxy";
                    default -> null;
                });
    }

    private volatile Function<MapDefinition, CompletableFuture<WorldHandle>> behavior;
    /** ★v1.9(27 号)sticky 同句柄表(键 = mapId + "|" + 会话 value;仅 sticky 实例使用)。 */
    private final Map<String, WorldHandle> stickyHandles = new HashMap<>();
    /** ★v1.9(27 号)sticky 开关(同 (map, session) 幂等同句柄;failFromNowOn 时关闭并清表)。 */
    private volatile boolean sticky;

    /** 获取计数(引用/释放断言用)。 */
    public int acquireCount;
    /** 释放计数。 */
    public int releaseCount;
    /** 会话销毁计数(解散触发 discardSession 断言用)。 */
    public int discardCount;
    /** 被销毁的会话 id(按调用序记录;既有 acquire/release 计数不变)。 */
    public final List<SessionId> discardedSessions = new ArrayList<>();
    /** ★v1.9(27 号)接缝时序录(null = 不录):release/discardSession 按调用序追加。 */
    public final List<String> seamLog;

    private FakeWorldPort(Function<MapDefinition, CompletableFuture<WorldHandle>> behavior,
                          List<String> seamLog, boolean sticky) {
        this.behavior = behavior;
        this.seamLog = seamLog;
        this.sticky = sticky;
    }

    /** 即时成功(每次新句柄,实例名 = mapId-会话短码)。 */
    public static FakeWorldPort immediate() {
        return new FakeWorldPort(map -> CompletableFuture.completedFuture(
                new WorldHandle(proxyWorld(), map.id() + "-copy", map,
                        map.resetStrategy())), null, false);
    }

    /**
     * ★v1.9(27 号)同句柄幂等(镜像真端口表命中:同 (map, session) 键返回同一
     * WorldHandle/同一代理 World——WorldPortImpl 同键命中路径;离线「同图重开
     * from == to」与「同世界复用」判据依赖它,否则 current.world() == plan.world.world()
     * 恒 false、B3.5 同世界路径离线不可达)。seamLog 传 null 则不录时序。
     */
    public static FakeWorldPort sticky(List<String> seamLog) {
        return new FakeWorldPort(null, seamLog, true);
    }

    /** 即时失败(注入异常;超时路径共用本分支,传 TimeoutException 即超时等价物)。 */
    public static FakeWorldPort failing(Throwable cause) {
        return new FakeWorldPort(map -> CompletableFuture.failedFuture(cause), null, false);
    }

    /** 运行期切换为失败(首切实例就位后拦目标切换的 A6 注入口;sticky 同步关闭并清表)。 */
    public void failFromNowOn(Throwable cause) {
        behavior = map -> CompletableFuture.failedFuture(cause);
        sticky = false;
        stickyHandles.clear();
    }

    @Override
    public CompletableFuture<WorldHandle> acquire(MapDefinition map, SessionId forSession) {
        Objects.requireNonNull(map, "map");
        Objects.requireNonNull(forSession, "forSession");
        acquireCount++;
        if (sticky) {
            // ★v1.9(27 号):同 (mapId, 会话) 键命中 → 幂等返回同句柄(镜像真端口
            // 表命中:计数 +1 返回同对象);miss → 造新句柄入表(实例名按真端口基准)。
            WorldHandle handle = stickyHandles.computeIfAbsent(
                    map.id() + "|" + forSession.value(),
                    key -> new WorldHandle(proxyWorld(), map.id() + "-" + forSession.shortCode(),
                            map, map.resetStrategy()));
            return CompletableFuture.completedFuture(handle);
        }
        return behavior.apply(map);
    }

    @Override
    public CompletableFuture<Void> release(WorldHandle handle) {
        releaseCount++;
        if (seamLog != null) {
            seamLog.add("release");        // ★v1.9(27 号)时序录
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> discardSession(SessionId session) {
        Objects.requireNonNull(session, "session");
        discardCount++;
        discardedSessions.add(session);
        if (seamLog != null) {
            seamLog.add("discardSession"); // ★v1.9(27 号)时序录
        }
        return CompletableFuture.completedFuture(null);
    }
}
