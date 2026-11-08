package com.hydrokarbs.map;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 地图目录:全部已注册 {@link MapDefinition} 的查询面(§15.2 热重载纪律)。
 * 单注册通道:地图只能经 {@link #register} 注册,声明所有权在提供方
 * (20-D1「交成品不交工厂」/D6);校验直接验声明自带 {@code worldDirectoryPath},
 * 本类不持任何文件系统根(★v1.8/26 号)。
 *
 * <p><b>volatile 原子替换</b>:写操作一次性换新不可变快照,读方永不读到半截;
 * <b>目录顺序 = 注册序</b>(自动挑图取序首因此可预期,{@code api.maps()} 同序)。
 *
 * <p><b>注册期校验</b>(响亮抛):① id(= 目录名)查重,撞名抛并点名先到者
 * ——先到者保留;② 世界目录须存在且为目录(14.39:先落盘目录再 registerMap)。
 * <b>摘除</b>:不在目录 = 幂等 no-op;「正用摘除被拒」守卫在调用方(本类是纯
 * 目录,20-D4)。注册/摘除主线程;本类只保证引用可见性(volatile)。
 */
public final class MapRegistry {

    /** 注册条目:声明 + 提供方身份键(生产 = org.bukkit.plugin.Plugin;身份比较,7.2 要点 4 同款)。 */
    private record Registered(MapDefinition map, Object provider) {
    }

    /** 不可变快照(一次写操作的完整结果):id 索引 + 展示序(= 注册序)+ 注册条目。 */
    private record Snapshot(Map<String, MapDefinition> byId, List<MapDefinition> order,
                            List<Registered> registered) {

        static final Snapshot EMPTY = new Snapshot(Map.of(), List.of(), List.of());

        /** 组装:order = 注册序,byId 与之同源。 */
        static Snapshot of(List<Registered> registered) {
            List<MapDefinition> order = new ArrayList<>();
            for (Registered entry : registered) {
                order.add(entry.map());
            }
            Map<String, MapDefinition> index = new LinkedHashMap<>();
            for (MapDefinition map : order) {
                index.put(map.id(), map);
            }
            return new Snapshot(Map.copyOf(index), List.copyOf(order), List.copyOf(registered));
        }
    }

    private volatile Snapshot snapshot = Snapshot.EMPTY;

    // ── 注册通道(MiniGameApi#registerMap 的委托目标) ────────────────────

    /**
     * 注册一张地图(注册期校验见类注;重复 id 响亮抛点名先到者)。主线程调用。
     *
     * @param provider 提供方身份键(生产 = org.bukkit.plugin.Plugin;身份比较)
     * @param map      建好的地图声明(纯静态值,20-D1「交成品不交工厂」)
     * @throws IllegalStateException    id 撞名(点名先到者)
     * @throws IllegalArgumentException 世界目录不存在或不是目录
     */
    public MapDefinition register(Object provider, MapDefinition map) {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(map, "map");
        Path worldDir = map.worldDirectoryPath();
        if (!Files.isDirectory(worldDir)) {
            throw new IllegalArgumentException("地图 " + map.id() + " 的世界目录不存在或不是目录: "
                    + worldDir + "(14.39:注册前必须先落盘世界目录——onEnable 顺序 = "
                    + "落盘 → 构造 MapDefinition → registerMap)");
        }
        Snapshot current = snapshot;
        if (current.byId().containsKey(map.id())) {
            throw new IllegalStateException("重复的地图 id: " + map.id() + "(先到者: "
                    + describeSource(current, map.id()) + ";后来者: 本次注册)"
                    + "——同一注册通道,先到者保留(20-D3)");
        }
        List<Registered> registered = new ArrayList<>(current.registered());
        registered.add(new Registered(map, provider));
        snapshot = Snapshot.of(List.copyOf(registered));
        return map;
    }

    /**
     * 摘除一张地图(20-D4):已不在目录 → 幂等 no-op 返回 false。「有会话正用 →
     * 摘除被拒」守卫在调用方(本类是纯目录,见类注)。主线程调用。
     *
     * @return true = 已摘除;false = 目录中无此 id(幂等 no-op)
     */
    public boolean unregister(Object provider, String mapId) {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(mapId, "mapId");
        Snapshot current = snapshot;
        if (!current.byId().containsKey(mapId)) {
            return false;                               // 幂等:已不在目录 = no-op(07 §7.1 javadoc 口径)
        }
        List<Registered> remaining = current.registered().stream()
                .filter(entry -> !entry.map().id().equals(mapId))
                .toList();
        snapshot = Snapshot.of(remaining);
        return true;
    }

    /** 指定提供方注册的全部地图 id(按提供方兜底摘除的查询口;身份比较,7.2 要点 4)。 */
    public Set<String> registeredIdsOf(Object provider) {
        Objects.requireNonNull(provider, "provider");
        Set<String> ids = new LinkedHashSet<>();
        for (Registered entry : snapshot.registered()) {
            if (entry.provider() == provider) {
                ids.add(entry.map().id());
            }
        }
        return Set.copyOf(ids);
    }

    /** 先到者来源描述(重复 id 响亮点名;带提供方类名,20-D3)。 */
    private static String describeSource(Snapshot current, String mapId) {
        for (Registered entry : current.registered()) {
            if (entry.map().id().equals(mapId)) {
                return "插件注册(" + entry.provider() + ")";
            }
        }
        throw new IllegalStateException("目录内部不一致: id " + mapId + " 在 id 索引中但无注册条目");
    }

    // ── 查询面 ────────────────────────────────────────────────

    /** 按 id 查地图;无此 id 返回 empty。 */
    public Optional<MapDefinition> byId(String mapId) {
        return Optional.ofNullable(snapshot.byId.get(mapId));
    }

    /** 按 tag 过滤(注册序;标签取声明 {@link MapDefinition#tags()} 唯一读口);无匹配返回空列表。 */
    public List<MapDefinition> byTag(String tag) {
        List<MapDefinition> matched = new ArrayList<>();
        for (MapDefinition map : snapshot.order) {
            if (map.tags().contains(tag)) {
                matched.add(map);
            }
        }
        return List.copyOf(matched);
    }

    /** 全部地图(注册序,不可变)。 */
    public List<MapDefinition> all() {
        return snapshot.order;
    }
}
