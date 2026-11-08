package com.hydrokarbs.engine.component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.hydrokarbs.engine.mode.MatchMode;
import com.hydrokarbs.engine.port.ComponentLookupPort;

/**
 * 实例组件注册表:声明序集合 + 每组件资源表(§2.5)。
 *
 * <p>声明记录来自模式聚合根的 {@link MatchMode.ComponentEntry},装配期逐条
 * {@link #register}。遍历一律走不可变快照——遍历期间组件再订阅/退订不破坏本轮;
 * 资源表按组件实例索引(句柄统一挂这里),由 {@link #reclaimRegisteredResources()}
 * 幂等统一回收——组件 stop 返回后框架自动做,组件不自己注销;
 * 查找口({@link #get}/{@link #getAll}/{@link #byId})实现
 * {@link ComponentLookupPort},即 svc 服务集的 components 成员。
 */
public final class ComponentRegistry implements ComponentLookupPort {

    private static final Logger LOG = Logger.getLogger(ComponentRegistry.class.getName());

    private final Map<String, MatchModeComponent> componentsById = new LinkedHashMap<>();
    private final Map<MatchModeComponent, List<AutoCloseable>> resourcesByComponent =
            new IdentityHashMap<>();
    /** 发起方不在组件栈上的登记桶(诊断归属不明的句柄;回收同样覆盖)。 */
    private final List<AutoCloseable> unownedResources = new ArrayList<>();

    /**
     * 登记组件 + 声明记录。装配期 id 查重已在模式 Builder 做;
     * 容器内同一组件重复 register 视为编程错误,抛 {@link IllegalStateException}。
     */
    public void register(MatchModeComponent component, MatchMode.ComponentEntry entry) {
        Objects.requireNonNull(component, "component");
        Objects.requireNonNull(entry, "entry");
        if (componentsById.containsKey(component.id()) || resourcesByComponent.containsKey(component)) {
            throw new IllegalStateException("组件重复注册(编程错误): " + component.id());
        }
        componentsById.put(component.id(), component);
        resourcesByComponent.put(component, new ArrayList<>());
    }

    /**
     * 登记可回收资源句柄到发起组件名下(close 统一回收)。
     * owner 为 null 时进公共桶;owner 未注册抛 {@link IllegalStateException}(装配序错误)。
     */
    public void registerResource(MatchModeComponent owner, AutoCloseable handle) {
        Objects.requireNonNull(handle, "handle");
        if (owner == null) {
            unownedResources.add(handle);
            return;
        }
        List<AutoCloseable> bucket = resourcesByComponent.get(owner);
        if (bucket == null) {
            throw new IllegalStateException("资源登记的组件未注册: " + owner.id());
        }
        bucket.add(handle);
    }

    /** 声明序不可变快照。 */
    public List<MatchModeComponent> inDeclarationOrder() {
        return List.copyOf(componentsById.values());
    }

    /** 逆声明序不可变快照(逆序 stop 用)。 */
    public List<MatchModeComponent> inReverseDeclarationOrder() {
        List<MatchModeComponent> reversed = new ArrayList<>(componentsById.values());
        Collections.reverse(reversed);
        return List.copyOf(reversed);
    }

    /** 快照遍历(逐组件派发一拍;§12.2 {@code components.forEachTicking(c -> c.update())})。 */
    public void forEachTicking(Consumer<? super MatchModeComponent> action) {
        inDeclarationOrder().forEach(action);
    }

    @Override
    public <T extends MatchModeComponent> T get(Class<T> type) {
        Objects.requireNonNull(type, "type");
        for (MatchModeComponent component : componentsById.values()) {
            if (type.isInstance(component)) {
                return type.cast(component);
            }
        }
        return null;
    }

    @Override
    public <T> List<T> getAll(Class<T> type) {
        Objects.requireNonNull(type, "type");
        List<T> found = new ArrayList<>();
        for (MatchModeComponent component : componentsById.values()) {
            if (type.isInstance(component)) {
                found.add(type.cast(component));
            }
        }
        return List.copyOf(found);
    }

    @Override
    public Optional<MatchModeComponent> byId(String id) {
        return Optional.ofNullable(componentsById.get(id));
    }

    /**
     * 统一回收全部登记资源(幂等):逐句柄 close,单个失败记 WARN 后继续、不中断;
     * 回收后清空登记,连调多次时第二次起为空操作。
     */
    public void reclaimRegisteredResources() {
        closeAll(unownedResources);
        unownedResources.clear();
        for (List<AutoCloseable> bucket : resourcesByComponent.values()) {
            closeAll(bucket);
            bucket.clear();
        }
    }

    private static void closeAll(List<AutoCloseable> handles) {
        for (AutoCloseable handle : handles) {
            try {
                handle.close();
            } catch (Throwable t) {
                LOG.log(Level.WARNING, "Registered resource reclaim failed (ignored)", t);
            }
        }
    }
}
