package com.hydrokarbs.session;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import java.util.stream.Stream;

import org.bukkit.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.hydrokarbs.bridge.world.WorldLoader;
import com.hydrokarbs.bridge.world.WorldPortImpl;
import com.hydrokarbs.engine.port.EngineServices;
import com.hydrokarbs.engine.port.ResetStrategy;
import com.hydrokarbs.engine.port.WorldHandle;
import com.hydrokarbs.engine.session.DissolveReason;
import com.hydrokarbs.engine.session.Session;
import com.hydrokarbs.engine.session.SessionId;
import com.hydrokarbs.engine.session.SessionPolicy;
import com.hydrokarbs.engine.session.SessionServiceImpl;
import com.hydrokarbs.engine.session.SessionSpec;
import com.hydrokarbs.engine.mode.MatchModeRegistry;
import com.hydrokarbs.map.MapDefinition;
import com.hydrokarbs.map.MapRegistry;
import com.hydrokarbs.testkit.FakeWorldPort;
import com.hydrokarbs.testkit.MapTestKit;
import com.hydrokarbs.testkit.OfflineAssembly;

/**
 * ★17 世界零泄漏(§16.2/§3.7)——<b>首建后永久生效,不得为通过功能放宽本类断言</b>。
 * 检验对象 = {@link WorldPortImpl} 计数与处置逻辑(假 {@link WorldLoader} 驱动,
 * 百次循环离线可跑;真服抽查归 2.5 闭环,两处合计才是 ★17 完整证据)。★v1.8 起
 * 复制源 = 声明自带路径、副本名 = 路径派生 id + 会话短码(基准重写,非放宽)。
 *
 * <p>覆盖:①100 次循环零泄漏;②并发合并计数;③两种处置策略;④缺声明目录;
 * ⑤准备超时;⑥REUSE_DIRTY 同会话复载不复制;⑦清残留路径边界;⑧discardSession
 * (★v1.4 复用窗口 = 会话生命周期,含复制中途解散的迟到 in-flight 端到端)。
 * 另附状态不变式四条、线程契约断言与 release 两种完成时机语义。
 */
class WorldRefCountTest {

    // ── 执行器域记录(线程契约断言:装卸=main 域,复制/删除=io 域) ──────────

    /** 当前执行器域("main"/"io"/"test" 外);直跑执行器包装时置位。 */
    private static final AtomicReference<String> ZONE = new AtomicReference<>("test");

    private static final class FakeWorldLoader implements WorldLoader {

        final List<String> zonesAtCreate = new java.util.concurrent.CopyOnWriteArrayList<>();
        final List<String> zonesAtUnload = new java.util.concurrent.CopyOnWriteArrayList<>();
        final AtomicInteger createCount = new AtomicInteger();
        final AtomicInteger unloadCount = new AtomicInteger();
        final ConcurrentMap<World, String> worldNames = new ConcurrentHashMap<>();
        final Set<String> loaded = ConcurrentHashMap.newKeySet();
        volatile boolean unloadResult = true;
        volatile boolean failNextCreate;

        @Override
        public World createWorld(String instanceName, Path copiedDir) {
            zonesAtCreate.add(ZONE.get());
            createCount.incrementAndGet();
            if (failNextCreate) {
                failNextCreate = false;
                throw new IllegalStateException("注入的加载失败: " + instanceName);
            }
            World world = FakeWorldPort.proxyWorld();
            worldNames.put(world, instanceName);
            loaded.add(instanceName);
            return world;
        }

        @Override
        public boolean unloadWorld(World world, boolean save) {
            zonesAtUnload.add(ZONE.get());
            unloadCount.incrementAndGet();
            String name = worldNames.remove(world);
            if (name != null) {
                loaded.remove(name);
            }
            return unloadResult;
        }

        @Override
        public List<String> loadedWorldNames() {
            return List.copyOf(loaded);
        }
    }

    /** 挂起式 IO 执行器(并发合并/超时测试):每任务一线程,先等门闩放行。 */
    private static final class GatedThreadedIo implements java.util.concurrent.Executor {

        private final CountDownLatch gate = new CountDownLatch(1);
        final AtomicInteger tasks = new AtomicInteger();

        @Override
        public void execute(java.lang.Runnable command) {
            Thread worker = new Thread(() -> {
                try {
                    gate.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                ZONE.set("io");
                try {
                    command.run();
                } finally {
                    ZONE.set("test");
                }
                tasks.incrementAndGet();
            }, "world-io-test");
            worker.start();
        }

        void open() {
            gate.countDown();
        }
    }

    /** 测试装置:副本根 + 假载器 + 直跑(或门闩)双执行器 + 真端口实现
     *  (★v1.8:端口不再持模板根——复制源随各声明的 worldDirectoryPath)。 */
    private static final class Rig {

        final Path copies;
        final FakeWorldLoader loader = new FakeWorldLoader();
        final AtomicInteger ioTasks = new AtomicInteger();
        final GatedThreadedIo gatedIo;      // null = 直跑 IO
        final WorldPortImpl port;

