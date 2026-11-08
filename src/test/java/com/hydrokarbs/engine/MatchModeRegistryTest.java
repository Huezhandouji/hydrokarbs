package com.hydrokarbs.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hydrokarbs.engine.AttributeBag;
import com.hydrokarbs.engine.component.ComponentDependencyException;
import com.hydrokarbs.engine.component.MatchModeComponent;
import com.hydrokarbs.engine.mode.MatchMode;
import com.hydrokarbs.engine.mode.MatchModeMetadata;
import com.hydrokarbs.engine.mode.MatchModeProvider;
import com.hydrokarbs.engine.mode.MatchModeRegistry;
import com.hydrokarbs.engine.port.MatchModeInstanceServices;
import com.hydrokarbs.engine.session.MatchModeConfig;
import com.hydrokarbs.map.MapDefinition;
import com.hydrokarbs.testkit.MapTestKit;
import com.hydrokarbs.testkit.TestComponent;

/**
 * 模式注册表注册闸(施工计划 2.2「测试与验收」;§12.2/§2.6):
 * 假工厂经 register 进目录并可 require;同 id 重复 → 响亮 ISE;
 * 依赖校验失败 → 跳过 + SEVERE、其余照常;forMap 按 matchesTags 谓词
 * (模式侧空 mapTags = 通配,31 号决策 B);未知 id require 抛。
 * discover() 的 ServiceLoader 半边以「恰发现出厂空模式工厂」实代测
 * (31 号:SPI 清单首行 = EmptyMatchModeFactory,离线零 Bukkit)。
 * 离线:零 Bukkit import/静态调用。
 */
class MatchModeRegistryTest {

    private final List<LogRecord> logs = new ArrayList<>();
    private final List<String> hookLog = new ArrayList<>();
    private MatchModeRegistry registry;

    @BeforeEach
    void setUp() {
        Logger logger = Logger.getLogger(MatchModeRegistryTest.class.getName() + "-"
                + System.nanoTime());
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                logs.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        // 配置源:全回落缺省(装载链已由 ConfigRepositoryTest 覆盖,此处只测注册闸)
        MatchModeConfig fallback = new MatchModeConfig() {
            @Override public int getInt(String path, int def) { return def; }
            @Override public String getString(String path, String def) { return def; }
            @Override public List<String> getStringList(String path, List<String> def) { return def; }
            @Override public List<String> keys(String section) { return List.of(); }
        };
        registry = new MatchModeRegistry(id -> fallback, logger);
    }

    // ── 假工厂 ───────────────────────────────────────────────────

