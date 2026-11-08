package com.hydrokarbs.bridge.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import org.bukkit.plugin.java.JavaPlugin;

import com.hydrokarbs.engine.port.ConfigView;
import com.hydrokarbs.engine.session.MatchModeConfig;
import com.hydrokarbs.map.MapRegistry;

/**
 * 配置装载门面 + 默认资源释放(§15.2)。
 *
 * <p>三件事:saveDefaults 首次启动释放默认资源(已存在不覆盖,幂等);
 * loadAll 扫 {@code modes/*.yml} 与 {@code config.yml}——铁律 3:单文件失败
 * 跳过 + SEVERE 不中断整体,空目录合法;{@link ConfigView} 实现——主配置
 * 惰性只读面(reload 后自动读新值)。
 *
 * <p>线程契约(§15.2):启动期主线程同步合法;热重载须虚拟线程解析、
 * 回主线程后换引用(字段 volatile,替换原子)。
 */
public final class ConfigRepository implements ConfigView {

    private static final Logger DEFAULT_LOG = Logger.getLogger(ConfigRepository.class.getName());

    /** 默认资源清单(幂等释放;含出厂空模式配置,嵌套路径父目录随建;玩法实例后续在此追加)。 */
    private static final List<String> DEFAULT_RESOURCES = List.of("config.yml", "modes/limbo.yml");

    private final Path dataDir;
    private final DefaultResourceSource defaults;
    private final YamlCodec codec;
    private final MatchModeConfigLoader modeLoader;
    private final Logger logger;

    private final MapRegistry maps;   // 地图不经配置装载:外部经 MapRegistry 单通道注册
    private volatile Map<String, Object> mainConfig = Map.of();
    private volatile Map<String, MatchModeConfig> modeConfigs = Map.of();

    public ConfigRepository(Path dataDir, DefaultResourceSource defaults) {
        this(dataDir, defaults, DEFAULT_LOG);
    }

    /** 测试注入日志捕获器的构造口(生产用双参构造)。 */
    ConfigRepository(Path dataDir, DefaultResourceSource defaults, Logger logger) {
        this.dataDir = dataDir;
        this.defaults = defaults;
        this.logger = logger;
        this.codec = new YamlCodec();
        this.modeLoader = new MatchModeConfigLoader(codec, logger);
        this.maps = new MapRegistry();
    }

    /**
     * 首次启动释放默认资源:<b>已存在的文件永不覆盖</b>(装载时序不变式 1)。
     * 嵌套资源的目标父目录逐资源随建;默认资源本身缺席则 WARN 跳过。
     */
    public void saveDefaults() throws IOException {
        Files.createDirectories(dataDir);
        for (String name : DEFAULT_RESOURCES) {
            Path target = dataDir.resolve(name);
            if (Files.exists(target)) {
                continue;                                  // 幂等:二次启动不覆盖
            }
            Files.createDirectories(target.getParent());   // 嵌套资源父目录随建
            try (InputStream source = defaults.open(name)) {
                if (source == null) {
                    logger.log(Level.WARNING, "Default resource missing, not extracted: {0}", name);
                    continue;
                }
                Files.copy(source, target);
            }
        }
    }

    /**
     * 全量装载(启动期一次;/reload 只是再次调用本方法)。
     * 单文件失败跳过 + SEVERE 点名文件(铁律 3);空目录/缺目录合法。
     */
    public void loadAll() throws IOException {
        mainConfig = loadMainConfig();
        modeConfigs = loadModeConfigs();
        logger.log(Level.INFO, "Config loaded: {0} mode config(s)", modeConfigs.size());
    }

    /** 地图目录(引用原子替换语义见 {@link MapRegistry};条目全部来自外部注册)。 */
    public MapRegistry maps() {
        return maps;
    }