        Rig(Path copies, GatedThreadedIo gatedIo, long timeoutMillis) {
            this.copies = copies;
            this.gatedIo = gatedIo;
            java.util.concurrent.Executor io = gatedIo != null ? gatedIo : command -> {
                ZONE.set("io");
                try {
                    command.run();
                } finally {
                    ZONE.set("test");
                }
                ioTasks.incrementAndGet();
            };
            java.util.concurrent.Executor main = command -> {
                ZONE.set("main");
                try {
                    command.run();
                } finally {
                    ZONE.set("test");
                }
            };
            Logger logger = Logger.getLogger("world-ref-count-test");
            logger.setUseParentHandlers(false);
            port = new WorldPortImpl(loader, copies, main, io,
                    java.time.Duration.ofMillis(timeoutMillis), logger);
        }

        static Rig direct(Path copies) {
            return new Rig(copies, null, 30_000L);
        }
    }

    // ── 便捷件 ───────────────────────────────────────────────

    private static final SessionId SESSION = new SessionId("arena-1");

    /** 声明构造(★v1.8 新形状):路径 = {@code <declarations>/<id>},策略进约定键。 */
    private static MapDefinition map(Path declarations, String id, ResetStrategy reset) {
        return MapTestKit.declaration(declarations.resolve(id), Set.of("common"), reset);
    }

    private static void layTemplate(Path declarations, String mapId, String... files) throws IOException {
        Path dir = declarations.resolve(mapId);
        Files.createDirectories(dir);
        for (String file : files) {
            Files.writeString(dir.resolve(file), "template-content:" + file);
        }
    }

    private static WorldHandle await(CompletableFuture<WorldHandle> future) {
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("等待句柄被中断", e);
        } catch (ExecutionException e) {
            throw new AssertionError("获取句柄异常完成: " + e.getCause(), e.getCause());
        } catch (java.util.concurrent.TimeoutException e) {
            throw new AssertionError("等待句柄超时(5s)", e);
        }
    }

