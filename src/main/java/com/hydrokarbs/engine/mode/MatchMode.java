package com.hydrokarbs.engine.mode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;

import com.hydrokarbs.engine.AttributeBag;
import com.hydrokarbs.engine.component.ComponentDependencyException;
import com.hydrokarbs.engine.component.MatchModeComponent;
import com.hydrokarbs.engine.instance.MatchModeInstance;
import com.hydrokarbs.engine.port.EngineServices;
import com.hydrokarbs.engine.port.WorldHandle;
import com.hydrokarbs.engine.session.Handoff;
import com.hydrokarbs.engine.session.MatchModeConfig;
import com.hydrokarbs.engine.session.Session;
import com.hydrokarbs.map.MapDefinition;

/**
 * 模式聚合根:纯数据聚合 + 唯一有序组件描述符表(02-核心概念 §2.3)。
 *
 * <p>全类零行为分支:仅有的两个方法都是装配期职责({@link #verifyDependencies}
 * 与 {@link #createInstance});行为归组件,不改公开方法集(§2.8-决断二;★22 冻结为 9 个)。
 *
 * <p>新增一个模式 = 用 Builder 拼组件表;模板进程级单例,build 后不可追加组件(A9)。
 */
public final class MatchMode {

    private final String id;
    private final Component displayName;
    private final List<Component> description;
    private final Material icon;                 // 入口牌子/GUI 用;未设置时为 null(消费方自理)
    private final Set<String> mapTags;           // 支持哪些地图(按 MapDefinition.tags 匹配)
    private final AttributeBag meta;             // 元数据 = AttributeBag 词料容器,约定键见 MatchModeMetadata,缺席 = 中性缺省;人数窗读取经 MatchModeMetadata 帮助方法(入场裁决已归组件)

    /** 唯一有序组件描述符表:LinkedHashMap,声明序 = 装配调用序 = 生命周期/事件派发序。 */
    private final Map<String, ComponentEntry> components;

    private MatchMode(Builder builder) {
        this.id = builder.id;
        this.displayName = builder.displayName;
        this.description = builder.description;
        this.icon = builder.icon;
        this.mapTags = builder.mapTags;
        this.meta = builder.metadata;
        this.components = Collections.unmodifiableMap(new LinkedHashMap<>(builder.components));
    }

    // ── 仅有的两个方法(都是装配期职责) ─────────────────────────────

    /**
     * 装配期依赖检查:缺必需依赖抛 {@link ComponentDependencyException}(算法见 §2.6)。
     * 注册前闸与切换准备阶段调用;在这里失败,旧实例毫发无损。
     *
     * <p>要点:自己不算自己的提供者;可选依赖缺失不报错;环不检查
     * (允许,环内先后未定义,不得依赖);只看描述符类型,不需要实例。
     */
    public void verifyDependencies() {
        for (Map.Entry<String, ComponentEntry> entry : components.entrySet()) {
            List<String> missing = missingRequiredDependencies(entry.getKey());
            if (!missing.isEmpty()) {
                throw new ComponentDependencyException(
                        "模式 " + id + " 的组件 " + entry.getKey() + " 缺少必需依赖: " + String.join(", ", missing));
            }
        }
    }

    /**
     * 造实例的唯一入口:先 {@link #verifyDependencies} 再造——任何路径不可能绕过检查。
     * 容器(1.3)按描述符表造组件并构造期注入服务集。
     */
    public MatchModeInstance createInstance(Session session, MapDefinition map, MatchModeConfig cfg,
                                            WorldHandle world, Handoff handoff, EngineServices engine) {
        verifyDependencies();
        return new MatchModeInstance(this, session, map, cfg, world, handoff, engine);
    }

    // ── 只读访问器(短名,非 JavaBean 的 getXxx;★22 方法集按这些名字冻结) ──

    public String id() {
        return id;
    }

    public Component displayName() {
        return displayName;
    }

    public List<Component> description() {
        return description;
    }

    public Material icon() {
        return icon;
    }

    public Set<String> mapTags() {
        return mapTags;
    }

    public AttributeBag meta() {
        return meta;
    }

    public Map<String, ComponentEntry> components() {
        return components;
    }

    // ── 诊断(★22 收口:一律 private,公开面不进诊断方法) ─────────────

