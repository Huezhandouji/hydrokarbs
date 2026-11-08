package com.hydrokarbs.engine.instance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;

import com.hydrokarbs.engine.AttributeBag;
import com.hydrokarbs.engine.component.MatchModeComponent;
import com.hydrokarbs.engine.mode.MatchMode;
import com.hydrokarbs.engine.mode.MatchModeMetadata;
import com.hydrokarbs.engine.port.EngineServices;
import com.hydrokarbs.engine.port.MatchModeInstanceServices;
import com.hydrokarbs.engine.port.PlatformPort;
import com.hydrokarbs.engine.session.Handoff;
import com.hydrokarbs.engine.session.MatchModeConfig;
import com.hydrokarbs.engine.session.Session;
import com.hydrokarbs.engine.session.SessionId;
import com.hydrokarbs.engine.session.SessionPolicy;
import com.hydrokarbs.engine.session.SessionSpec;
import com.hydrokarbs.map.MapDefinition;

/**
 * close 幂等(施工计划 1.3「测试与验收」;16-路线图与验收 §16.2-★6):
 * 连调 3 次只清一次——各组件 stop 恰好一次、逆序、登记资源回收恰一次
 * (每个 later 句柄的取消恰一次,重复 close 不再产生新取消)。
 * 离线可跑:零 Bukkit import(Bukkit 类型位置传 null/全限定)。
 */
class MatchModeInstanceCloseIdempotencyTest {

    /** 全回落缺省配置(2.2 接口扩员后的具名假实现)。 */
    private static final class FallbackConfig implements MatchModeConfig {

        @Override
        public int getInt(String path, int def) {
            return def;
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

    /** 假组件:记录钩子;start 里经端口登记一次性任务(产生可回收句柄)。 */
    static final class ProbeComponent extends MatchModeComponent {
        final MatchModeInstanceServices services;
        final List<String> log;

        ProbeComponent(String id, MatchModeInstanceServices svc, List<String> log) {
            super(id, svc);
            this.services = svc;
            this.log = log;
        }

        @Override
        public void start() {
            log.add(id() + ":start");
            services.self().later(1, () -> log.add(id() + ":later-ran"));
        }

        @Override
        public void stop() {
            log.add(id() + ":stop");
        }
    }

    static final class ProbeSpec extends MatchModeComponent.Specification<ProbeComponent> {
        private final List<String> log;

        ProbeSpec(List<String> log) {
            super("probe");
            this.log = log;
        }

        @Override
        public ProbeComponent create(String id, MatchModeInstanceServices svc) {
            return new ProbeComponent(id, svc, log);
        }
    }

    /** 平台假实现:记录并即时执行;句柄 close 计数(幂等观察点)。 */
    static final class CountingPlatform implements PlatformPort {
        int cancels;

        @Override
        public Logger logger() {
            return Logger.getLogger("probe");
        }

        @Override
        public org.bukkit.plugin.Plugin plugin() {   // Bukkit 位置:纯接口,传 null
            return null;
        }

        @Override
        public AutoCloseable scheduleOnce(long delayTicks, Runnable task) {
            task.run();
            return () -> cancels++;
        }
    }

    @Test
    void closeThreeTimesStopsEachComponentExactlyOnceReverseOrder() {
        List<String> log = new ArrayList<>();
        MatchMode.Builder builder = new MatchMode.Builder("m1")
                .metadata(MatchModeMetadata.addTo(new AttributeBag.Builder(), false, 0, 100).build());
        for (int i = 1; i <= 3; i++) {
            builder.addComponent("c" + i, new ProbeSpec(log));
        }
        MatchMode mode = builder.build();

        Session session = new Session(
                new SessionSpec(new SessionId("s1"), "close-probe", List.of()),
                SessionPolicy.defaults(SessionPolicy.Topology.ROTATION));
        session.roster().join(UUID.randomUUID(), 1L);
        MatchModeConfig cfg = new FallbackConfig();
        MapDefinition map = new MapDefinition(java.nio.file.Path.of("map1"), AttributeBag.empty());
        CountingPlatform platform = new CountingPlatform();

        MatchModeInstance instance = mode.createInstance(session, map, cfg, null,
                Handoff.empty(), new EngineServices(platform));
        instance.awakeAll();
        instance.startAll();

        assertEquals(0, platform.cancels, "前置:close 之前句柄未被取消");
        assertEquals(3, log.stream().filter(e -> e.endsWith(":later-ran")).count(),
                "前置:三个组件各登记一个任务并被即时执行");

        instance.close();
        instance.close();
        instance.close();

        List<String> stops = log.stream().filter(e -> e.endsWith(":stop")).toList();
        assertEquals(3, stops.size(), "三个组件的 stop 各恰好一次");
        assertEquals(List.of("c3:stop", "c2:stop", "c1:stop"), stops, "逆声明序 stop");
        assertEquals(3, platform.cancels, "每句柄取消恰一次,重复 close 不再产生新取消");
    }
}
