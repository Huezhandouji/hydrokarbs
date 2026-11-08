package com.hydrokarbs.engine;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;

import com.hydrokarbs.engine.AttributeBag;
import com.hydrokarbs.engine.component.ComponentDependencyException;
import com.hydrokarbs.engine.component.MatchModeComponent;
import com.hydrokarbs.engine.instance.MatchModeInstance;
import com.hydrokarbs.engine.mode.MatchMode;
import com.hydrokarbs.engine.mode.MatchModeMetadata;
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
 * 装配期依赖校验(02-核心概念 §2.6 算法表逐行成测试;§16.2-1 的前置闸)。
 *
 * <p>匹配规则:A 的必需类型 R 被满足 ⟺ 存在另一组件 B(id ≠ A)使
 * {@code R.isAssignableFrom(B.providedType())}——自己不算自己的提供者。
 * 六个行为断言:必需被满足通过 / 自环硬失败 / 可选缺失通过 / 互环通过(环不检查,
 * 唤醒顺序 = 声明序,不承诺拓扑序)/ 失败消息点名模式 id+组件 id+缺的类型全名 /
 * {@code createInstance} 先校验后造(路径不可绕过)。
 *
 * <p>fake 均为本测试内自定义的最小件(不依赖 1.5 的生命周期记录件);离线可跑,
 * 零 Bukkit import(Plugin 位置返回 null)。
 */
class ComponentResolutionTest {

    // ── fake 组件与描述符(最小件:只为类型图与创建哨兵服务) ──────────────

    /** 基座:无钩子行为。 */
    static class StubComponent extends MatchModeComponent {
        StubComponent(String id, MatchModeInstanceServices svc) {
            super(id, svc);
        }
    }

    static final class Alpha extends StubComponent {
        Alpha(String id, MatchModeInstanceServices svc) {
            super(id, svc);
        }
    }

    static final class Beta extends StubComponent {
        Beta(String id, MatchModeInstanceServices svc) {
            super(id, svc);
        }
    }

    /** 永不入表的类型:扮演「无人提供」的必需/可选依赖。 */
    static final class Missing extends StubComponent {
        Missing(String id, MatchModeInstanceServices svc) {
            super(id, svc);
        }
    }

    /** 最小描述符:供给面显式声明,create 记入哨兵日志(证明「造没造、按什么序造」)。 */
    static final class StubSpec<T extends MatchModeComponent> extends MatchModeComponent.Specification<T> {

        private final Class<T> provided;
        private final BiFunction<String, MatchModeInstanceServices, T> componentFactory;
        private final List<String> creationLog;

        StubSpec(String label, Class<T> provided,
                 BiFunction<String, MatchModeInstanceServices, T> componentFactory,
                 List<String> creationLog) {
            super(label);
            this.provided = provided;
            this.componentFactory = componentFactory;
            this.creationLog = creationLog;
        }

        @Override
        public Class<T> providedType() {
            return provided;
        }

        @Override
        public T create(String id, MatchModeInstanceServices svc) {
            creationLog.add(id);
            return componentFactory.apply(id, svc);
        }
    }

    /** 平台假实现(createInstance 装配路径用不到调度;Plugin 位置返回 null)。 */
    static final class NullPlatform implements PlatformPort {
        @Override
        public Logger logger() {
            return Logger.getLogger("component-resolution-test");
        }

        @Override
        public org.bukkit.plugin.Plugin plugin() {
            return null;
        }

        @Override
        public AutoCloseable scheduleOnce(long delayTicks, Runnable task) {
            throw new IllegalStateException("本测试不应触达调度");
        }
    }

    // ── 构造工具 ──────────────────────────────────────────────────

    private static MatchMode.Builder modeBuilder(String id) {
        return new MatchMode.Builder(id)
                .metadata(MatchModeMetadata.addTo(new AttributeBag.Builder(), false, 0, 100).build());
    }

    private static MatchModeInstance create(MatchMode mode) {
        return mode.createInstance(
                new Session(new SessionSpec(new SessionId("s1"), "resolve-probe", List.of()),
                        SessionPolicy.defaults(SessionPolicy.Topology.ROTATION)),
                new MapDefinition(java.nio.file.Path.of("map1"), AttributeBag.empty()),
                com.hydrokarbs.testkit.OfflineAssembly.cfg(),
                null,
                Handoff.empty(),
                new com.hydrokarbs.engine.port.EngineServices(new NullPlatform()));
    }

    // ── §2.6 六行逐行 ─────────────────────────────────────────────

    /** 行 1:必需依赖被另一组件满足 → verifyDependencies 通过。 */
    @Test
    void requiredDependencySatisfiedByAnotherComponentPasses() {
        List<String> creationLog = new ArrayList<>();
        MatchMode mode = modeBuilder("ok-mode")
                .addComponent("alpha", new StubSpec<>("alpha", Alpha.class, Alpha::new, creationLog)
                        .requires(Beta.class))
                .addComponent("beta", new StubSpec<>("beta", Beta.class, Beta::new, creationLog))
                .build();
        assertDoesNotThrow(mode::verifyDependencies, "alpha 的必需依赖 Beta 由 beta 满足,必须通过");
    }

