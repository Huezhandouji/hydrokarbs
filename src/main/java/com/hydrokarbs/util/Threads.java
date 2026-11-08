package com.hydrokarbs.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * 异步 → 主线程的唯一回程口。所有跨线程回调必须经它(§10.5 边界铁律)。
 *
 * <p>两条易踩规则(§10.5):
 * <ol>
 *   <li>CompletableFuture 回调跑在完成它的线程上——{@link #supplyThenOnMain}
 *       内部必须显式 runTask 回主线程;</li>
 *   <li>主线程上调 runTask 会延后一 tick——统一走 {@link #runOnMain} 判断。</li>
 * </ol>
 *
 * <p>反向禁止:主线程不得 {@code future.get()} 阻塞等待(唯一例外 = onDisable
 * 的有界冲刷,阶段 5 落地)。
 */
public final class Threads {

    private static volatile Plugin plugin;

    /** 内部执行器:虚拟线程每任务一条(§15.3:数据库/文件 IO 线,弃用服务端异步池)。 */
    private static volatile Executor executor = Executors.newVirtualThreadPerTaskExecutor();

    private Threads() {
    }

    /** 注入插件句柄并重建内部虚拟线程执行器(onEnable 调用)。 */
    public static void init(Plugin p) {
        plugin = p;
        executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * 注入重载:阶段 5 的带背压执行器(§10.3.2)落地后替换注入——回程口语义不变。
     */
    public static void init(Plugin p, Executor customExecutor) {
        plugin = p;
        executor = customExecutor;
    }

    /** 在虚拟线程里跑阻塞逻辑;结果在主线程消费。 */
    public static <T> void supplyThenOnMain(
            Supplier<T> blockingWork, Consumer<T> onMain, Consumer<Throwable> onError) {
        CompletableFuture.supplyAsync(blockingWork, executor)
                .whenComplete((value, err) ->
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (err != null) onError.accept(err);
                            else            onMain.accept(value);
                        }));
    }

    /** 已在主线程时就地执行,不调度(避免白等一 tick)。 */
    public static void runOnMain(Runnable r) {
        if (Bukkit.isPrimaryThread()) r.run();
        else Bukkit.getScheduler().runTask(plugin, r);
    }

    /** 关闭内部执行器(onDisable 调用;不阻塞等待)。 */
    public static void shutdown() {
        Executor current = executor;
        if (current instanceof ExecutorService service) {
            service.shutdown();
        }
        executor = null;
        plugin = null;
    }
}
