package com.hydrokarbs.engine.mode;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.hydrokarbs.engine.component.ComponentDependencyException;
import com.hydrokarbs.engine.port.ModeLookup;
import com.hydrokarbs.engine.session.MatchModeConfig;
import com.hydrokarbs.map.MapDefinition;

/**
 * 模式注册表:发现 + 注册闸(12-代码骨架 §12.2)。
 *
 * <p>发现(★v1.6):仅 {@code ServiceLoader}(本 jar);第三方模式经
 * {@code HydrokarbsApi#registerMode} 显式注册。引擎零模式知识(§14.13)。
 *
 * <p><b>注册闸(两类失败语义不得弄反,§2.6)</b>:依赖校验失败 → 启动期跳过
 * 注册 + SEVERE(坏模板不进注册表);重复 id → 一律响亮抛
 * {@link IllegalStateException}(绝不静默覆盖)。0 个模式注册属合法,只打 INFO。
 *
 * <p>进程级单例(§2.3):注册期 build 一次、切换期不重建;build 后不可追加组件
 * (A9)。tag 匹配({@link #matchesTags})是唯一的模式↔地图耦合面。线程:一律主线程。
 */
public final class MatchModeRegistry implements ModeLookup {

    private static final Logger DEFAULT_LOG = Logger.getLogger(MatchModeRegistry.class.getName());

    private final Map<String, MatchMode> modes = new LinkedHashMap<>();
    private final Function<String, MatchModeConfig> configSource;
    private final Logger logger;

    public MatchModeRegistry(Function<String, MatchModeConfig> configSource) {
        this(configSource, DEFAULT_LOG);
    }

    /** 日志注入构造口(离线测试捕获 SEVERE/INFO 断言用;生产行为与单参构造一致)。 */
    public MatchModeRegistry(Function<String, MatchModeConfig> configSource, Logger logger) {
        this.configSource = configSource;
        this.logger = logger;
    }

    /**
     * 发现(本 jar 先,每个提供方走同一注册闸):
     * ① {@code ServiceLoader.load(MatchModeProvider.class)}——本 jar 的
     * {@code META-INF/services} 清单;② ServicesManager 半边已退场(★v1.6)
     * ——第三方经 {@code HydrokarbsApi#registerMode} 注册(引擎零静态调用,离线可测)。
     * 收尾打注册表初始化行(0 个模式属正常,INFO 级)。
     */
    public void discover() {
        // ① ServiceLoader(本 jar):SPI 清单里每行一个工厂全限定名
        ServiceLoader.load(MatchModeProvider.class).forEach(this::register);
        // ② ServicesManager 半边已退场(★v1.6)——第三方注册走 HydrokarbsApi#registerMode
        logger.log(Level.INFO, "Mode registry initialized: {0} mode(s)", modes.size());
    }

    /**
     * 注册一个提供方(启动期口径):{@code build(配置)} → {@code verifyDependencies()}
     * → 入表。依赖校验失败 → SEVERE + 跳过(坏模板不进注册表);重复 id → 响亮抛
     * {@link IllegalStateException}(绝不静默覆盖)。缺配置文件的模式以全默认参数
     * build(§15.2 铁律 1)。
     */
    public void register(MatchModeProvider provider) {
        MatchMode mode = provider.build(configSource.apply(provider.id()));
        try {
            mode.verifyDependencies();                     // ★ 注册前闸:坏模板不进注册表(§2.6)
        } catch (ComponentDependencyException e) {
            logger.log(Level.SEVERE, "Mode " + provider.id() + " failed dependency check, not registered", e);
            return;
        }
        if (modes.putIfAbsent(mode.id(), mode) != null) {
            throw new IllegalStateException("重复的模式 id: " + mode.id());
        }
        logger.log(Level.INFO, "Loaded mode: {0}{1}",
                new Object[] { mode.id(), MatchModeMetadata.recordsHistory(mode.meta()) ? "" : " (no history)" });
    }

    /**
     * 注册一个运行期提供方(★v1.6,api 显式注册通道):与 {@link #register} 同闸,
     * 但失败(build 抛异常/依赖校验失败/重复 id)一律响亮上抛,由调用方捕获。
     * 缺配置文件的模式以全默认参数 build(§15.2 铁律 1)。主线程调用。
     *
     * @return 入表的 {@link MatchMode} 实例
     * @throws IllegalStateException 重复 id(先到者保留,绝不静默覆盖)
     */
    public MatchMode registerProvider(MatchModeProvider provider) {
        MatchMode mode = provider.build(configSource.apply(provider.id()));
        mode.verifyDependencies();                     // 注册前闸:坏模板不进注册表(§2.6)
        if (modes.putIfAbsent(mode.id(), mode) != null) {
            throw new IllegalStateException("重复的模式 id: " + mode.id());
        }
        logger.log(Level.INFO, "Loaded mode: {0}{1}",
                new Object[] { mode.id(), MatchModeMetadata.recordsHistory(mode.meta()) ? "" : " (no history)" });
        return mode;
    }

    /**
     * 按 id 摘除模式(★v1.6,api 显式摘除通道的注册表落点):目录中无此 id →
     * 幂等 no-op。<b>会话回退编排不在这里</b>,由调用方(api 层)对受影响会话
     * 发起回退/解散(§3.1)。
     *
     * @return true = 已摘除;false = 目录中无此 id(幂等 no-op)
     */
    public boolean unregister(String modeId) {
        Objects.requireNonNull(modeId, "modeId");
        return modes.remove(modeId) != null;
    }

    /** 按 id 取模式;缺失响亮抛(切换准备阶段「目标不存在」拒绝的上游)。 */
    @Override
    public MatchMode require(String modeId) {
        MatchMode mode = modes.get(modeId);
        if (mode == null) {
            throw new NoSuchElementException("未注册的模式 id: " + modeId
                    + "(已注册: " + modes.keySet() + ")");
        }
        return mode;
    }

    /** 全部已注册模式(声明序,不可变快照)。 */
    @Override
    public Collection<MatchMode> all() {
        return Collections.unmodifiableCollection(modes.values());
    }

    /**
     * 模式↔地图匹配唯一判据(31 号决策 B):模式侧 mapTags 为空 = <b>通配</b>
     * (接受任意地图,仍受会话地图池过滤);否则与地图侧标签
     * ({@link MapDefinition#tags()})交集非空才匹配;地图侧空标签<b>不构成通配</b>。
     * {@link #forMap} 与切换准备 A3 的地图解析共用本谓词;注册表不认识任何
     * 具体 id 或标签词汇(§12.2)。
     */
    public static boolean matchesTags(MatchMode mode, MapDefinition map) {
        return mode.mapTags().isEmpty() || !Collections.disjoint(mode.mapTags(), map.tags());
    }

    /**
     * 该地图可用的模式:{@link #matchesTags} 谓词过滤(空结果合法——地图适配的
     * 强制点在切换检查,不在注册表)。TODO(§15.2):{@code supported-modes}
     * 显式收窄字段后续阶段评估。
     */
    public List<MatchMode> forMap(MapDefinition map) {
        return modes.values().stream()
                .filter(mode -> matchesTags(mode, map))
                .toList();
    }
}
