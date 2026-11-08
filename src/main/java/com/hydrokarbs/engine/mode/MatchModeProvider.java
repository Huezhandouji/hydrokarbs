package com.hydrokarbs.engine.mode;

import com.hydrokarbs.engine.session.MatchModeConfig;

/**
 * 模式 SPI 工厂接口(07-公共API §7.2;13-扩展步骤 §13.4):注册工厂而非成品。
 * 聚合根 {@link MatchMode} 是 final 类,Builder 即声明语言,本接口即 SPI;
 * 第三方经 {@code MiniGameApi#registerMode} 注册(★v1.6),框架 build()
 * (内含注册前闸 verifyDependencies,坏声明进不了注册表,§2.6)。
 * 参照实现:{@code examples/TheirPlugin}。
 *
 * <p><b>A9 三限制(§0.2-A9)</b>:build() 后不可变;不可追加/改组件表;要变体就注册新 id。
 *
 * <p><b>依赖白名单(§13.4)</b>:① {@code com.hydrokarbs.api};② engine 契约
 * ({@code engine.mode}/{@code engine.session} 值类型、两级组件基类);
 * ③ L3 组件公开 {@code Specification}。<b>组件抛异常只隔离自己的实例</b>(§3.1)。
 */
public interface MatchModeProvider {

    /** 模式 id(注册表唯一键)。 */
    String id();

    /** 用给定配置造聚合根(每模式一份 {@code modes/<id>.yml} 视图)。 */
    MatchMode build(MatchModeConfig cfg);
}