    /** 指定组件缺失的必需依赖类型名(可选项缺失不计;自己不算自己的提供者)。 */
    private List<String> missingRequiredDependencies(String componentId) {
        ComponentEntry self = components.get(componentId);
        List<String> missing = new ArrayList<>();
        for (Class<? extends MatchModeComponent> required : self.requiredTypes()) {
            if (!hasProviderOtherThan(componentId, required)) {
                missing.add(required.getName());
            }
        }
        return missing;
    }

    /** 是否存在 id ≠ selfId、提供类型可赋给 type 的组件(自己不算自己的提供者)。 */
    private boolean hasProviderOtherThan(String selfId, Class<? extends MatchModeComponent> type) {
        for (Map.Entry<String, ComponentEntry> other : components.entrySet()) {
            if (other.getKey().equals(selfId)) {
                continue;
            }
            if (type.isAssignableFrom(other.getValue().providedType())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 装配条目:(工厂, 描述符类型, 提供类型, 必需依赖, 可选依赖, 地图适配检查) 的不可变快照。
     * 地图适配检查以 {@code spec::checkMap} 方法引用捕获(freeze 后声明面已拒改),
     * 切换准备 A3 逐条消费(§3.2-A3:任何非空返回 → 切换被拒)。
     */
    public record ComponentEntry(
            MatchModeComponent.Factory<? extends MatchModeComponent> factory,
            Class<?> descriptorType,
            Class<? extends MatchModeComponent> providedType,
            List<Class<? extends MatchModeComponent>> requiredTypes,
            List<Class<? extends MatchModeComponent>> optionalTypes,
            BiFunction<MapDefinition, MatchModeConfig, List<String>> mapCheck) {
    }

    /** 聚合根 Builder:唯一装配入口(声明序 = 入表序)。 */
    public static final class Builder {

        private final String id;
        private Component displayName;
        private List<Component> description;
        private Material icon;
        private Set<String> mapTags = Set.of();
        private AttributeBag metadata;
        private final Map<String, ComponentEntry> components = new LinkedHashMap<>();

        public Builder(String id) {
            if (id == null || id.trim().isEmpty()) {
                throw new IllegalArgumentException("MatchMode id cannot be null or empty.");
            }
            this.id = id;
        }

        public Builder displayName(Component displayName) {
            this.displayName = java.util.Objects.requireNonNull(displayName, "displayName");
            return this;
        }

        public Builder description(List<Component> lines) {
            this.description = List.copyOf(java.util.Objects.requireNonNull(lines, "description"));
            return this;
        }

        public Builder icon(Material icon) {
            this.icon = java.util.Objects.requireNonNull(icon, "icon");
            return this;
        }

        /** 支持哪些地图(按 MapDefinition.tags 匹配);空集 = 通配任意地图(31 号决策 B,
         *  仍受会话地图池过滤;地图侧 tags 为空不构成通配)。 */
        public Builder mapTags(Set<String> tags) {
            this.mapTags = Set.copyOf(java.util.Objects.requireNonNull(tags, "mapTags"));
            return this;
        }

        public Builder metadata(AttributeBag metadata) {
            this.metadata = java.util.Objects.requireNonNull(metadata, "metadata");
            return this;
        }

        /**
         * 统一装配入口:绑 id → {@code freeze()} 取快照 → 入表。
         * id 跨类型查重(同一命名空间,重复即抛 {@link IllegalArgumentException})。
         */
        public Builder addComponent(String id, MatchModeComponent.Specification<?> spec) {
            if (id == null || id.trim().isEmpty()) {
                throw new IllegalArgumentException("Component id cannot be null or empty.");
            }
            java.util.Objects.requireNonNull(spec, "spec");
            if (components.containsKey(id)) {
                throw new IllegalArgumentException("重复的组件 id: " + id + "(模式 " + this.id + ")");
            }
            spec.bindId(id);
            MatchModeComponent.Specification.Snapshot snapshot = spec.freeze();
            components.put(id, new ComponentEntry(
                    snapshot.factory(),
                    spec.getClass(),
                    snapshot.providedType(),
                    snapshot.requiredTypes(),
                    snapshot.optionalTypes(),
                    spec::checkMap));
            return this;
        }

        /** 产出不可变聚合根。兜底:displayName 缺省 = id;description 缺省 = 占位文案;元数据缺省 = {@link AttributeBag#empty()}(缺席键 = 中性缺省)。 */
        public MatchMode build() {
            if (metadata == null) {
                metadata = AttributeBag.empty();
            }
            if (displayName == null) {
                displayName = Component.text(id);
            }
            if (description == null) {
                description = List.of(Component.text("(暂无描述)"));
            }
            return new MatchMode(this);
        }
    }
}
