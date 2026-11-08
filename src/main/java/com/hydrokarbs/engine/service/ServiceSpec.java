package com.hydrokarbs.engine.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * 进程级服务的声明式描述符(18 号修正案 D3/§3):一个 id、一个主类型、一个工厂、
 * 一组依赖声明;由 L0 现场构建、只被一个注册表消费(D4 单类型单服务,需要两个
 * 接口面就做门面接口)。与组件 Spec 的刻意差异(D3):无 freeze();工厂收
 * {@link ServiceEnv} 而非 svc 服务集,依赖创建期注入(拓扑序保证就绪)。
 *
 * <p>语义口径:{@code requires} 缺席 → seal 抛 {@link ServiceDependencyException}
 * 点名双方;{@code requiresOptional} 缺席 → 不报错,运行期 {@code depOpt} 自查。
 */
public final class ServiceSpec<S> {

    private final String id;
    private final Class<S> type;
    private final Function<ServiceEnv, S> factory;
    private final List<Class<?>> required;
    private final List<Class<?>> optional;

    private ServiceSpec(Builder<S> builder) {
        this.id = builder.id;
        this.type = builder.type;
        this.factory = builder.factory;
        this.required = List.copyOf(builder.required);
        this.optional = List.copyOf(builder.optional);
    }

    /**
     * 起一个声明的 Builder:id/类型/工厂在创建期钉定,依赖声明链式追加。
     * 构建期校验(id 非空等)落在 {@link Builder#build()}。
     */
    public static <S> Builder<S> of(String id, Class<S> type, Function<ServiceEnv, S> factory) {
        return new Builder<>(id, type, factory);
    }

    /** 服务 id(进程内唯一;注册表按它查重点名)。 */
    public String id() {
        return id;
    }

    /** 服务主类型(单类型单服务;注册表按它注入与查重)。 */
    public Class<S> type() {
        return type;
    }

    /** 工厂:收 {@link ServiceEnv},返回服务实例;在 seal 期按拓扑序被调用。 */
    public Function<ServiceEnv, S> factory() {
        return factory;
    }

    /** 必需依赖类型(声明序);缺席 → seal 抛,点名双方。 */
    public List<Class<?>> required() {
        return required;
    }

    /** 可选依赖类型(声明序);缺席不报错,运行期 {@code depOpt} 自查。 */
    public List<Class<?>> optional() {
        return optional;
    }

    /** 声明构建器:{@code ServiceSpec.of(...).requires(X.class).build()}。 */
    public static final class Builder<S> {

        private final String id;
        private final Class<S> type;
        private final Function<ServiceEnv, S> factory;
        private final Set<Class<?>> required = new LinkedHashSet<>();
        private final Set<Class<?>> optional = new LinkedHashSet<>();

        private Builder(String id, Class<S> type, Function<ServiceEnv, S> factory) {
            this.id = id;
            this.type = Objects.requireNonNull(type, "type");
            this.factory = Objects.requireNonNull(factory, "factory");
        }

        /** 声明必需依赖类型:seal 期缺席 → 抛 {@link ServiceDependencyException} 点名双方。 */
        public Builder<S> requires(Class<?> type) {
            required.add(Objects.requireNonNull(type, "type"));
            return this;
        }

        /** 声明可选依赖类型:缺席不报错,运行期 {@code depOpt} 自查。 */
        public Builder<S> requiresOptional(Class<?> type) {
            optional.add(Objects.requireNonNull(type, "type"));
            return this;
        }

        /** 构建:校验 id 非空、同一类型不得既必需又可选(与组件 Spec 同款纪律)。 */
        public ServiceSpec<S> build() {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("ServiceSpec 的 id 不可为空(注册表按 id 查重点名)");
            }
            for (Class<?> both : required) {
                if (optional.contains(both)) {
                    throw new IllegalArgumentException("依赖类型 " + both.getName()
                            + " 不得同时声明为必需与可选(服务 id: " + id + ")");
                }
            }
            return new ServiceSpec<>(this);
        }
    }
}
