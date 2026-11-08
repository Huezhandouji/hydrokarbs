package com.hydrokarbs.engine.instance;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
 * 清场韧性(施工计划 1.3「测试与验收」;16-路线图与验收 §16.2-★7):
 * 中间组件 stop 抛异常 → 其余组件 stop 全部完成、close 正常返回、异常被记录
 * (逆序且异常不中断)。
 */
class CleanupResilienceTest {

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


    /** 收集 WARN 及以上日志的假 Handler。 */
    static final class CapturingHandler extends Handler {
        final List<LogRecord> records = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                records.add(record);
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }

    /** 假组件:stop 可注入异常。 */
    static final class ProbeComponent extends MatchModeComponent {
        final List<String> log;
        final Runnable onStop;

        ProbeComponent(String id, MatchModeInstanceServices svc, List<String> log, Runnable onStop) {
            super(id, svc);
            this.log = log;
            this.onStop = onStop;
        }

        @Override
        public void stop() {
            log.add(id() + ":stop");
            if (onStop != null) {
                onStop.run();
            }
        }
    }

    static final class ProbeSpec extends MatchModeComponent.Specification<ProbeComponent> {
        private final List<String> log;
        private final Runnable onStop;

        ProbeSpec(List<String> log, Runnable onStop) {
            super("probe");
            this.log = log;
            this.onStop = onStop;
        }

        @Override
        public ProbeComponent create(String id, MatchModeInstanceServices svc) {
            return new ProbeComponent(id, svc, log, onStop);
        }
    }

    static final class NullPlatform implements PlatformPort {
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
            return () -> {
            };
        }
    }

    private final CapturingHandler handler = new CapturingHandler();
    private final Logger containerLogger = Logger.getLogger(MatchModeInstance.class.getName());

    @BeforeEach
    void attachHandler() {
        containerLogger.addHandler(handler);
    }

    @AfterEach
    void detachHandler() {
        containerLogger.removeHandler(handler);
    }

    @Test
    void middleComponentStopFailureDoesNotBreakCleanup() {
        List<String> log = new ArrayList<>();
        RuntimeException stopFailure = new RuntimeException("stop-boom");
        MatchMode.Builder builder = new MatchMode.Builder("m1")
                .metadata(MatchModeMetadata.addTo(new AttributeBag.Builder(), false, 0, 100).build());
        builder.addComponent("c1", new ProbeSpec(log, null));
        builder.addComponent("c2", new ProbeSpec(log, () -> {
            throw stopFailure;
        }));
        builder.addComponent("c3", new ProbeSpec(log, null));
        MatchMode mode = builder.build();

        Session session = new Session(
                new SessionSpec(new SessionId("s1"), "cleanup-probe", List.of()),
                SessionPolicy.defaults(SessionPolicy.Topology.ROTATION));
        session.roster().join(UUID.randomUUID(), 1L);
        MatchModeInstance instance = mode.createInstance(session,
                new MapDefinition(java.nio.file.Path.of("map1"), AttributeBag.empty()),
                new FallbackConfig(), null, Handoff.empty(), new EngineServices(new NullPlatform()));

        instance.awakeAll();
        instance.startAll();

        assertDoesNotThrow(instance::close, "close 正常返回:单个 stop 异常不上抛");

        assertEquals(List.of("c3:stop", "c2:stop", "c1:stop"),
                log.stream().filter(e -> e.endsWith(":stop")).toList(),
                "其余组件 stop 全部完成(逆序,异常不中断)");

        boolean recorded = handler.records.stream()
                .anyMatch(r -> r.getLevel() == Level.WARNING && r.getThrown() == stopFailure);
        assertEquals(true, recorded, "stop 异常被记录(WARN 携带原异常)");
    }
}
