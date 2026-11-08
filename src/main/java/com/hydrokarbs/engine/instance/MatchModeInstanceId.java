package com.hydrokarbs.engine.instance;

/**
 * 实例 id(值对象;§12.1)。
 *
 * <p>由容器在构造期生成(模式 id + 进程内序号),进程内唯一;
 * 供日志、诊断与「按 id 现取」类查询使用。
 */
public record MatchModeInstanceId(String value) {
}