    /** 依赖自满足的假工厂:组件表全部声明无依赖的假组件。 */
    private static MatchModeProvider provider(String id, Set<String> mapTags,
                                              MatchModeComponent.Specification<?>... specs) {
        return new MatchModeProvider() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public MatchMode build(MatchModeConfig cfg) {
                MatchMode.Builder builder = new MatchMode.Builder(id)
                        .mapTags(mapTags)
                        .metadata(MatchModeMetadata.addTo(new AttributeBag.Builder(), false, 0, 100).build());
                int index = 1;
                for (MatchModeComponent.Specification<?> spec : specs) {
                    builder.addComponent("c" + index++, spec);
                }
                return builder.build();
            }
        };
    }

    /** 空 mapTags 的假工厂(通配断言用,31 号决策 B)。 */
    private static MatchModeProvider taglessProvider(String id) {
        return provider(id, Set.of());
    }

    /** 无人提供的组件类型(制造「依赖校验失败」的坏模板)。 */
    private abstract static class Unresolved extends MatchModeComponent {

        Unresolved(String id, MatchModeInstanceServices svc) {
            super(id, svc);
        }
    }

    private boolean hasSevere() {
        return logs.stream().anyMatch(r -> r.getLevel() == Level.SEVERE);
    }

    // ── 用例 ─────────────────────────────────────────────────────

    @Test
    void registeredProviderIsRetrievableByRequire() {
        registry.register(provider("alpha", Set.of("standard"), TestComponent.spec(hookLog)));
        MatchMode mode = registry.require("alpha");
        assertEquals("alpha", mode.id());
        assertSame(mode, registry.all().iterator().next());
    }

    @Test
    void duplicateIdFailsLoudlyAndKeepsFirstArrival() {
        registry.register(provider("alpha", Set.of("a")));
        assertThrows(IllegalStateException.class,
                () -> registry.register(provider("alpha", Set.of("b"))));
        assertEquals(1, registry.all().size());
        assertEquals(Set.of("a"), registry.require("alpha").mapTags(), "先到者保留,不静默覆盖");
    }

    @Test
    void dependencyFailureIsSkippedWithSevereWhileOthersRegister() {
        registry.register(provider("bad", Set.of("x"),
                TestComponent.requiring(hookLog, Unresolved.class)));   // 无人提供 → 坏模板
        registry.register(provider("good", Set.of("x"), TestComponent.spec(hookLog)));

        assertThrows(NoSuchElementException.class, () -> registry.require("bad"));
        assertTrue(hasSevere(), "依赖校验失败必须 SEVERE(§2.6)");
        assertTrue(logs.stream().anyMatch(r -> r.getLevel() == Level.SEVERE
                && String.valueOf(r.getMessage()).contains("bad")), "SEVERE 点名坏模式 id");
        assertEquals(1, registry.all().size());
        assertEquals("good", registry.require("good").id());
    }

    @Test
    void dependencyExceptionTypeIsComponentDependencyException() {
        MatchMode bad = new MatchMode.Builder("probe")
                .metadata(MatchModeMetadata.addTo(new AttributeBag.Builder(), false, 0, 100).build())
                .addComponent("needs", TestComponent.requiring(hookLog, Unresolved.class))
                .build();
        assertThrows(ComponentDependencyException.class, bad::verifyDependencies);
    }

    @Test
    void requireUnknownIdThrowsLoudly() {
        NoSuchElementException e = assertThrows(NoSuchElementException.class,
                () -> registry.require("ghost"));
        assertTrue(e.getMessage().contains("ghost"));
    }

    @Test
    void forMapFiltersByTagIntersection() {
        registry.register(provider("m-std", Set.of("standard")));
        registry.register(provider("m-wide", Set.of("standard", "large")));
        registry.register(provider("m-coop", Set.of("cooperative")));
        registry.register(taglessProvider("m-any"));      // 空 mapTags = 通配(31 号决策 B)

        // 声明侧标签走 TAGS 词料键(★v1.8);forMap 不触文件系统,路径占位即可
        MapDefinition standardMap = MapTestKit.declaration(Path.of("map1"),
                Set.of("standard", "arena"));
        List<String> matched = registry.forMap(standardMap).stream().map(MatchMode::id).toList();
        assertEquals(List.of("m-std", "m-wide", "m-any"), matched,
                "交集非空匹配 + 空 mapTags 模式通配任意图");

        // 通配不对称:模式侧空 = 匹配任意图(含空标签图);地图侧空不构成通配
        // ——非空 mapTags 模式对空标签图恒不匹配(31 号决策 B)
        MapDefinition emptyTags = MapTestKit.declaration(Path.of("map2"), Set.of());
        List<String> matchedEmpty = registry.forMap(emptyTags).stream().map(MatchMode::id).toList();
        assertEquals(List.of("m-any"), matchedEmpty,
                "空标签图只被通配(空 mapTags)模式匹配,合法不抛");
        assertTrue(MatchModeRegistry.matchesTags(registry.require("m-any"), emptyTags),
                "matchesTags 谓词直测:模式侧空 mapTags 对空标签图 = true");
        assertFalse(MatchModeRegistry.matchesTags(registry.require("m-std"), emptyTags),
                "matchesTags 谓词直测:地图侧空 tags 不构成通配");
    }

    @Test
    void zeroModesIsLegalState() {
        assertTrue(registry.all().isEmpty(), "0 个模式注册属合法状态(不打错误)");
        assertFalse(hasSevere());
    }

    @Test
    void discoverFindsExactlyTheFactoryShippedMode() {
        // 31 号:SPI 清单首行 = 出厂空模式工厂——discover 恰发现它并过注册闸
        //(原「空清单零发现」前提被出厂模式打破,重写并随 31 号登记修测理由)
        registry.discover();
        assertEquals(1, registry.all().size(), "测试类路径恰发现 1 个出厂模式");
        assertEquals("limbo", registry.require("limbo").id());
        assertTrue(registry.require("limbo").components().isEmpty(), "出厂空模式零组件");
        assertNotNull(logs.stream()
                .filter(r -> r.getLevel() == Level.INFO)
                .map(LogRecord::getMessage)
                .filter(m -> String.valueOf(m).contains("Mode registry initialized"))
                .findFirst()
                .orElse(null), "注册表初始化行必须出现");
    }

    /** discover() 的 ServiceLoader 半边实代测(31 号重写):SPI 清单恰一行 = 出厂工厂 FQCN。 */
    @Test
    void spiManifestListsExactlyTheFactoryShippedProvider() throws IOException {
        Path spi = Path.of("src/main/resources/META-INF/services",
                "com.hydrokarbs.engine.mode.MatchModeProvider");
        assertTrue(Files.isRegularFile(spi), "SPI 资源文件必须存在(§12.1/§13.1-#7): " + spi);
        List<String> entries = Files.readAllLines(spi, StandardCharsets.UTF_8).stream()
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .toList();
        assertEquals(List.of("com.hydrokarbs.mode.empty.EmptyMatchModeFactory"), entries,
                "SPI 清单恰一行 = 出厂空模式工厂(31 号;原「清单为空」前提被出厂模式打破)");
    }
}
