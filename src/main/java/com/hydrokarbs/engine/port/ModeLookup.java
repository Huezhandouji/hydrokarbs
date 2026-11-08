package com.hydrokarbs.engine.port;

import java.util.Collection;

import com.hydrokarbs.engine.mode.MatchMode;

/**
 * 模式查询窄口:切换准备阶段对注册表的唯一依赖面(03-会话与模式切换 §3.2-A1)。
 *
 * <p>从 {@link com.hydrokarbs.engine.mode.MatchModeRegistry} 提取两读口为接口;
 * 切换实现面向本口编程(离线测试可注入假实现);注册表本体(双发现/注册闸)不在此面。
 */
public interface ModeLookup {

    /** 按 id 取模式;缺失响亮抛(切换准备「目标不存在」拒绝的上游)。 */
    MatchMode require(String modeId);

    /** 全部已注册模式(声明序,不可变快照;命令建议与切换诊断消费)。 */
    Collection<MatchMode> all();
}
