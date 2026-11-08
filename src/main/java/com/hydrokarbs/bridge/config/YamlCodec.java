package com.hydrokarbs.bridge.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.MarkedYAMLException;

/**
 * YAML → 不可变模型的唯一编解码点(§15.2)。
 *
 * <p>唯一读口 {@link #readMode}:文件 → 原始映射(交 {@link MatchModeConfigLoader}),
 * UTF-8,SnakeYAML。任何失败抛 {@link ConfigException},消息带文件名与行号。
 *
 * <p><b>线程契约(§15.2)</b>:热重载须虚拟线程解析、回主线程换引用;启动期
 * 文件量小,主线程同步合法。本类无共享可变状态,任意线程可调;零 Bukkit import。
 */
public final class YamlCodec {

    /** 每次解析新建解析器实例(SnakeYAML 实例非线程安全,小对象代价可忽略)。 */
    private Yaml newParser() {
        return new Yaml();
    }

    /**
     * 读模式/主配置文件:产出原始映射(空文件 → 空 Map,可用——§15.2 铁律 1)。
     * 值级容错(缺键默认/非法降级+警告)不在这里,在配置视图
     * ({@link MatchModeConfigLoader})——本方法只负责「文件 → 映射」。
     */
    public Map<String, Object> readMode(Path file) {
        return readRoot(file, String.valueOf(file.getFileName()), "配置");
    }

    private Map<String, Object> readRoot(Path file, String fileName, String what) {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Object root = newParser().load(reader);
            if (root == null) {
                return Map.of();                          // 空文件 = 空映射
            }
            if (!(root instanceof Map<?, ?> map)) {
                throw new ConfigException(what + "文件 " + fileName + ": 根节点必须是映射,实为 "
                        + root.getClass().getSimpleName());
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> typed = (Map<String, Object>) map;
            return typed;
        } catch (IOException e) {
            throw new ConfigException("读取" + what + "文件失败: " + fileName, e);
        } catch (ConfigException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ConfigException(what + "文件格式错误: " + fileName + ": " + locate(e), e);
        }
    }

    /** 提取底层解析器的定位信息(含行号;SnakeYAML 的消息模板自带「line N, column M」)。 */
    private static String locate(RuntimeException e) {
        if (e instanceof MarkedYAMLException marked) {
            if (marked.getProblemMark() != null) {
                return "line " + (marked.getProblemMark().getLine() + 1) + ": " + e.getMessage();
            }
        }
        return e.getMessage();
    }
}
