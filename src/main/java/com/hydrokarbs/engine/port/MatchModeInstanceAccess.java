package com.hydrokarbs.engine.port;

import java.util.List;

import com.hydrokarbs.engine.component.MatchModeComponent;
import com.hydrokarbs.engine.instance.MatchModeInstanceId;
import com.hydrokarbs.engine.instance.Participant;

/**
 * 会话侧只读实例面(★v4-D5;02-核心概念 §2.5.1;无写口)。
 *
 * <p>会话级组件经 {@code SessionPort#currentInstance()} 现取本面,
 * 读当前实例的运行数据。
 *
 * <p><b>只读是架构纪律</b>(§2.8-决断四):<b>不得缓存实例组件引用越过切换</b>
 * ——现取现用,切走后旧实例已 close,悬空引用即病。跨级干预只走
 * {@code SessionPort#requestSwitch}/{@code SessionPort#requestDissolve}。
 */
public interface MatchModeInstanceAccess {

    /** 实例 id。 */
    MatchModeInstanceId id();

    /** 实例运行的模式 id。 */
    String modeId();

    /** 实例已存活 tick 数。 */
    long tick();

    /** 参赛者只读快照(不可变列表)。 */
    List<Participant> participants();

    /** 模式模板只读信息(id/名称/描述/图标/tags/meta)。 */
    MatchModeInfoPort modeInfo();

    /** 按类型取<b>声明序第一个</b>可赋值实例组件;无 → null。 */
    <T extends MatchModeComponent> T component(Class<T> type);

    /** 按类型取全部可赋值实例组件(声明序)。 */
    <T extends MatchModeComponent> List<T> components(Class<T> type);
}
