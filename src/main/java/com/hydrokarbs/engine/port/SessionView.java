package com.hydrokarbs.engine.port;

import java.util.List;

import com.hydrokarbs.engine.component.SessionComponent;
import com.hydrokarbs.engine.session.SessionId;
import com.hydrokarbs.engine.session.SessionMember;

/**
 * 实例侧只读会话视图(02-核心概念 §2.5.1;挂在 {@link MatchModeInstancePort#session()} 上)。
 *
 * <p>模式接入会话能力的<b>软接入</b>口(§13.7):按类型读会话组件;
 * 硬依赖同样以类型表达(组件 {@code requires/requiresOptional} + 装配闸)。
 *
 * <p><b>只读是架构纪律</b>(§2.8-决断四):实例组件不得写入会话组件;
 * <b>本视图无 {@code events()}</b>——实例组件不得订阅会话事件(未决 §11-Q7)。
 */
public interface SessionView {

    /** 所属会话 id。 */
    SessionId id();

    /** 成员只读快照(不可变列表)。 */
    List<SessionMember> members();

    /** 按类型取<b>声明序第一个</b>可赋值会话组件;无 → null(★模式接入会话能力)。 */
    <T extends SessionComponent> T component(Class<T> type);

    /** 按类型取全部可赋值会话组件(声明序)。 */
    <T extends SessionComponent> List<T> components(Class<T> type);
}