    private static void awaitVoid(CompletableFuture<Void> future) {
        try {
            assertNull(future.get(5, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("等待释放被中断", e);
        } catch (ExecutionException e) {
            throw new AssertionError("释放异常完成: " + e.getCause(), e.getCause());
        } catch (java.util.concurrent.TimeoutException e) {
            throw new AssertionError("等待释放超时(5s)", e);
        }
    }

    private static void assertInvariants(WorldPortImpl port, String where) {
        // 不变式①:表中每个句柄 count ≥ 1;②:同一 instanceName 至多一条
        // (条目数 == 唯一 instanceName 数;refCounts 的 Map 键去重会掩盖重复,须双口对账);
        // ③:in-flight 必清(④归零必移除并入各用例的显式断言)
        var counts = port.refCounts();
        counts.forEach((name, count) -> assertTrue(count >= 1,
                where + ": count ≥ 1 被破(" + name + "=" + count + ")"));
        assertEquals(port.refEntryCount(), counts.size(),
                where + ": 条目数与唯一 instanceName 数应相等(同 instanceName 至多一条)");
        assertEquals(0, port.inflightCount(), where + ": in-flight 应为空");
    }

    // ── ① 100 次循环零泄漏(+不变式/线程契约并入) ─────────────────────

    @Test
    void hundredCycleAcquireReleaseLeavesZeroLeak(@TempDir Path declarations, @TempDir Path copies)
            throws IOException {
        layTemplate(declarations, "lobby_main", "level.dat", "region.marker");
        layTemplate(declarations, "lobby_mini", "level.dat");
        Rig rig = Rig.direct(copies);
        MapDefinition reuseMap = map(declarations, "lobby_main", ResetStrategy.REUSE_DIRTY);
        MapDefinition restoreMap = map(declarations, "lobby_mini", ResetStrategy.TEMPLATE_RESTORE);

        // 模拟切换时序:先获取新图,后释放旧图(§3.7 释放时机);两图交替 100 轮,
        // 两种处置策略都随轮次被覆盖(REUSE_DIRTY 复载/TEMPLATE_RESTORE 重拷)
        WorldHandle current = await(rig.port.acquire(reuseMap, SESSION));
        int reuseReloads = 0;
        for (int i = 1; i <= 100; i++) {
            boolean toMini = i % 2 == 1;
            WorldHandle next = await(rig.port.acquire(toMini ? restoreMap : reuseMap, SESSION));
            WorldHandle retiring = current;
            awaitVoid(rig.port.release(retiring));
            // 回图重新获取是全新句柄(归零条目已移除,不依赖缓存侥幸)
            if (retiring.map().id().equals(next.map().id())) {
                assertNotSame(retiring, next, "同 key 归零后的重新获取应发放新句柄");
            }
            current = next;
            if (!toMini) {
                reuseReloads++;
            }
            assertInvariants(rig.port, "第 " + i + " 轮后");
        }
        awaitVoid(rig.port.release(current));
        assertTrue(reuseReloads >= 49, "100 轮中 REUSE_DIRTY 侧应被反复覆盖(实际 " + reuseReloads + " 轮)");

        // 零泄漏四断言:表清空、无未释放句柄(装卸次数相等)、无残留已加载副本名、
        // 目录按策略处置(REUSE_DIRTY 保留 / TEMPLATE_RESTORE 消失)
        assertTrue(rig.port.refCounts().isEmpty(), "100 次循环后引用计数表应清空: " + rig.port.refCounts());
        assertEquals(0, rig.port.inflightCount(), "in-flight 应已清空");
        assertEquals(rig.loader.createCount.get(), rig.loader.unloadCount.get(),
                "每次加载都应配对一次卸载(create=" + rig.loader.createCount
                        + ", unload=" + rig.loader.unloadCount + ")");
        assertTrue(rig.loader.loadedWorldNames().isEmpty(),
                "已加载世界名单应无残留副本: " + rig.loader.loadedWorldNames());
        assertTrue(Files.isDirectory(copies.resolve("lobby_main-" + SESSION.shortCode())),
                "REUSE_DIRTY 释放后应保留副本目录(同会话复载用)");
        assertFalse(Files.exists(copies.resolve("lobby_mini-" + SESSION.shortCode())),
                "TEMPLATE_RESTORE 释放后应删除副本目录");
        assertInvariants(rig.port, "循环结束后");
        // 线程契约:装卸全部发生在注入的主线程执行器域内;复制/删除任务跑在 IO 执行器上
        assertTrue(rig.loader.zonesAtCreate.stream().allMatch("main"::equals),
                "createWorld 应全部在主线程执行器域: " + rig.loader.zonesAtCreate);
        assertTrue(rig.loader.zonesAtUnload.stream().allMatch("main"::equals),
                "unloadWorld 应全部在主线程执行器域: " + rig.loader.zonesAtUnload);
        assertTrue(rig.ioTasks.get() >= 101, "复制/删除任务应全部经由 IO 执行器(实际 "
                + rig.ioTasks.get() + ")");
    }

    // ── ② 并发合并与计数(5 次并发同句柄、计数 5、5 次释放才一次卸载) ────────

    @Test
    @SuppressWarnings("unchecked")
    void concurrentAcquireMergesToSingleCreateAndCountsFive(
            @TempDir Path declarations, @TempDir Path copies) throws Exception {
        layTemplate(declarations, "arena_x", "level.dat");
        GatedThreadedIo gatedIo = new GatedThreadedIo();
        Rig rig = new Rig(copies, gatedIo, 30_000L);
        MapDefinition target = map(declarations, "arena_x", ResetStrategy.REUSE_DIRTY);

        // 门闩关住 IO:首个获取在途(复制未完成),后续 4 个并发 miss 合并到同一 future
        CompletableFuture<WorldHandle> first = rig.port.acquire(target, SESSION);
        CompletableFuture<WorldHandle>[] merged = new CompletableFuture[4];
        for (int i = 0; i < merged.length; i++) {
            merged[i] = rig.port.acquire(target, SESSION);
        }
        assertEquals(1, rig.port.inflightCount(), "并发期应恰有一个 in-flight");
        gatedIo.open();

        WorldHandle handle = await(first);
        for (CompletableFuture<WorldHandle> future : merged) {
            assertSame(handle, await(future), "并发合并:全部等待者应拿到同一句柄");
        }
        assertEquals(1, rig.loader.createCount.get(), "同图并发只复制一次(只加载一次)");
        assertEquals(5, rig.port.refCounts().get(handle.instanceName()).intValue(),
                "5 次并发获取应计数 5: " + rig.port.refCounts());
        assertInvariants(rig.port, "并发获取完成后");

        // release 5 次后才触发一次卸载;前 4 次未归零立即完成
        for (int i = 1; i <= 4; i++) {
            CompletableFuture<Void> partial = rig.port.release(handle);
            assertTrue(partial.isDone(), "未归零的释放应立即完成(第 " + i + " 次)");
            assertEquals(0, rig.loader.unloadCount.get(), "未归零不应触发卸载");
        }
        awaitVoid(rig.port.release(handle));
        assertEquals(1, rig.loader.unloadCount.get(), "第 5 次释放才触发唯一一次卸载");
        assertTrue(rig.port.refCounts().isEmpty(), "归零条目必被移除: " + rig.port.refCounts());
        assertInvariants(rig.port, "全部释放后");
    }

    // ── ③ 两种处置策略 + ⑥ REUSE_DIRTY 同会话复载不复制(脏标记黑盒) ─────────

    @Test
    void resetStrategiesDisposeCopiesAccordingly(@TempDir Path declarations, @TempDir Path copies)
            throws IOException {
        layTemplate(declarations, "keep_x", "level.dat");
        layTemplate(declarations, "wipe_y", "level.dat");
        Rig rig = Rig.direct(copies);
        MapDefinition keep = map(declarations, "keep_x", ResetStrategy.REUSE_DIRTY);
        MapDefinition wipe = map(declarations, "wipe_y", ResetStrategy.TEMPLATE_RESTORE);

        // REUSE_DIRTY:归零保留目录;同会话再次获取直接复载、不复制
        // (黑盒脏标记:复制会清残留重抄使标记消失;复用则标记原样保留)
        WorldHandle kept = await(rig.port.acquire(keep, SESSION));
        Path keptDir = copies.resolve(kept.instanceName());
        Path keptMarker = keptDir.resolve("dirty-marker.txt");
        Files.writeString(keptMarker, "局内留下的痕迹");
        awaitVoid(rig.port.release(kept));
        assertTrue(Files.isDirectory(keptDir), "REUSE_DIRTY 归零应保留副本目录");
        WorldHandle reloaded = await(rig.port.acquire(keep, SESSION));
        assertTrue(Files.exists(keptMarker), "同会话复载不应复制(脏标记应原样保留)");
        assertNotSame(kept, reloaded, "复载发放新句柄(旧条目已移除)");
        assertEquals(2, rig.loader.createCount.get(), "复载仍应重新加载世界");
        awaitVoid(rig.port.release(reloaded));

        // TEMPLATE_RESTORE:归零删除目录;再次获取从声明目录重新复制(标记消失)
        WorldHandle wiped = await(rig.port.acquire(wipe, SESSION));
        Path wipedDir = copies.resolve(wiped.instanceName());
        Files.writeString(wipedDir.resolve("dirty-marker.txt"), "应被模板还原抹掉");
        awaitVoid(rig.port.release(wiped));
        assertFalse(Files.exists(wipedDir), "TEMPLATE_RESTORE 归零应删除副本目录");
        WorldHandle restored = await(rig.port.acquire(wipe, SESSION));
        assertFalse(Files.exists(wipedDir.resolve("dirty-marker.txt")),
                "重拷后不应残留脏标记");
        assertTrue(Files.exists(wipedDir.resolve("level.dat")), "重拷应以声明目录内容为准");
        awaitVoid(rig.port.release(restored));
        assertInvariants(rig.port, "两策略处置后");
    }

    // ── ④ 缺声明目录:future 异常完成且计数未入表 ───────────────────────

    @Test
    void missingDeclarationDirFailsCleanlyWithoutTableEntry(@TempDir Path declarations,
                                                            @TempDir Path copies)
            throws IOException {
        Rig rig = Rig.direct(copies);
        CompletableFuture<WorldHandle> future =
                rig.port.acquire(map(declarations, "ghost", ResetStrategy.REUSE_DIRTY), SESSION);

        ExecutionException failure = assertThrows(ExecutionException.class,
                () -> future.get(5, TimeUnit.SECONDS));
        assertTrue(failure.getCause().getMessage().contains("模板目录缺失"),
                "缺声明目录应点名复制源路径: " + failure.getCause().getMessage());
        assertTrue(rig.port.refCounts().isEmpty(), "失败获取不得入表");
        assertEquals(0, rig.port.inflightCount(), "失败后 in-flight 必清");
        assertEquals(0, rig.loader.createCount.get(), "缺声明目录不应触发加载");

        // 越出声明父目录的路径解析(..):声明路径构造期规范化,解析越界照旧走
        // 同一失败路径,且副本根零新增(walk 含根目录自身,须剔除后计数)
        CompletableFuture<WorldHandle> traversal =
                rig.port.acquire(map(declarations, "../evil", ResetStrategy.TEMPLATE_RESTORE), SESSION);
        assertThrows(ExecutionException.class, () -> traversal.get(5, TimeUnit.SECONDS));
        try (Stream<Path> walk = Files.walk(copies)) {
            assertEquals(0, walk.filter(path -> !path.equals(copies)).count(),
                    "越界路径解析不得在副本根产生任何路径");
        }
    }

    // ── ⑤ 准备超时(注入短超时):异常完成、in-flight 已清、后续可重走 ─────────

    @Test
    void prepareTimeoutCompletesExceptionallyAndAllowsRetry(
            @TempDir Path declarations, @TempDir Path copies) throws Exception {
        layTemplate(declarations, "slow_z", "level.dat");
        GatedThreadedIo gatedIo = new GatedThreadedIo();
        Rig rig = new Rig(copies, gatedIo, 150L);       // 短超时替代 30s(可测口径)
        MapDefinition target = map(declarations, "slow_z", ResetStrategy.REUSE_DIRTY);

        // IO 门闩关住 → 复制挂起 → 150ms 超时:future 异常完成、in-flight 清、未入表
        CompletableFuture<WorldHandle> hung = rig.port.acquire(target, SESSION);
        assertFalse(hung.isDone(), "门闩未开时获取应挂起");
        assertThrows(ExecutionException.class, () -> hung.get(2, TimeUnit.SECONDS));
        assertTrue(hung.isCompletedExceptionally(), "超时应异常完成");
        assertEquals(0, rig.port.inflightCount(), "超时后 in-flight 必清(否则该 key 永远等死 future)");
        assertTrue(rig.port.refCounts().isEmpty(), "超时获取不得入表");

        // 放行门闩:迟到的复制流水走到加载点被迟到守卫拦下(不加载、不入表)
        gatedIo.open();
        awaitQuiet(200);
        assertEquals(0, rig.loader.createCount.get(),
                "超时已拒绝的获取,迟到流水不得加载世界入表");

        // 后续同 key 获取可正常走通(不依赖已死的 in-flight)
        WorldHandle retried = await(rig.port.acquire(target, SESSION));
        assertEquals(1, rig.loader.createCount.get(), "重试获取应正常加载");
        awaitVoid(rig.port.release(retried));
        assertInvariants(rig.port, "超时与重试后");
    }

    /** 安静等待(门闩放行后的迟到流水收尾)。 */
    private static void awaitQuiet(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ── ⑦ 清残留与删除的路径形态边界(仅限副本根下本实现形态) ─────────────

    @Test
    void copyCleanupIsConfinedToManagedShape(@TempDir Path declarations, @TempDir Path copies)
            throws IOException {
        layTemplate(declarations, "shaped_a", "level.dat", "wanted.txt");
        Rig rig = Rig.direct(copies);
        MapDefinition shaped = map(declarations, "shaped_a", ResetStrategy.TEMPLATE_RESTORE);
        SessionId session = SESSION;
        String instanceName = "shaped_a-" + session.shortCode();

        // 预置三类干扰物:同名残留(带垃圾)、同根异名目录、声明目录文件
        Path staleDir = copies.resolve(instanceName);
        Files.createDirectories(staleDir);
        Files.writeString(staleDir.resolve("junk-leftover.txt"), "上次异常残留");
        Path unrelatedDir = copies.resolve("zzz-unrelated-1");
        Files.createDirectories(unrelatedDir);
        Files.writeString(unrelatedDir.resolve("keep.txt"), "与本实现无关的目录");
        Path declarationMarker = declarations.resolve("shaped_a").resolve("level.dat");

        // TEMPLATE_RESTORE 获取:同名残留被清(形态匹配)后以声明目录内容重建;
        // 异名目录与声明目录原封不动——删除范围仅限副本根下本实现形态
        WorldHandle handle = await(rig.port.acquire(shaped, session));
        assertFalse(Files.exists(staleDir.resolve("junk-leftover.txt")), "同名残留应被清空");
        assertTrue(Files.exists(staleDir.resolve("level.dat")), "副本应以声明目录内容重建");
        assertTrue(Files.exists(unrelatedDir.resolve("keep.txt")), "异名目录不得被触碰");
        assertTrue(Files.exists(declarationMarker), "声明目录母本不得被触碰");

        // 归零删除也只删本实现形态的目录,异名目录仍在
        awaitVoid(rig.port.release(handle));
        assertFalse(Files.exists(staleDir), "TEMPLATE_RESTORE 归零删除副本目录");
        assertTrue(Files.exists(unrelatedDir), "归零删除后异名目录仍应在");
        assertTrue(Files.exists(declarationMarker), "归零删除不得波及声明目录母本");
    }

    // ── release 两种完成时机语义 + 同图重开计数配平 ────────────────────

    @Test
    void releaseCompletionSemanticsAndSameMapBalance(@TempDir Path declarations, @TempDir Path copies)
            throws IOException {
        layTemplate(declarations, "paired_p", "level.dat");
        Rig rig = Rig.direct(copies);
        MapDefinition target = map(declarations, "paired_p", ResetStrategy.REUSE_DIRTY);

        // 未归零:立即完成,零装卸、零 IO 任务
        WorldHandle first = await(rig.port.acquire(target, SESSION));
        WorldHandle second = await(rig.port.acquire(target, SESSION));   // 同图重开:命中 +1
        assertSame(first, second, "同 (map, session) 重复获取应幂等返回同句柄");
        int ioTasksBefore = rig.ioTasks.get();
        CompletableFuture<Void> partial = rig.port.release(first);
        assertTrue(partial.isDone(), "未归零释放应立即完成");
        assertEquals(0, rig.loader.unloadCount.get(), "未归零不应卸载");
        assertEquals(ioTasksBefore, rig.ioTasks.get(), "未归零不应派发 IO 任务");

        // 归零(REUSE_DIRTY):卸载完成后 future 才完成,目录保留
        CompletableFuture<Void> finalRelease = rig.port.release(second);
        assertTrue(finalRelease.isDone(), "归零处置(REUSE_DIRTY)应同步完成");
        assertEquals(1, rig.loader.unloadCount.get(), "归零应恰卸载一次");
        assertTrue(Files.isDirectory(copies.resolve(first.instanceName())),
                "REUSE_DIRTY 归零保留目录");

        // 重复释放:幂等无害(条目已移除 → 未知句柄直接完成)
        assertDoesNotThrow(() -> awaitVoid(rig.port.release(first)));
        assertDoesNotThrow(() -> awaitVoid(rig.port.release(second)));
        assertEquals(1, rig.loader.unloadCount.get(), "重复释放不得再次卸载");
        assertInvariants(rig.port, "配平与幂等检查后");

        // 归零(TEMPLATE_RESTORE)的 future 完成点 = 目录删除完成之后
        layTemplate(declarations, "paired_q", "level.dat");
        MapDefinition wipe = map(declarations, "paired_q", ResetStrategy.TEMPLATE_RESTORE);
        WorldHandle wiped = await(rig.port.acquire(wipe, SESSION));
        int ioBeforeWipe = rig.ioTasks.get();
        CompletableFuture<Void> wipeRelease = rig.port.release(wiped);
        assertTrue(wipeRelease.isDone(), "归零处置(TEMPLATE_RESTORE)应在删除完成后完成");
        assertFalse(Files.exists(copies.resolve(wiped.instanceName())), "删除已生效");
        assertTrue(rig.ioTasks.get() > ioBeforeWipe, "删目录应派发到 IO 执行器");
    }

    // ── 卸载失败路径:SEVERE 语义 + 保留目录 + 不阻塞(处置完成) ──────────

    @Test
    void unloadFailureKeepsDirAndStillCompletes(@TempDir Path declarations, @TempDir Path copies)
            throws IOException {
        layTemplate(declarations, "stubborn_s", "level.dat");
        Rig rig = Rig.direct(copies);
        List<String> logLines = new java.util.concurrent.CopyOnWriteArrayList<>();
        Logger spy = Logger.getLogger("world-ref-count-unload-fail");
        spy.setUseParentHandlers(false);
        spy.addHandler(new java.util.logging.Handler() {
            @Override public void publish(java.util.logging.LogRecord record) {
                logLines.add(record.getLevel() + "|" + record.getMessage());
            }
            @Override public void flush() { }
            @Override public void close() { }
        });
        // 以注入失败日志与失败卸载的端口复测(★v1.8:构造不再收模板根)
        WorldPortImpl failingPort = new WorldPortImpl(rig.loader, copies,
                command -> {
                    ZONE.set("main");
                    try {
                        command.run();
                    } finally {
                        ZONE.set("test");
                    }
                },
                command -> {
                    ZONE.set("io");
                    try {
                        command.run();
                    } finally {
                        ZONE.set("test");
                    }
                }, java.time.Duration.ofSeconds(30), spy);
        rig.loader.unloadResult = false;       // 注入:Bukkit.unloadWorld 返回 false

        MapDefinition target = map(declarations, "stubborn_s", ResetStrategy.TEMPLATE_RESTORE);
        WorldHandle handle = await(failingPort.acquire(target, SESSION));
        Path dir = copies.resolve(handle.instanceName());
        awaitVoid(failingPort.release(handle));       // 卸载失败不阻塞:处置仍完成

        assertEquals(0, failingPort.refCounts().size(), "归零条目仍应移除");
        assertTrue(Files.isDirectory(dir), "卸载失败应保留副本目录");
        assertTrue(logLines.stream().anyMatch(line -> line.startsWith("SEVERE|")
                        && line.contains("keeping directory")),
                "卸载失败应记 SEVERE 并注明保留目录: " + logLines);
    }

    // ── ⑧ 会话销毁 discardSession(★v1.4:复用窗口 = 会话生命周期;
    //    ★v1.8 断言族按新基准重写:复制源 = 声明路径,命名 = 路径派生 id) ──────

    /**
     * ⑧a REUSE_DIRTY 主线:释放(模拟切换离开)保留 → 再获取复载不复制(复用语义
     * 在窗口内零改动)→ discardSession 后目录销毁、计数表清零。
     */
    @Test
    void discardSessionDestroysReusedCopiesAndKeepsReuseSemanticsUntilThen(
            @TempDir Path declarations, @TempDir Path copies) throws IOException {
        layTemplate(declarations, "reuse_d", "level.dat");
        Rig rig = Rig.direct(copies);
        MapDefinition target = map(declarations, "reuse_d", ResetStrategy.REUSE_DIRTY);

        // 获取 → 释放(模拟 B5 换图离开):REUSE_DIRTY 归零保留目录
        WorldHandle first = await(rig.port.acquire(target, SESSION));
        Path dir = copies.resolve(first.instanceName());
        Files.writeString(dir.resolve("dirty-marker.txt"), "局内留下的痕迹");
        awaitVoid(rig.port.release(first));
        assertTrue(Files.isDirectory(dir), "REUSE_DIRTY 释放后保留副本目录(复用窗口仍开)");

        // 再获取:复用既有目录、世界仍重新加载。未重新复制的判据 = 黑盒脏标记原样
        // (与同族 resetStrategiesDisposeCopiesAccordingly 一致:复制会清残留重抄使
        // 标记消失,复用则标记保留;ioTasks 无法区分——prepare 任务恒派发,复用
        // 只跳过其内部 copyTree 段)
        WorldHandle second = await(rig.port.acquire(target, SESSION));
        assertTrue(Files.exists(dir.resolve("dirty-marker.txt")),
                "复载不应重新复制(脏标记应原样保留,复用语义)");
        assertEquals(2, rig.loader.createCount.get(), "复载仍应重新加载世界");
        assertNotSame(first, second, "复载发放新句柄(旧条目已移除)");

        // discardSession:目录销毁、计数表清零(复用窗口随会话解散关闭)
        awaitVoid(rig.port.discardSession(SESSION));
        assertFalse(Files.exists(dir), "解散后副本目录应销毁(无论策略)");
        assertTrue(rig.port.refCounts().isEmpty(), "解散后计数表应清零: " + rig.port.refCounts());
        assertEquals(0, rig.port.refEntryCount(), "解散后条目应清零");
        assertInvariants(rig.port, "discardSession 后");
    }

    /** ⑧b 双图会话:map1 释放(保留)→ map2 在用 → discardSession 两目录全部消失。 */
    @Test
    void discardSessionDestroysEveryCopyOfATwoMapSession(@TempDir Path declarations,
                                                         @TempDir Path copies) throws IOException {
        layTemplate(declarations, "dual_first", "level.dat");
        layTemplate(declarations, "dual_second", "level.dat");
        Rig rig = Rig.direct(copies);
        MapDefinition firstMap = map(declarations, "dual_first", ResetStrategy.REUSE_DIRTY);
        MapDefinition secondMap = map(declarations, "dual_second", ResetStrategy.REUSE_DIRTY);

        // map1 在用 → 切到 map2(B5 释放 map1,会话活着目录保留);map2 仍持句柄
        WorldHandle onFirst = await(rig.port.acquire(firstMap, SESSION));
        awaitVoid(rig.port.release(onFirst));
        WorldHandle onSecond = await(rig.port.acquire(secondMap, SESSION));
        Path firstDir = copies.resolve(onFirst.instanceName());
        Path secondDir = copies.resolve(onSecond.instanceName());
        assertTrue(Files.isDirectory(firstDir), "切换离开后 map1 副本目录应保留");
        assertTrue(Files.isDirectory(secondDir), "map2 在用,副本目录在");

        // discardSession:两目录全部销毁(存活条目处置 + 曾发放名册兜删)
        awaitVoid(rig.port.discardSession(SESSION));
        assertFalse(Files.exists(firstDir), "曾发放已保留的 map1 目录应销毁(发放名册)");
        assertFalse(Files.exists(secondDir), "在用的 map2 目录应销毁(存活条目处置)");
        assertTrue(rig.port.refCounts().isEmpty(), "计数表应清零: " + rig.port.refCounts());
        assertEquals(2, rig.loader.unloadCount.get(), "两个句柄都应卸载不存档");
        assertInvariants(rig.port, "双图销毁后");
    }

    /** ⑧c TEMPLATE_RESTORE:discardSession 与既有 release 归零处置结论一致(删目录)。 */
    @Test
    void discardSessionConsistentWithReleaseZeroDisposalForTemplateRestore(
            @TempDir Path declarations, @TempDir Path copies) throws IOException {
        layTemplate(declarations, "wipe_both", "level.dat");
        Rig rig = Rig.direct(copies);
        MapDefinition target = map(declarations, "wipe_both", ResetStrategy.TEMPLATE_RESTORE);

        // 既有 release 归零处置(同族参考 resetStrategiesDisposeCopiesAccordingly):删目录
        WorldHandle released = await(rig.port.acquire(target, SESSION));
        Path releasedDir = copies.resolve(released.instanceName());
        awaitVoid(rig.port.release(released));
        assertFalse(Files.exists(releasedDir), "TEMPLATE_RESTORE 归零释放应删目录");

        // discardSession 对存活条目的处置:结论一致
        WorldHandle live = await(rig.port.acquire(target, SESSION));
        Path liveDir = copies.resolve(live.instanceName());
        assertTrue(Files.isDirectory(liveDir), "再次获取应重建目录");
        awaitVoid(rig.port.discardSession(SESSION));
        assertFalse(Files.exists(liveDir), "TEMPLATE_RESTORE 经 discardSession 也删目录");
        assertTrue(rig.port.refCounts().isEmpty(), "计数表应清零: " + rig.port.refCounts());
        assertEquals(2, rig.loader.unloadCount.get(), "两次处置都卸载不存档");
        assertInvariants(rig.port, "TEMPLATE_RESTORE 双处置后");
    }

    /**
     * ⑧d 迟到 in-flight(复制中途解散,真会话服务 × 真端口端到端):
     * 解散先作废挂起计划并发起 discardSession(短码记入已死集);其后复制完成、
     * 世界照常加载发放——迟到的 acquire whenComplete 经 onWorldReady 的 aborted
     * 守卫(§14.19:获取成功而计划已死 → 即还引用)释放句柄,归零处置发现
     * 短码已死 → 无论策略删其副本目录。
     */
    @Test
    void dissolveDuringCopyReleasesLateHandleViaOnWorldReadyAndDeletesCopy(
            @TempDir Path declarations, @TempDir Path copies) throws Exception {
        GatedThreadedIo gatedIo = new GatedThreadedIo();
        Rig rig = new Rig(copies, gatedIo, 30_000L);

        // 最小会话装配:真会话服务 + 真世界端口(假载器、门闩 IO;回程口直跑)
        // (★v1.8 新基准:注册式声明落盘 + 注册走 MapTestKit,缺省策略即 REUSE_DIRTY)
        MapRegistry maps = new MapRegistry();
        MapTestKit.registered(maps, declarations, "late_e2e", Set.of("common"));
        MapDefinition mapDef = maps.byId("late_e2e").orElseThrow();
        MatchModeRegistry modes = new MatchModeRegistry(id -> OfflineAssembly.cfg());
        modes.register(SwitchHarness.fixedProvider(SwitchHarness.mode("late_m", 0, 100)));
        SessionServiceImpl service = new SessionServiceImpl.Builder()
                .modes(modes)
                .worlds(rig.port)
                .maps(maps)
                .configSource(id -> OfflineAssembly.cfg())
                .policy(SessionPolicy.defaults(SessionPolicy.Topology.ROTATION))
                .fallbackModeId("")
                .engineServices(new EngineServices(new OfflineAssembly.FakePlatform()))
                .mainExecutor(Runnable::run)       // A6 完成回程直跑(离线纪律)
                .build();

        // 建会话:首切发起 → acquire 在途(复制未完成,门闩关住)
        Session session = service.create(
                new SessionSpec(new SessionId("late-e2e"), "late_m", List.of(), List.of()));
        Path dir = copies.resolve(mapDef.id() + "-" + session.id().shortCode());

        // 复制中途解散:计划作废 + 短码入已死集(此刻无存活条目、无已发放名册)
        service.dissolve(session.id(), DissolveReason.SHUTDOWN);
        assertTrue(service.all().isEmpty(), "会话已摘除");
        assertTrue(rig.port.refCounts().isEmpty(), "解散时获取尚未入表");
        assertEquals(0, rig.loader.unloadCount.get(), "解散时尚无句柄可卸载");

        // 放行:复制 → 加载 → 发放 → onWorldReady 守卫迟到释放 → 已死短码 → 删目录。
        // 两阶段等待:先等复制真把目录建出来(否则「不存在」在复制前也成立,轮询空转),
        // 再等守卫链把目录删掉
        gatedIo.open();
        awaitUntil(() -> Files.exists(dir));
        awaitUntil(() -> !Files.exists(dir));
        assertFalse(Files.exists(dir), "已死会话的迟到副本目录应删除(无论策略)");
        assertEquals(1, rig.loader.unloadCount.get(), "迟到句柄应经 onWorldReady 释放并卸载");
        assertTrue(rig.port.refCounts().isEmpty(), "归零条目必移除: " + rig.port.refCounts());
        assertEquals(0, rig.port.inflightCount(), "in-flight 必清");
        assertInvariants(rig.port, "迟到 in-flight 收场后");
    }

    /** 条件轮询(守卫链在发放线程异步收场:轮询至断言前提成立或超时)。 */
    private static void awaitUntil(java.util.function.BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 5_000;
        try {
            while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
                Thread.sleep(10);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** ⑧e 幂等:重复销毁无异常、零重复处置;从未获取过的会话销毁亦无害。 */
    @Test
    void discardSessionIsIdempotentAndHarmlessForUnknownSessions(
            @TempDir Path declarations, @TempDir Path copies) throws IOException {
        layTemplate(declarations, "idem_i", "level.dat");
        Rig rig = Rig.direct(copies);
        MapDefinition target = map(declarations, "idem_i", ResetStrategy.REUSE_DIRTY);

        WorldHandle handle = await(rig.port.acquire(target, SESSION));
        Path dir = copies.resolve(handle.instanceName());

        // 首次销毁 + 重复调用:无异常、无重复处置(卸载一次、删任务不重复)
        awaitVoid(rig.port.discardSession(SESSION));
        int ioAfterFirstDiscard = rig.ioTasks.get();
        int unloadsAfterFirstDiscard = rig.loader.unloadCount.get();
        assertDoesNotThrow(() -> awaitVoid(rig.port.discardSession(SESSION)));
        assertDoesNotThrow(() -> awaitVoid(rig.port.discardSession(SESSION)));
        assertFalse(Files.exists(dir), "目录已销毁");
        assertEquals(unloadsAfterFirstDiscard, rig.loader.unloadCount.get(),
                "重复销毁不得再次卸载");
        assertEquals(ioAfterFirstDiscard, rig.ioTasks.get(), "重复销毁不得重复派删");
        assertTrue(rig.port.refCounts().isEmpty(), "计数表应清零: " + rig.port.refCounts());

        // 从未获取过的会话:幂等无害(空处置、立即完成)
        SessionId stranger = new SessionId("stranger-9");
        assertDoesNotThrow(() -> awaitVoid(rig.port.discardSession(stranger)));
        assertInvariants(rig.port, "幂等销毁后");
    }
}
