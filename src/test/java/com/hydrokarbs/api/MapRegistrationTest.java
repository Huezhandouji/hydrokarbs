package com.hydrokarbs.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.hydrokarbs.engine.session.DissolveReason;
import com.hydrokarbs.engine.session.Handoff;
import com.hydrokarbs.engine.session.MatchModeSelection;
import com.hydrokarbs.engine.session.Session;
import com.hydrokarbs.engine.session.SessionId;
import com.hydrokarbs.engine.session.SwitchResult;
import com.hydrokarbs.testkit.FakeWorldPort;
import com.hydrokarbs.testkit.OfflineApi;
import com.hydrokarbs.testkit.TestPlugins;

/**
 * 地图注册通道端到端(★54~★55,★v1.2;7.5 施工文档步骤 8/11;20-D1~D7,离线;
 * ★v1.8 去模板根新基准):注册({@code registerMap})→ 目录列出 → 可被切换选中
 * 并开局 → 摘除({@code unregisterMap});坏声明响亮拒绝(声明路径缺失/非目录带
 * 完整绝对路径、同一路径重复注册点名先到者);正用摘除被拒且地图照常可用
 * (摘除拒绝是特性,不是障碍)。
 *
 * <p><b>离线口径</b>:{@code Plugin} 以动态代理占位(身份键;镜像
 * {@code FakeWorldPort#proxyWorld} 的「测试类型签名引用 Bukkit 类型、传代理不碰
 * Bukkit 静态」先例);世界目录以 {@code @TempDir} 落盘(声明按路径直交,无模板根);
 * 切换经真会话服务 + {@link FakeWorldPort}(复用 testkit,镜像 SwitchHarness 装配)。
 */
class MapRegistrationTest {

    private static final String MODE_ID = "probe_mode";

    @TempDir
    Path tempDir;

    private OfflineApi harness;
    private Plugin provider;
    private Path mapsDir;

    @BeforeEach
    void setUp() {
        harness = new OfflineApi(FakeWorldPort.immediate(), OfflineApi.mode(MODE_ID, OfflineApi.TAG));
        provider = TestPlugins.named("MapRegistrationTest-plugin");
        mapsDir = tempDir.resolve("maps");                  // 声明目录落盘挂点(★v1.8:无模板根约定)
    }

    // ── ★54 注册端到端:落盘(造声明目录)→ registerMap → 列出 → 可选中并开局 ──

    @Test
    void registeredMapIsListedSelectableAndOpens() throws Exception {
        // 14.39:onEnable 顺序 = 落盘世界目录(@TempDir 代落盘)→ 构造 MapDefinition → registerMap
        Files.createDirectories(mapsDir.resolve("their_arena"));
        Files.createDirectories(mapsDir.resolve("their_arena2"));

        var info = harness.api.registerMap(provider, OfflineApi.map(mapsDir.resolve("their_arena"), OfflineApi.TAG));
        harness.api.registerMap(provider, OfflineApi.map(mapsDir.resolve("their_arena2"), OfflineApi.TAG));
        assertEquals("their_arena", info.id(), "成功返回 MapInfo 快照(07 §7.1)");
        assertTrue(harness.api.maps().stream().anyMatch(map -> map.id().equals("their_arena")),
                "api.maps() 列出注册式地图(20-D6)");
        assertTrue(harness.maps.byId("their_arena").isPresent(), "目录 byId 命中(可被切换解析)");

        // 开局:首切换按标签挑图(注册序;空目录 = 注册图即首张)
        Session session = harness.createSession("s1", MODE_ID);
        assertEquals("their_arena", session.currentInstance().orElseThrow().view().map().id(),
                "开局落在注册式地图上(★54「可被 switchMode 选中并开局」)");

        // 指定图切换:注册式地图可被显式选中(20-D4:注册只交声明,世界照走 WorldPort;
        // ★v1.15/33 号:切换经会话句柄)
        CompletableFuture<SwitchResult> future = harness.api.session("s1").orElseThrow()
                .switchMode(new MatchModeSelection(MODE_ID, Optional.of("their_arena2"), true),
                        Handoff.empty());
        harness.ticker.tickAll();
        assertInstanceOf(SwitchResult.Succeeded.class, future.join(), "指定注册图切换成功");
        assertEquals("their_arena2", session.currentInstance().orElseThrow().view().map().id(),
                "实例正用指定注册图");
    }

