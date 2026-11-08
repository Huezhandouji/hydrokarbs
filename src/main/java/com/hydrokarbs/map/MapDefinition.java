package com.hydrokarbs.map;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;

import com.hydrokarbs.engine.AttributeBag;
import com.hydrokarbs.engine.port.ResetStrategy;

/**
 * 地图声明:不可变 record,三样纯静态值——世界目录路径 + 副本处置策略 + 词料
 * 容器({@link AttributeBag metadata},必须非空,无词料传 {@link AttributeBag#empty()})。
 * id 由声明目录名派生(目录查重/副本命名/钉图键的唯一性同一来源,★v1.8/26 号);
 * 标签是词料中的 {@link #TAGS} 项,经 {@link #tags()} 读取(全框架唯一标签读口;
 * 缺省空集 = 不被任何模式按标签选中,20-D3)。
 *
 * <p><b>路径即权威</b>:入参 {@code toAbsolutePath().normalize()} 规范化为绝对
 * 路径,构造后不再重解析;缺目录名(id 无从派生)构造期响亮抛;世界目录
 * 存在性/查重在注册口把关(MapRegistry)。
 *
 * <p>冻结由容器不可变性结构保证(§2.7;词料只在装配期经 Builder 写入一次);
 * 零 Bukkit 依赖,World/Location 换算推迟到消费点。
 */
public record MapDefinition(Path worldDirectoryPath, ResetStrategy resetStrategy, AttributeBag metadata) {

    /** 标签词料键:名字 {@code tags},值类型 {@code Set<String>}(唯一标签读口 {@link #tags()} 的取数键)。 */
    public static final AttributeBag.Key<Set<String>> TAGS =
            AttributeBag.Key.of("tags", stringSetType());

    public MapDefinition {
        Objects.requireNonNull(worldDirectoryPath, "worldDirectoryPath");
        Objects.requireNonNull(resetStrategy, "resetStrategy");
        Objects.requireNonNull(metadata, "metadata");
        worldDirectoryPath = worldDirectoryPath.toAbsolutePath().normalize();
        Path fileName = worldDirectoryPath.getFileName();
        if (fileName == null || fileName.toString().isBlank()) {
            throw new IllegalArgumentException("地图声明路径缺目录名(id 派生依据): "
                    + worldDirectoryPath);
        }
    }

    /**
     * 受检擦除转换:{@code Set.class} 是标签集的运行期类型;{@code Set<String>} 的
     * 元素类型被擦除,读口只校验到 {@code Set} 层(词料不承载元素类型承诺)。
     */
    @SuppressWarnings("unchecked")
    private static Class<Set<String>> stringSetType() {
        return (Class<Set<String>>) (Class<?>) Set.class;
    }

    /**
     * 二参便利构造:{@code resetStrategy} 缺省 {@link ResetStrategy#REUSE_DIRTY}
     * (未声明即复用,缺省语义维持现行为)。第二参是 {@code metadata} 词料容器,
     * 不是策略——要声明处置策略请用三参构造。
     */
    public MapDefinition(Path worldDirectoryPath, AttributeBag metadata) {
        this(worldDirectoryPath, ResetStrategy.REUSE_DIRTY, metadata);
    }

    /** 地图 id = 声明目录名(路径派生;目录查重/副本命名/钉图键同一来源)。 */
    public String id() {
        return worldDirectoryPath.getFileName().toString();
    }

    /**
     * 标签集(全框架唯一标签读口):{@link #TAGS} 缺失或值不是 {@code Set} 实例
     * 时返回空集——空集 = 不被任何模式按标签选中(20-D3 口径)。
     */
    public Set<String> tags() {
        return metadata.getOrDefault(TAGS, Set.of());
    }
}
