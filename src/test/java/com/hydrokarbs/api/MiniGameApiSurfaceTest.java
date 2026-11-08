package com.hydrokarbs.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

/**
 * 公共 API 冻结(★19;07 §7.2):反射冻结 17 方法集,归一为
 * {@code "name(参数类型列表):返回类型"} 规范串集合与清单常量<b>全等</b>——
 * 任何删/改签名即红,「只增不改」。
 *
 * <p>运营细则:新增方法 = 追加清单 + 单独说明动机与消费者;改签名 = 视同删+增,
 * 已有调用方同步改;删除先标 {@code @Deprecated} 一个发布周期;record 加字段
 * 无损但可在清单外补分量断言。
 *
 * <p>泛型实参经反射不可区分,由 {@code MiniGameApiImplTest} 补断言;战绩两方法
 * 随 ★v1.3/R1 悬置不在清单。各版本方法数沿革见 07 §7.2 与 22/24/25/26/31 号
 * 修正案;API_VERSION 常量与版本协商面已随 ★v1.14/32 号退场。
 */
class MiniGameApiSurfaceTest {

    /** v6 冻结清单(17 行对 17 法;v1 面 7 行逐字节冻结,后 10 行 = 7.5/★v1.6 增量)。 */
    private static final Set<String> FROZEN = Set.of(
            // ── v1 面(7.1,2026-10-25;8 行 → ★v1.4 删两参 createSession 余 7 行,
            //    签名一字不动) ──────────────────────────────────────────────
            "sessionOf(java.util.UUID):java.util.Optional",                        // 查询
            "session(java.lang.String):java.util.Optional",                        // 查询
            "sessions():java.util.List",                                           // 查询
            "switchMode(java.lang.String,com.hydrokarbs.engine.session.MatchModeSelection,"
                    + "com.hydrokarbs.engine.session.Handoff):java.util.concurrent.CompletableFuture",   // 控制
            "modes():java.util.List",                                              // 目录
            "maps():java.util.List",                                               // 目录
            "mode(java.lang.String):java.util.Optional",                           // 目录
            // ── 7.5 增量(v1.1 v2 面 + ★v1.2 v3 面;07 §7.1/19 §3/20 §2;
            //    ★v1.7 删 D4 能力标签 2 行) ──
            "joinSession(java.lang.String,java.util.UUID):"
                    + "com.hydrokarbs.engine.session.JoinResult",                  // D2 成员(★42)
            "leaveSession(java.util.UUID,com.hydrokarbs.engine.session.LeaveReason):void",   // D2 成员(幂等)
            "subscribeSessionEvents(java.util.function.Consumer):"
                    + "com.hydrokarbs.api.EventSubscription",                      // D3 订阅(★43)
            "createSession(com.hydrokarbs.api.SessionRecipe):"
                    + "com.hydrokarbs.api.view.SessionInfo",                       // D14 通道二(★52)
            "registerMap(org.bukkit.plugin.Plugin,com.hydrokarbs.map.MapDefinition):"
                    + "com.hydrokarbs.api.view.MapInfo",                           // ★v1.2 地图注册(★54)
            "unregisterMap(org.bukkit.plugin.Plugin,java.lang.String):void",       // ★v1.2 地图摘除
            // ── ★v1.6 增量(显式注册收敛;24 号修正案,ServicesManager 提供方通道退场) ──
            "registerMode(org.bukkit.plugin.Plugin,"
                    + "com.hydrokarbs.engine.mode.MatchModeProvider):"
                    + "com.hydrokarbs.api.view.MatchModeInfo",                     // 模式显式注册
            "unregisterMode(org.bukkit.plugin.Plugin,java.lang.String):void",      // 模式摘除(回退编排)
            "registerSessionComponents(org.bukkit.plugin.Plugin,java.util.List):void",   // D14 通道一显式面
            "unregisterSessionComponents(org.bukkit.plugin.Plugin):void");         // 贡献摘除(幂等)

    @Test
    void methodSetEqualsFrozenSurface() {
        Set<String> actual = Arrays.stream(MiniGameApi.class.getDeclaredMethods())
                .map(MiniGameApiSurfaceTest::signature)
                .collect(Collectors.toSet());
        assertEquals(FROZEN, actual,
                "MiniGameApi 方法集与冻结清单不符——「只增不改」(07-公共API §7.2;★19)。"
                        + "新增 = 追加清单并单独 PR 说明动机与消费者;删/改签名 = 破坏冻结面,驳回归位");
    }

    /** 归一化:方法名(参数类型逗号串,声明序):返回类型(全 Class#getName)。 */
    private static String signature(Method method) {
        String params = Arrays.stream(method.getParameterTypes())
                .map(Class::getName)
                .collect(Collectors.joining(","));
        return method.getName() + "(" + params + "):" + method.getReturnType().getName();
    }
}