    /** 行 6(自环):A 声明自己提供的类型 → 仍算缺依赖,硬失败。 */
    @Test
    void selfProvidedTypeStillCountsAsMissing() {
        List<String> creationLog = new ArrayList<>();
        MatchMode mode = modeBuilder("self-mode")
                .addComponent("alpha", new StubSpec<>("alpha", Alpha.class, Alpha::new, creationLog)
                        .requires(Alpha.class))
                .addComponent("beta", new StubSpec<>("beta", Beta.class, Beta::new, creationLog))
                .build();
        ComponentDependencyException ex = assertThrows(ComponentDependencyException.class,
                mode::verifyDependencies, "自己不算自己的提供者,自环必须硬失败");
        assertTrue(String.valueOf(ex.getMessage()).contains(Alpha.class.getName()),
                "失败消息须点名缺的类型全名,实际:" + ex.getMessage());
    }

    /** 行 4(可选):可选依赖缺失 → 通过、不报错。 */
    @Test
    void missingOptionalDependencyPassesSilently() {
        List<String> creationLog = new ArrayList<>();
        MatchMode mode = modeBuilder("opt-mode")
                .addComponent("alpha", new StubSpec<>("alpha", Alpha.class, Alpha::new, creationLog)
                        .requiresOptional(Missing.class))
                .addComponent("beta", new StubSpec<>("beta", Beta.class, Beta::new, creationLog))
                .build();
        assertDoesNotThrow(mode::verifyDependencies, "可选依赖缺失不报错,组件运行期自查");
    }

    /** 行 5(环):互环 A↔B → 装配通过(环不检查;唤醒顺序 = 声明序,不承诺拓扑序)。 */
    @Test
    void mutualCyclePassesAssembly() {
        List<String> creationLog = new ArrayList<>();
        MatchMode mode = modeBuilder("cycle-mode")
                .addComponent("alpha", new StubSpec<>("alpha", Alpha.class, Alpha::new, creationLog)
                        .requires(Beta.class))
                .addComponent("beta", new StubSpec<>("beta", Beta.class, Beta::new, creationLog)
                        .requires(Alpha.class))
                .build();
        assertDoesNotThrow(mode::verifyDependencies, "环不检查:互为提供者的必需依赖两边都被满足");
        MatchModeInstance instance = assertDoesNotThrow(() -> create(mode), "互环表的完整装配路径照常可用");
        assertEquals(List.of("alpha", "beta"), creationLog, "组件按声明序创建(环内先后未定义——不得依赖)");
        assertNotNull(instance);
    }

    /** 行 3(失败形态):失败消息点名模式 id、组件 id、缺的类型全名。 */
    @Test
    void failureMessageNamesModeComponentAndMissingType() {
        List<String> creationLog = new ArrayList<>();
        MatchMode mode = modeBuilder("mode-x")
                .addComponent("alpha", new StubSpec<>("alpha", Alpha.class, Alpha::new, creationLog)
                        .requires(Missing.class))
                .addComponent("beta", new StubSpec<>("beta", Beta.class, Beta::new, creationLog))
                .build();
        ComponentDependencyException ex = assertThrows(ComponentDependencyException.class,
                mode::verifyDependencies);
        String message = String.valueOf(ex.getMessage());
        assertTrue(message.contains("mode-x"), "消息必须点名模式 id,实际:" + message);
        assertTrue(message.contains("alpha"), "消息必须点名组件 id,实际:" + message);
        assertTrue(message.contains(Missing.class.getName()), "消息必须点名缺的类型全名,实际:" + message);
    }

    /** 行 2(检查时机):createInstance 对坏表先抛(先 verifyDependencies 后造,路径不可绕过);对好表返回实例。 */
    @Test
    void createInstanceVerifiesFirstAndCannotBeBypassed() {
        // 坏表:校验必须发生在「造任何组件」之前——哨兵日志保持为空即证
        List<String> badLog = new ArrayList<>();
        MatchMode bad = modeBuilder("bad-mode")
                .addComponent("alpha", new StubSpec<>("alpha", Alpha.class, Alpha::new, badLog)
                        .requires(Missing.class))
                .addComponent("beta", new StubSpec<>("beta", Beta.class, Beta::new, badLog))
                .build();
        assertThrows(ComponentDependencyException.class, () -> create(bad),
                "createInstance 必须先 verifyDependencies,坏表在造实例前抛");
        assertTrue(badLog.isEmpty(), "校验先于创建:坏表不得已造出任何组件,实际造了:" + badLog);

        // 好表:返回实例且组件按声明序造出
        List<String> goodLog = new ArrayList<>();
        MatchMode good = modeBuilder("good-mode")
                .addComponent("alpha", new StubSpec<>("alpha", Alpha.class, Alpha::new, goodLog)
                        .requires(Beta.class))
                .addComponent("beta", new StubSpec<>("beta", Beta.class, Beta::new, goodLog))
                .build();
        MatchModeInstance instance = create(good);
        assertNotNull(instance, "好表返回实例");
        assertEquals(List.of("alpha", "beta"), goodLog, "组件按声明序创建——校验确实放行了装配");
        assertEquals("good-mode", instance.id().value().substring(0, "good-mode".length()),
                "实例 id 以模式 id 开头(同一张表造出的实例)");
    }
}
