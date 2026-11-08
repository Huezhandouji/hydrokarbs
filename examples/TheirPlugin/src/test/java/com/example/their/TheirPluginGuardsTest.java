package com.example.their;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.hydrokarbs.engine.component.SessionComponent;
import com.hydrokarbs.engine.port.SessionServices;

/**
 * 消费样例守卫用例(★51 消费方侧):
 * <ul>
 *   <li>★46 低版本拒载已随 API 版本协商面退场悬置(★v1.14/32 号)——api 缺席
 *       拒载仍在 onEnable 首行(拒载路径本体归 runServer 冒烟);</li>
 *   <li>坏贡献(★51 消费方侧形状):贡献「无人供给依赖」组件的描述符在框架收录闸
 *       响亮抛(onEnable 失败 = 拒载信号;行为本体由框架侧测试承载,样例示范形状);
 *       同时断言合法贡献声明。</li>
 * </ul>
 * <p>零 Bukkit 静态调用(★45:样例仅依赖白名单)。
 */
class TheirPluginGuardsTest {

    // ── ★51 消费方侧:合法贡献声明 + 坏贡献形状(★v1.6 显式注册口径) ──

    @Test
    void declaredContributionSpecsAreNonEmptyAndWellTyped() {
        List<SessionComponent.Specification<?>> specs = TheirPlugin.SESSION_COMPONENT_SPECS;
        assertFalse(specs.isEmpty(),
                "贡献声明非空(D14 通道一显式注册;空贡献表无收录意义)");
        assertEquals(TheirSessionComponent.class, specs.get(0).providedType(),
                "贡献元素供给本样例组件类型(组件 id 由供给面类型名推导)");
    }

    @Test
    void brokenContributionShapeIsExpressibleAndFrameworkOwesLoudRejection() {
        // 坏贡献:声明依赖一个无人供给的组件类型——框架收录闸干跑校验
        // (组件 id 查重/必需依赖)会响亮抛给调用方(★v1.6 显式注册可捕获;★51)。
        List<SessionComponent.Specification<?>> broken = List.<SessionComponent.Specification<?>>of(
                new BrokenSpec().requires(UnresolvedSessionComponent.class));

        assertEquals(1, broken.size(), "坏贡献形状可表达");
        assertEquals(List.of(UnresolvedSessionComponent.class),
                broken.get(0).freeze().requiredTypes(),
                "坏声明 = 带无人供给的必需依赖(框架侧干跑校验的拒绝输入)");
        // 拒绝行为本体:框架侧 SessionComponentContributionTest#badContributionFailsLoudly...
    }

    /** 坏贡献描述符(泛型实参可解析,freeze 口径与真实声明一致)。 */
    private static final class BrokenSpec
            extends SessionComponent.Specification<UnresolvedSessionComponent> {

        BrokenSpec() {
            super("their-broken-contribution");
        }

        @Override
        public UnresolvedSessionComponent create(String id, SessionServices svc) {
            throw new UnsupportedOperationException("坏贡献不会被装配(注册闸在先响亮拒)");
        }
    }

    /** 无人供给的会话组件类型(坏声明的依赖目标)。 */
    private abstract static class UnresolvedSessionComponent extends SessionComponent {

        UnresolvedSessionComponent(String id, SessionServices svc) {
            super(id, svc);
        }
    }
}
