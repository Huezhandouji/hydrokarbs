package com.hydrokarbs.engine.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.hydrokarbs.engine.instance.Participant;
import com.hydrokarbs.engine.session.EndResult;
import com.hydrokarbs.engine.session.Handoff;
import com.hydrokarbs.engine.session.HandoffView;
import com.hydrokarbs.engine.session.MatchModeConfig;
import com.hydrokarbs.engine.session.MatchModeSelection;
import com.hydrokarbs.engine.event.MatchModeInstanceEventPort;
import com.hydrokarbs.map.MapDefinition;

/**
 * 实例自面:框架「基础端口」的落点(02-核心概念 §2.5;结束/切换都从这里走)。
 * 实现体是实例容器(self 端口组,捕获容器自身)。
 *
 * <p>★v4 唯一修订:新增 {@link #session()}(我所属会话的只读视图,§2.8-决断四)
 * ——只增该方法,其余签名不动。
 */
public interface MatchModeInstancePort {

    /** 本实例的参赛者快照(声明序无关;返回不可变列表)。 */
    List<Participant> participants();

    /** 按 uuid 取参赛者;不在本实例返回 empty。 */
    Optional<Participant> participant(UUID uuid);

    /** 实例已存活 tick 数。 */
    long tick();

    /** 入口交接载荷(只读)。 */
    HandoffView handoff();

    /** 本次运行的地图。 */
    MapDefinition map();

    /** 本次运行的模式参数(只读配置视图)。 */
    MatchModeConfig cfg();

    /** 实例事件端口:发布 / 订阅(订阅随组件 stop 自动回收)。 */
    MatchModeInstanceEventPort events();

    /** 延迟任务(句柄登记到发起组件名下,stop 自动取消)。 */
    void later(int delayTicks, Runnable task);

    /**
     * ★ 请求本实例结束。实例进入 ENDING,ticker 下一 tick 处置(不立即清场,§3.1)。
     * 结束是组件的主动声明,不被框架轮询。
     */
    void end(EndResult result);

    /** ★ 请求模式切换(等价会话服务切换方法的组件侧口;受最短驻留/重入约束)。 */
    void switchTo(MatchModeSelection target, Handoff payload);

    /** ★v4 新增:我所属会话的只读视图(会话级组件的只读查询入口;§2.5.1、§2.8-决断四)。 */
    SessionView session();
}