    /**
     * 取指定模式的配置视图:缓存命中直接返回;未装载的模式返回空 config + WARN
     * (铁律 1:注册期 build 对缺文件的模式以全默认参数进行,不炸注册)。
     */
    public MatchModeConfig modeConfig(String modeId) {
        MatchModeConfig cached = modeConfigs.get(modeId);
        if (cached != null) {
            return cached;
        }
        return modeLoader.load(modesDir().resolve(modeId + ".yml"));
    }

    // ── ConfigView:主配置惰性只读面 ──────────────────────────────

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(String path, T fallback) {
        Object value = resolve(mainConfig, path);
        if (value == null) {
            return fallback;
        }
        if (fallback instanceof Integer) {
            return value instanceof Number n ? (T) Integer.valueOf(n.intValue()) : fallback;
        }
        if (fallback instanceof Long) {
            return value instanceof Number n ? (T) Long.valueOf(n.longValue()) : fallback;
        }
        if (fallback instanceof Double) {
            return value instanceof Number n ? (T) Double.valueOf(n.doubleValue()) : fallback;
        }
        if (fallback instanceof Boolean) {
            return value instanceof Boolean b ? (T) b : fallback;
        }
        if (fallback instanceof String) {
            return (value instanceof String || value instanceof Number || value instanceof Boolean)
                    ? (T) String.valueOf(value)
                    : fallback;
        }
        if (fallback instanceof List<?>) {
            return value instanceof List<?> list ? (T) List.copyOf(list) : fallback;
        }
        return fallback;
    }

    // ── 内部:装载编排 ───────────────────────────────────────────

    private Map<String, Object> loadMainConfig() {
        Path file = dataDir.resolve("config.yml");
        if (!Files.isRegularFile(file)) {
            logger.log(Level.WARNING, "config.yml missing, using built-in defaults");
            return Map.of();
        }
        try {
            return codec.readMode(file);
        } catch (ConfigException e) {
            logger.log(Level.SEVERE, "config.yml broken, using built-in defaults", e);
            return Map.of();
        }
    }

    private Map<String, MatchModeConfig> loadModeConfigs() throws IOException {
        Map<String, MatchModeConfig> loaded = new LinkedHashMap<>();
        for (Path file : listYaml(modesDir())) {
            String modeId = stripYamlSuffix(String.valueOf(file.getFileName()));
            try {
                loaded.put(modeId, modeLoader.load(file));
            } catch (ConfigException e) {
                logger.log(Level.SEVERE, "Mode config load failed, skipping: " + file, e);
            }
        }
        return Map.copyOf(loaded);
    }

    private Path modesDir() {
        return dataDir.resolve("modes");
    }

    private List<Path> listYaml(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> entries = Files.list(dir)) {
            return entries.filter(Files::isRegularFile)
                    .filter(path -> String.valueOf(path.getFileName()).endsWith(".yml"))
                    .sorted()
                    .toList();
        }
    }

    private static String stripYamlSuffix(String fileName) {
        return fileName.endsWith(".yml") ? fileName.substring(0, fileName.length() - 4) : fileName;
    }

    /** 点分段下钻(与配置视图的路径语法一致)。 */
    private static Object resolve(Map<String, Object> root, String path) {
        if (path == null || path.isBlank()) {
            return root.isEmpty() ? null : root;
        }
        Object node = root;
        for (String segment : path.split("\\.")) {
            if (!(node instanceof Map<?, ?> map)) {
                return null;
            }
            node = map.get(segment);
            if (node == null) {
                return null;
            }
        }
        return node;
    }

    /**
     * 默认资源源:按资源名开输入流(null = 无该默认资源)。
     * 生产实现见 {@link #of(JavaPlugin)}——<b>本类的唯一 Bukkit 触点</b>,
     * 离线测试以临时目录/内存实现注入。
     */
    @FunctionalInterface
    public interface DefaultResourceSource {

        InputStream open(String resourceName) throws IOException;

        /** 从插件 jar 内读默认资源(JavaPlugin#getResource;唯一 Bukkit 依赖点)。 */
        static DefaultResourceSource of(JavaPlugin plugin) {
            return plugin::getResource;
        }
    }
}
