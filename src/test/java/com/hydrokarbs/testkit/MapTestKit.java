package com.hydrokarbs.testkit;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.hydrokarbs.engine.AttributeBag;
import com.hydrokarbs.engine.port.ResetStrategy;
import com.hydrokarbs.map.MapDefinition;
import com.hydrokarbs.map.MapRegistry;

/**
 * 地图注册造数器(★单注册通道收敛后的 testkit 共享 fixture,2026-10-27;
 * ★v1.8 按新基准改写:声明按路径直交,无模板根——落盘即声明目录本体):
 * 为地图落盘世界目录({@code <parent>/<id>/},14.39「先落盘后注册」的离线代偿)
 * + 构造两参新形状声明(标签经 {@link AttributeBag.Builder} 装入
 * {@link MapDefinition#TAGS} 词料键)+ 以假 provider
 * (身份键)调 {@link MapRegistry#register},与 api 注册通道同一入口。
 *
 * <p>临时目录自理:{@link #newMapsDir()} 建系统临时目录下唯一父目录,进程退出时
 * 统一递归清理;能用 JUnit {@code @TempDir} 的调用点优先传 {@code @TempDir} 下目录
 * (框架自身的 per-test 清理)。离线:零 Bukkit import,provider 身份键用普通对象
 * (身份比较,7.2 要点 4 同款;生产 = org.bukkit.plugin.Plugin)。
 */
public final class MapTestKit {

    /** 本类造出的全部临时父目录(进程退出时统一递归清理)。 */
    private static final List<Path> ROOTS = new ArrayList<>();

    /** 便捷版假 provider(身份键;身份比较语义,toString 可读便于诊断)。 */
    private static final Object PROVIDER = new Object() {
        @Override
        public String toString() {
            return "map-testkit-provider";
        }
    };

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            for (Path root : ROOTS) {
                deleteRecursively(root);
            }
        }, "map-testkit-temp-cleanup"));
    }

    private MapTestKit() {
    }

    /**
     * 自理临时父目录(系统临时目录下唯一子目录;声明目录落盘的挂点)。
     * 无 JUnit {@code @TempDir} 可用的装配点(SwitchHarness 等自足基座)用本口。
     */
    public static Path newMapsDir() {
        try {
            Path root = Files.createTempDirectory("hydrokarbs-map-declarations");
            synchronized (ROOTS) {
                ROOTS.add(root);
            }
            return root;
        } catch (IOException e) {
            throw new UncheckedIOException("临时地图目录创建失败", e);
        }
    }

    /** 声明构造(目录不必存在——存在性是注册期/获取期校验的事):标签进 TAGS 词料键。 */
    public static MapDefinition declaration(Path worldDirectoryPath, Set<String> tags) {
        return new MapDefinition(worldDirectoryPath, tagsMetadata(tags));
    }

    /** 声明构造(带副本处置策略):标签进 TAGS 词料键,策略走三参构造。 */
    public static MapDefinition declaration(Path worldDirectoryPath, Set<String> tags,
                                            ResetStrategy resetStrategy) {
        return new MapDefinition(worldDirectoryPath, resetStrategy, tagsMetadata(tags));
    }

    /** 标签词料装配:空集用共享空实例,非空经 Builder 一次冻结(无词料 = 不被标签选中)。 */
    private static AttributeBag tagsMetadata(Set<String> tags) {
        return tags.isEmpty()
                ? AttributeBag.empty()
                : new AttributeBag.Builder().put(MapDefinition.TAGS, tags).build();
    }

    /**
     * 落盘 {@code <parent>/<id>} 世界目录并以 {@code provider} 身份注册
     * (id = 声明目录名,路径派生;返回注册的同一声明实例)。
     *
     * @param registry 目标目录;parent 落盘父目录(@TempDir 或自理临时目录);
     *                  provider 身份键(任意对象);id 地图 id(即目录名);
     *                  tags 标签集({@link MapDefinition#TAGS} 词料键,空集合法)
     */
    public static MapDefinition registered(MapRegistry registry, Object provider, Path parent,
                                           String id, Set<String> tags) {
        Path dir = parent.resolve(id);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException("世界目录落盘失败: " + dir, e);
        }
        return registry.register(provider, declaration(dir, tags));
    }

    /** 全声明便捷版(身份键 = 本类专用 PROVIDER;世界目录落盘自理)。 */
    public static MapDefinition registered(MapRegistry registry, Path parent,
                                           String id, Set<String> tags) {
        return registered(registry, PROVIDER, parent, id, tags);
    }

    /** varargs 标签便捷版(Set.of 包装后委托 Set 重载)。 */
    public static MapDefinition registered(MapRegistry registry, Path parent,
                                           String id, String... tags) {
        return registered(registry, parent, id, Set.of(tags));
    }

    /** varargs 标签 + 指定 provider 便捷版(Set.of 包装后委托五参重载)。 */
    public static MapDefinition registered(MapRegistry registry, Object provider, Path parent,
                                           String id, String... tags) {
        return registered(registry, provider, parent, id, Set.of(tags));
    }

    /** 已备好世界目录与声明的直录口(调用方自理落盘与 metadata 构造)。 */
    public static MapDefinition registered(MapRegistry registry, Object provider,
                                           MapDefinition map) {
        return registry.register(provider, map);
    }

    /** 已备好声明的直录便捷版(身份键 = 本类专用 PROVIDER)。 */
    public static MapDefinition registered(MapRegistry registry, MapDefinition map) {
        return registered(registry, PROVIDER, map);
    }

    /** 递归删除(进程退出清理专用;目录树浅,不追求原子性)。 */
    private static void deleteRecursively(Path root) {
        try (var paths = Files.walk(root)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException ignored) {
                    // 退出清理尽力而为:删除失败不阻断其余根的清理
                }
            });
        } catch (IOException ignored) {
            // 同上:退出清理尽力而为
        }
    }
}
