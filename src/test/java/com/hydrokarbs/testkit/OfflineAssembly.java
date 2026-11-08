package com.hydrokarbs.testkit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.hydrokarbs.engine.AttributeBag;
import com.hydrokarbs.engine.component.MatchModeComponent;
import com.hydrokarbs.engine.instance.MatchModeInstance;
import com.hydrokarbs.engine.mode.MatchMode;
import com.hydrokarbs.engine.mode.MatchModeMetadata;
import com.hydrokarbs.engine.port.EngineServices;
import com.hydrokarbs.engine.port.PlatformPort;
import com.hydrokarbs.engine.session.EndResult;
import com.hydrokarbs.engine.session.Handoff;
import com.hydrokarbs.engine.session.MatchModeConfig;
import com.hydrokarbs.engine.session.Session;
import com.hydrokarbs.engine.session.SessionId;
import com.hydrokarbs.engine.session.SessionMember;
import com.hydrokarbs.engine.session.SessionPolicy;
import com.hydrokarbs.engine.session.SessionSpec;
import com.hydrokarbs.map.MapDefinition;

/**
 * 离线装配工具(施工计划 1.5「离线装配的最小假件清单」,test 源集):
 * 测试地图、Map 背后的模式参数测试实现、会话测试桩、假平台、模式拼装便捷口。
 * 全部只依赖引擎公开面;零 Bukkit import(Plugin 位置以全限定名返回 null)。
 */
public final class OfflineAssembly {

    private OfflineAssembly() {
    }

    /**
     * 测试地图(最小声明;★v1.8 两参新形状——路径占位即声明目录名 test_map,
     * metadata 传 {@link AttributeBag#empty()}(无标签声明);引擎实例装配不触文件系统,
     * 无需真实落盘;注册场景请走 MapTestKit)。
     */
    public static MapDefinition map() {
        return new MapDefinition(java.nio.file.Path.of("test_map"), AttributeBag.empty());
    }

    /** Map 背后的模式参数测试实现(缺路径回落 def;2.2 扩员后的具名假实现)。 */
    public static MatchModeConfig cfg(Map<String, Integer> values) {
        return new MapConfig(values);
    }

    /** 全回落缺省的模式参数。 */
    public static MatchModeConfig cfg() {
        return new MapConfig(Map.of());
    }

    /** 整数表背后的最小配置视图(只实现取值族,keys 恒空——测试不消费节键)。 */
    private record MapConfig(Map<String, Integer> values) implements MatchModeConfig {

        @Override
        public int getInt(String path, int def) {
            return values.getOrDefault(path, def);
        }

        @Override
        public String getString(String path, String def) {
            return def;
        }

        @Override
        public List<String> getStringList(String path, List<String> def) {
            return def;
        }

        @Override
        public List<String> keys(String section) {
            return List.of();
        }
    }

    /** 记录结束请求的会话测试桩(覆写非 final 方法)。 */
    public static final class RecordingSession extends Session {

        /** 结束请求记录:(实例 id 尾段, 去向描述)。 */
        public final List<String> endNotices = new ArrayList<>();
        /** 原始结束请求记录(实例, 结果)。 */
        public final List<Map.Entry<MatchModeInstance, EndResult>> rawEndNotices = new ArrayList<>();

        public RecordingSession(String id, SessionMember... members) {
            super(new SessionSpec(new SessionId(id), "recording-probe", List.of()),
                    SessionPolicy.defaults(SessionPolicy.Topology.ROTATION));
            for (SessionMember member : members) {
                roster().join(member.uuid(), member.joinedAtMillis());
            }
        }

        @Override
        public void instanceEnded(MatchModeInstance instance, EndResult result) {
            endNotices.add(instance.id().value() + "/"
                    + (result.transition().isEmpty() ? "nowhere" : "intent:" + describe(result)));
            rawEndNotices.add(Map.entry(instance, result));
        }

        private static String describe(EndResult result) {
            return result.transition().map(t -> t.target().modeId()).orElse("?");
        }
    }

    /** 空成员会话桩。 */
    public static RecordingSession session(String id) {
        return new RecordingSession(id);
    }

    /** 平台假实现:记录并即时执行一次性任务;句柄 close 即计数。 */
    public static final class FakePlatform implements PlatformPort {

        /** later→scheduleOnce 的延迟记录。 */
        public final List<Long> delays = new ArrayList<>();
        /** 已执行的任务快照。 */
        public final List<Runnable> tasks = new ArrayList<>();
        /** 句柄 close 次数(close 统一回收的外显)。 */
        public int cancels;

        @Override
        public Logger logger() {
            return Logger.getLogger("test-platform");
        }

        @Override
        public org.bukkit.plugin.Plugin plugin() {   // Bukkit 位置:纯接口,返回 null,不 import
            return null;
        }

        @Override
        public AutoCloseable scheduleOnce(long delayTicks, Runnable task) {
            delays.add(delayTicks);
            tasks.add(task);
            task.run();
            return () -> cancels++;
        }
    }

    /** 四件套拼装便捷口:按传入顺序 addComponent(声明序 = 入表序;mapTags 缺省空集)。 */
    public static MatchMode mode(String modeId, LinkedHashMap<String, MatchModeComponent.Specification<?>> specs) {
        MatchMode.Builder builder = new MatchMode.Builder(modeId)
                .metadata(MatchModeMetadata.addTo(new AttributeBag.Builder(), false, 0, 100).build());
        specs.forEach(builder::addComponent);
        return builder.build();
    }

    /** 造实例(真容器;awakeAll/startAll 为包私有,调用方须在 engine.instance 包)。 */
    public static MatchModeInstance instance(MatchMode mode, RecordingSession session, FakePlatform platform) {
        return mode.createInstance(session, map(), cfg(), null, Handoff.empty(), new EngineServices(platform));
    }
}
