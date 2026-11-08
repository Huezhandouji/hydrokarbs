package com.hydrokarbs.engine;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 声明词料容器:纯值容器,零 Bukkit 依赖(§2.7「状态唯一」)。持有者两处——地图声明
 * {@code MapDefinition#metadata()} 与模式声明 {@code MatchMode#meta()}(模式元数据
 * 约定键与中性缺省见 {@code MatchModeMetadata})。
 *
 * <p>声明级单写方、构造即冻结——{@link Builder} 是唯一写口,写只发生在声明装配期;
 * {@link #empty()} 返回共享空实例;构造后 {@code values} 经 {@link Map#copyOf}
 * 结构不可变,无任何公开写口。
 *
 * <p>只承载声明的静态词料:<b>无命名空间断言、无组件授权表、不参与实例
 * {@code close()} 链</b>(与旧运行期属性袋仅沿用 FQCN,机制互不共享)。
 *
 * <p><b>读口语义</b>:{@link #get} 键不存在或值类型不符返回 {@link Optional#empty()};
 * {@link #getOrDefault} 同情形返回 fallback;{@link #has} 只问键是否存在,不论值类型。
 */
public final class AttributeBag {

    /** 词料键:名字 + 值类型(类型是读口的运行期校验依据;无命名空间分量)。 */
    public record Key<T>(String name, Class<T> type) {

        public Key {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(type, "type");
        }

        public static <T> Key<T> of(String name, Class<T> type) {
            return new Key<>(name, type);
        }
    }

    /** 共享空实例(无词料;不可变,可按引用复用)。 */
    private static final AttributeBag EMPTY = new AttributeBag(Map.of());

    private final Map<Key<?>, Object> values;

    /** 私有构造:入表即拷贝为不可变映射,外部无法再改词料集合。 */
    private AttributeBag(Map<Key<?>, Object> values) {
        this.values = Map.copyOf(values);
    }

    /** 无词料声明使用的共享空实例。 */
    public static AttributeBag empty() {
        return EMPTY;
    }

    // ── 读口(三件;无任何公开写口) ──────────────────────────────

    /**
     * 取值:键不存在或值类型与键声明不符({@code key.type().isInstance(value)} 为
     * {@code false})返回 {@link Optional#empty()}。
     */
    public <T> Optional<T> get(Key<T> key) {
        Objects.requireNonNull(key, "key");
        Object value = values.get(key);
        if (!key.type().isInstance(value)) {
            return Optional.empty();
        }
        return Optional.of(key.type().cast(value));
    }

    /** 取值:键不存在或值类型不符返回 {@code fallback}。 */
    public <T> T getOrDefault(Key<T> key, T fallback) {
        return get(key).orElse(fallback);
    }

    /** 是否存在该键——不论取值类型(键身份 = 名字 + 类型,见 {@link Key})。 */
    public boolean has(Key<?> key) {
        Objects.requireNonNull(key, "key");
        return values.containsKey(key);
    }

    // ── 构造写口(唯一;声明装配期使用) ──────────────────────────

    /** 词料装配器:构造期逐项覆盖式入表,{@link #build()} 一次冻结。 */
    public static final class Builder {

        private final Map<Key<?>, Object> values = new HashMap<>();

        /** 覆盖式入表(同键后写胜);键与值均不允许 {@code null}。 */
        public <T> Builder put(Key<T> key, T value) {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(value, "value");
            values.put(key, value);
            return this;
        }

        /** 空表返回共享 {@link AttributeBag#empty()},否则以当前表构造新实例(入表即拷贝)。 */
        public AttributeBag build() {
            return values.isEmpty() ? empty() : new AttributeBag(values);
        }
    }
}