    // ── ★54 卸载摘除 + 正用拒绝(特性不是障碍,20-D4) ────────────────────

    @Test
    void unregisterRemovesButRefusedWhileInUse() throws Exception {
        Files.createDirectories(mapsDir.resolve("their_arena"));
        harness.api.registerMap(provider, OfflineApi.map(mapsDir.resolve("their_arena"), OfflineApi.TAG));
        Session session = harness.createSession("s1", MODE_ID);   // 正用 their_arena

        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> harness.api.unregisterMap(provider, "their_arena"),
                "有会话正用 → 摘除被拒(20-D4)");
        assertTrue(refused.getMessage().contains("s1"), "detail 点名会话: " + refused.getMessage());
        assertTrue(harness.api.maps().stream().anyMatch(map -> map.id().equals("their_arena")),
                "拒绝后地图照常在目录(★54「摘除被拒且地图照常可用」)");
        assertTrue(session.currentInstance().orElseThrow().view().map().id().equals("their_arena"),
                "正用会话无扰动");

        // 解散后守卫通过 → 摘除成功(幂等语义外的正常路径)
        harness.service.dissolve(new SessionId("s1"), DissolveReason.ADMIN);
        harness.api.unregisterMap(provider, "their_arena");
        assertFalse(harness.api.maps().stream().anyMatch(map -> map.id().equals("their_arena")),
                "摘除后目录移除(★54)");
        assertTrue(harness.maps.byId("their_arena").isEmpty());
    }

    @Test
    void unregisterUnknownIdIsIdempotentNoOp() {
        assertDoesNotThrow(() -> harness.api.unregisterMap(provider, "never_registered"),
                "已不在目录 = no-op(幂等,07 §7.1 javadoc 口径)");
        assertTrue(harness.api.maps().isEmpty());
    }

    // ── ★55 坏声明响亮拒绝:声明路径缺失带绝对路径 / 同一路径重复注册点名先到者 ──

    @Test
    void missingWorldDirIsLoudlyRejectedWithAbsolutePath() throws Exception {
        Files.createDirectories(mapsDir.resolve("their_arena"));
        harness.api.registerMap(provider, OfflineApi.map(mapsDir.resolve("their_arena"), OfflineApi.TAG));

        Path ghostDir = mapsDir.resolve("ghost_map");                       // 未落盘
        var ghost = OfflineApi.map(ghostDir, OfflineApi.TAG);
        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
                () -> harness.api.registerMap(provider, ghost),
                "世界目录缺失 → 响亮抛(20-D3;14.39:注册期校验才有意义)");
        assertTrue(missing.getMessage().contains("ghost_map"), "点名地图 id: " + missing.getMessage());
        assertTrue(missing.getMessage().contains(ghostDir.toAbsolutePath().toString()),
                "带完整绝对路径(★v1.8 detail 口径): " + missing.getMessage());

        // 框架与既有地图照常(★55:拒绝不留半注册态)
        assertTrue(harness.api.maps().stream().anyMatch(map -> map.id().equals("their_arena")));
        assertTrue(harness.api.maps().stream().noneMatch(map -> map.id().equals("ghost_map")));
    }

    @Test
    void duplicateRegistrationOfSamePathNamesFirstArriver() throws Exception {
        Files.createDirectories(mapsDir.resolve("shared"));
        harness.api.registerMap(provider, OfflineApi.map(mapsDir.resolve("shared"), OfflineApi.TAG));

        // 同一路径(规范化后)再注册 → 路径派生 id 同源,必撞查重闸(★v1.8)
        IllegalStateException duplicate = assertThrows(IllegalStateException.class,
                () -> harness.api.registerMap(TestPlugins.named("another-plugin"),
                        OfflineApi.map(mapsDir.resolve("shared"), OfflineApi.TAG)),
                "重复注册同一路径 → 同一闸响亮抛(同一注册通道,20-D3)");
        assertTrue(duplicate.getMessage().contains("先到者")
                && duplicate.getMessage().contains("插件注册"), "点名先到者与其来源: " + duplicate.getMessage());
    }

}
