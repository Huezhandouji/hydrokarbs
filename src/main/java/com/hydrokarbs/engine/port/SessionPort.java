package com.hydrokarbs.engine.port;

import java.util.List;
import java.util.Optional;

import com.hydrokarbs.engine.session.DissolveReason;
import com.hydrokarbs.engine.session.Handoff;
import com.hydrokarbs.engine.session.MatchModeSelection;
import com.hydrokarbs.engine.session.SessionId;
import com.hydrokarbs.engine.session.SessionMember;

/**
 * 会话自面:会话级组件对内核的唯一入口(02-核心概念 §2.5.1)。
 *
 * <p>组件影响力的面只有两个行动口(D8,§2.8-决断四):
 * {@link #requestSwitch}/{@link #requestDissolve},不新增其他影响力端口。
 */
public interface SessionPort {

    /** 本会话 id。 */
    SessionId id();

    /** 成员只读快照(不可变列表;含宽限期内在册成员)。 */
    List<SessionMember> members();

    /** 会话已存活 tick 数(空场计时与最短驻留的同一量纲来源,与实例级 tick() 同构)。 */
    long tick();

    /** 会话事件端口:发布 / 订阅(订阅句柄随组件 stop 自动回收)。 */
    SessionEventPort events();

    /**
     * 当前实例的只读访问面(★D5)。<b>现取不缓存</b>:无实例或 {@code ENDING/SWITCHING}
     * 间隙一律 {@code empty},不得回落到旧值(14.27);经它取得的实例组件引用不得
     * 越过切换存进字段。
     */
    Optional<MatchModeInstanceAccess> currentInstance();

    /**
     * ★D8 请求模式切换:<b>与 {@code SessionService#switchMode} 同一实现</b>,
     * 受最短驻留与重入约束(§3.5)。请求口不带豁免——载荷选区中的豁免标记被剥离
     * (自动切换无豁免路径);被拒时记日志,调用方无返回面(事件总线可观察结果)。
     */
    void requestSwitch(MatchModeSelection target, Handoff payload);

    /** ★D8 请求会话解散:<b>幂等</b>;仅会话级组件与内核可调(空场策略组件的动作落点)。 */
    void requestDissolve(DissolveReason reason);

    /** 延迟任务(句柄登记到发起组件名下,stop 自动取消;量纲 = tick)。 */
    void later(int delayTicks, Runnable task);
}
