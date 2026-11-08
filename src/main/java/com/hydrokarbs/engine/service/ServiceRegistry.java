package com.hydrokarbs.engine.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.hydrokarbs.engine.port.ConfigView;

/**
 * 进程级服务的两阶段装配容器(18 号修正案 D2/§3)。
 *
 * <p><b>两阶段契约</b>:install 只声明(查重 id/类型);seal 做「依赖校验 → 拓扑排序
 * → 按序创建 → 启动」,任一工厂/start 抛出 → 逆序 stop 回滚再抛,不留半启动态。
 * 消费面五方法封顶(install/seal/get/require/shutdown),扩需求一律新独立机制(14.32)。
 *
 * <p>单线程约定(D7):全部方法主线程,内部集合不加锁;核心零 Bukkit import,
 * 平台原语只以全限定名出现在构造器签名,验收离线可测。依赖解析:R 满足 ⟺ 存在
 * 服务 B 使 {@code R.isAssignableFrom(B.type())},按声明序取第一个;自需求按环
 * 处理;依赖仅经 requires 声明、由本表注入,禁止服务互 new(14.34)。
 * 回滚:start 成功的服务才进回滚名单,抛出者视为未启动。
 */
public final class ServiceRegistry implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(ServiceRegistry.class.getName());

    /** 装配阶段:OPEN(收声明)→ SEALED(装配成功)→ TERMINATED(关停/装配失败回滚)。 */
    private enum Stage {
        OPEN, SEALED, TERMINATED
    }

    private record Installed(ServiceSpec<?> spec, Object instance) {
    }

    private final org.bukkit.plugin.Plugin plugin;
    private final ConfigView cfg;

    /** 声明序 = install 调用序(拓扑排序的同层稳定次序)。 */
    private final List<ServiceSpec<?>> declaredSpecs = new ArrayList<>();
    private final Map<String, ServiceSpec<?>> byId = new LinkedHashMap<>();
    private final Map<Class<?>, ServiceSpec<?>> byType = new LinkedHashMap<>();

    /** 启动序(拓扑序):回滚与关停都按它逆序。 */
    private final List<Installed> started = new ArrayList<>();

    private Stage stage = Stage.OPEN;
    private final Env env = new Env();

    /**
     * 最小构造:无平台原语——env.plugin() 返回 null、env.cfg() 全回落缺省;离线测试用。
     */
    public ServiceRegistry() {
        this(null, ConfigView.fallbackView());
    }

    /**
     * 装配构造:L0 接线用——注入平台原语与惰性配置面,供 {@link ServiceEnv} 转发。
     * {@code plugin} 可为 null(离线测试);{@code cfg} 为 null 时回落全缺省壳。
     */
    public ServiceRegistry(org.bukkit.plugin.Plugin plugin,
                           ConfigView cfg) {
        this.plugin = plugin;
        this.cfg = cfg != null ? cfg : ConfigView.fallbackView();
    }

    // ── 消费面五方法 ────────────────────────────────────────────────

    /**
     * 阶段一:仅声明。重复 id / 重复类型 → {@link IllegalStateException} 点名先到者;
     * seal/shutdown 之后调用抛(注册表已封闭,★34)。
     */
    public <S> void install(ServiceSpec<S> spec) {
        Objects.requireNonNull(spec, "spec");
        if (stage != Stage.OPEN) {
            throw new IllegalStateException(stage == Stage.SEALED
                    ? "ServiceRegistry 已 seal,拒绝 install: " + spec.id()
                    + "(两阶段契约:声明须全部先于装配,seal 后注册表封闭)"
                    : "ServiceRegistry 已关停(shutdown),拒绝 install: " + spec.id());
        }
        ServiceSpec<?> sameId = byId.get(spec.id());
        if (sameId != null) {
            throw new IllegalStateException("服务 id 重复: " + spec.id()
                    + "(先到者: id=" + sameId.id() + ", 类型=" + sameId.type().getName()
                    + ";后到者类型=" + spec.type().getName() + ")");
        }
        ServiceSpec<?> sameType = byType.get(spec.type());
        if (sameType != null) {
            throw new IllegalStateException("服务类型重复: " + spec.type().getName()
                    + "(先到者: id=" + sameType.id() + ";后到者 id=" + spec.id() + ")");
        }
        declaredSpecs.add(spec);
        byId.put(spec.id(), spec);
        byType.put(spec.type(), spec);
    }

    /**
     * 阶段二:①缺必需依赖 → {@link ServiceDependencyException} 点名双方;
     * ②依赖环 → 点名环上服务;③按拓扑序创建并启动(声明序为同层稳定次序),
     * 创建成功的依赖在其消费者工厂运行前已就绪;④任一工厂/start 抛出 →
     * 回滚已启动服务(逆序 stop)再抛原异常,注册表转终结态。
     */
    public void seal() {
        if (stage != Stage.OPEN) {
            throw new IllegalStateException(stage == Stage.SEALED
                    ? "ServiceRegistry 已 seal,不可重复 seal(两阶段契约不可逆)"
                    : "ServiceRegistry 已关停/装配失败回滚,不可 seal");
        }
        verifyRequiredDependencies();                       // ① 缺必需依赖
        List<ServiceSpec<?>> cycle = detectCycle();
        if (cycle != null) {                                // ② 依赖环
            throw new ServiceDependencyException("服务依赖成环: " + describeCycle(cycle));
        }
        List<ServiceSpec<?>> order = topologicalOrder();    // ③ 拓扑序(声明序稳定)
        for (ServiceSpec<?> spec : order) {                 // ④ 逐个「创建 → 启动」
            Object instance;
            try {
                instance = spec.factory().apply(env);
            } catch (Throwable t) {
                rollbackStarted();
                throw sneakyRethrow(t);
            }
            if (instance instanceof ServiceLifecycle lifecycle) {
                try {
                    lifecycle.start();
                } catch (Throwable t) {
                    rollbackStarted();
                    throw sneakyRethrow(t);
                }
            }
            started.add(new Installed(spec, instance));
        }
        stage = Stage.SEALED;
    }

    /**
     * 按类型取服务:seal 之前调用抛(两阶段契约);未注册 → {@link Optional#empty()};
     * 关停/装配失败回滚后 → 恒 empty(「转不可用」,★37)。
     */
    public <S> Optional<S> get(Class<S> type) {
        Objects.requireNonNull(type, "type");
        if (stage == Stage.OPEN) {
            throw new IllegalStateException(
                    "ServiceRegistry 尚未 seal——get/require 仅在 seal 之后可用(先声明、后装配的两阶段契约)");
        }
        if (stage == Stage.TERMINATED) {
            return Optional.empty();
        }
        return Optional.ofNullable(type.cast(readyInstanceOf(type)));
    }

    /** 按类型取必需服务:缺席 → {@link IllegalStateException}(人话,点名类型与已装配清单)。 */
    public <S> S require(Class<S> type) {
        Objects.requireNonNull(type, "type");
        if (stage == Stage.OPEN) {
            throw new IllegalStateException(
                    "ServiceRegistry 尚未 seal——get/require 仅在 seal 之后可用(先声明、后装配的两阶段契约)");
        }
        S found = type.cast(readyInstanceOf(type));
        if (found == null) {
            throw new IllegalStateException("服务缺席: 类型 " + type.getName() + " 未装配"
                    + (stage == Stage.TERMINATED ? "(注册表已关停/装配失败回滚,全部服务不可用)" : "")
                    + ";已声明: " + ids());
        }
        return found;
    }

    /** 已注册 id 的只读视图(诊断口,不计入消费面五方法封顶;声明序;终结态恒空)。 */
    public Set<String> ids() {
        if (stage == Stage.TERMINATED) {
            return Set.of();
        }
        Set<String> ids = new LinkedHashSet<>();
        for (ServiceSpec<?> spec : declaredSpecs) {
            ids.add(spec.id());
        }
        return Collections.unmodifiableSet(ids);
    }

    /**
     * 关停:逆序 safeStop(逐个 try/catch,异常记日志不中断)→ 幂等(连调多次,
     * 每个服务的 stop 恰执行一次);此后 install 抛(★34/★38)。
     */
    public void shutdown() {
        if (stage == Stage.TERMINATED) {
            return;                          // 幂等:第二、三次调用直接返回
        }
        stopAllStarted();
        stage = Stage.TERMINATED;
    }

    /** {@link AutoCloseable} 别名(修正案 §3 骨架的 implements 落实):语义 = {@link #shutdown()}。 */
    @Override
    public void close() {
        shutdown();
    }

    // ── 装配期依赖解析 ─────────────────────────────────────────────

    /** 声明序第一个主类型可赋值给所需类型的服务;无 → null。 */
    private ServiceSpec<?> specProviding(Class<?> required) {
        for (ServiceSpec<?> spec : declaredSpecs) {
            if (required.isAssignableFrom(spec.type())) {
                return spec;
            }
        }
        return null;
    }

    /** 已就绪实例(创建+启动完成);依赖解析与 get/require/dep/depOpt 共用本口径。 */
    private Object readyInstanceOf(Class<?> type) {
        ServiceSpec<?> spec = specProviding(type);
        if (spec == null) {
            return null;
        }
        for (Installed installed : started) {
            if (installed.spec() == spec) {
                return installed.instance();
            }
        }
        return null;
    }

    /** ①缺必需依赖校验:点名声明方与缺失类型(双方)。 */
    private void verifyRequiredDependencies() {
        for (ServiceSpec<?> spec : declaredSpecs) {
            for (Class<?> required : spec.required()) {
                if (specProviding(required) == null) {
                    throw new ServiceDependencyException("服务 " + spec.id()
                            + " 声明必需依赖 " + required.getName()
                            + ",但注册表中没有任何服务的主类型满足它(缺失方: " + required.getName()
                            + ";已声明: " + ids() + ")");
                }
            }
        }
    }

    /** ②环检测:深度优先三色标记;返回环上服务(沿「谁依赖谁」方向,首尾同点),无环返回 null。 */
    private List<ServiceSpec<?>> detectCycle() {
        int n = declaredSpecs.size();
        int[] color = new int[n];              // 0 未访 / 1 在路径上 / 2 完成
        List<Integer> path = new ArrayList<>();   // 根 → 当前 的访问路径
        for (int i = 0; i < n; i++) {
            if (color[i] == 0) {
                List<Integer> cycle = dfs(i, color, path);
                if (cycle != null) {
                    List<ServiceSpec<?>> result = new ArrayList<>();
                    for (int index : cycle) {
                        result.add(declaredSpecs.get(index));
                    }
                    return result;
                }
            }
        }
        return null;
    }

    /** 返回 null = 无环;非 null = 环上索引(入口开头、沿依赖方向、末尾闭回到入口,如 a→b→a)。 */
    private List<Integer> dfs(int current, int[] color, List<Integer> path) {
        color[current] = 1;
        path.add(current);
        for (Class<?> required : declaredSpecs.get(current).required()) {
            ServiceSpec<?> provider = specProviding(required);
            if (provider == null) {
                continue;                       // ①已点名,此处只管环
            }
            int next = declaredSpecs.indexOf(provider);
            if (color[next] == 1) {             // 回边:依赖指回路径上的先辈 → 成环
                List<Integer> cycle = new ArrayList<>(path.subList(path.indexOf(next), path.size()));
                cycle.add(next);                // 闭合(首尾同点,文案即 a -> b -> a)
                return cycle;
            }
            if (color[next] == 0) {
                List<Integer> deeper = dfs(next, color, path);
                if (deeper != null) {
                    return deeper;
                }
            }
        }
        path.remove(path.size() - 1);
        color[current] = 2;
        return null;
    }

    /** 环文案:a -> b -> a(箭头 = 左边依赖右边)。 */
    private static String describeCycle(List<ServiceSpec<?>> cycle) {
        StringBuilder text = new StringBuilder();
        for (ServiceSpec<?> spec : cycle) {
            if (!text.isEmpty()) {
                text.append(" -> ");
            }
            text.append(spec.id());
        }
        return text.toString();
    }

    /** ③拓扑排序:Kahn 变体——每轮取声明序最前的「依赖已满足」者(同层按声明序,★36)。 */
    private List<ServiceSpec<?>> topologicalOrder() {
        int n = declaredSpecs.size();
        int[] pendingEdges = new int[n];       // 该服务尚待就绪的必需依赖边数
        List<List<Integer>> dependents = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            dependents.add(new ArrayList<>());
        }
        for (int i = 0; i < n; i++) {
            for (Class<?> required : declaredSpecs.get(i).required()) {
                ServiceSpec<?> provider = specProviding(required);
                if (provider == null) {
                    continue;
                }
                dependents.get(declaredSpecs.indexOf(provider)).add(i);
                pendingEdges[i]++;
            }
        }
        boolean[] placed = new boolean[n];
        List<ServiceSpec<?>> order = new ArrayList<>(n);
        for (int round = 0; round < n; round++) {
            int pick = -1;
            for (int i = 0; i < n; i++) {       // 声明序扫描 → 同层稳定
                if (!placed[i] && pendingEdges[i] == 0) {
                    pick = i;
                    break;
                }
            }
            if (pick == -1) {
                throw new ServiceDependencyException(
                        "服务依赖成环(拓扑排序无法推进): " + ids());
            }
            placed[pick] = true;
            order.add(declaredSpecs.get(pick));
            for (int dependent : dependents.get(pick)) {
                pendingEdges[dependent]--;
            }
        }
        return order;
    }

    // ── 回滚与关停共用 ─────────────────────────────────────────────

    /** 逆序 safeStop 全部已启动服务,清空启动名单;异常逐个记日志不中断。 */
    private void stopAllStarted() {
        for (int i = started.size() - 1; i >= 0; i--) {
            Installed installed = started.get(i);
            try {
                if (installed.instance() instanceof ServiceLifecycle lifecycle) {
                    lifecycle.stop();
                }
            } catch (Throwable t) {
                LOG.log(Level.WARNING,
                        "Service stop threw (captured, shutdown continues): " + installed.spec().id(), t);
            }
        }
        started.clear();
    }

    /** seal 失败回滚:逆序停掉已启动服务,注册表转终结态(不留半启动态)。 */
    private void rollbackStarted() {
        stopAllStarted();
        stage = Stage.TERMINATED;
    }

    /** 原样上抛工厂/start 的失败(回滚已完成);受检异常包一层带说明的运行时异常。 */
    private static RuntimeException sneakyRethrow(Throwable t) {
        if (t instanceof RuntimeException runtime) {
            throw runtime;
        }
        if (t instanceof Error error) {
            throw error;
        }
        throw new IllegalStateException("服务工厂/启动抛出受检异常(已回滚已启动服务)", t);
    }

    /** 工厂取物口的实现:依赖查「已就绪名单」,平台原语与配置面来自构造注入。 */
    private final class Env implements ServiceEnv {

        @Override
        @SuppressWarnings("unchecked")
        public <T> T dep(Class<T> type) {
            Object found = readyInstanceOf(type);
            if (found == null) {
                throw new IllegalStateException("ServiceEnv 取用失败: 没有已就绪的 "
                        + type.getName() + " 类型服务——取用与声明不符(该类型未通过"
                        + " requires/requiresOptional 声明,或其提供者尚未按拓扑序创建);"
                        + "已就绪: " + readyIds());
            }
            return (T) found;
        }

        @Override
        public <T> Optional<T> depOpt(Class<T> type) {
            return Optional.ofNullable(type.cast(readyInstanceOf(type)));
        }

        @Override
        public org.bukkit.plugin.Plugin plugin() {
            return plugin;
        }

        @Override
        public ConfigView cfg() {
            return cfg;
        }

        private Set<String> readyIds() {
            Set<String> ready = new LinkedHashSet<>();
            for (Installed installed : started) {
                ready.add(installed.spec().id());
            }
            return ready;
        }
    }
}
