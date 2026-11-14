package com.hydrokarbs.api;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.hydrokarbs.api.view.SessionInfo;
import com.hydrokarbs.engine.session.Handoff;
import com.hydrokarbs.engine.session.JoinResult;
import com.hydrokarbs.engine.session.LeaveReason;
import com.hydrokarbs.engine.session.MatchModeSelection;
import com.hydrokarbs.engine.session.SwitchResult;

/**
 * 会话控制句柄(★v1.15/33 号;ADR-0021):单个会话的控制与信息面——原
 * {@code HydrokarbsApi} 上 {@code switchMode(sessionId,…)}/{@code joinSession}/
 * {@code leaveSession} 三法的会话侧形态(冻结面合法重录,07 §7.2)。
 *
 * <p><b>不可变定位器</b>:句柄只持会话 id 与委托,不持活 {@code Session}/名册/实例
 * 引用——每次操作现解析目标会话(「现取不缓存」纪律向控制面的延伸,14.31 同款);
 * 会话消亡后句柄不失效为错误,而是走各方法注记的失效语义。经
 * {@code HydrokarbsApi#sessionOf/session/sessions/createSession} 解析获得。
 *
 * <p><b>组合式 SessionInfo</b>(33 号裁决一):快照 record 原样保留,句柄上唯一
 * 信息口 {@link #info()}——不把快照分量复制成句柄方法(一份不写两处)。
 *
 * <p><b>线程契约</b>(§10.5/§14.1):除 {@code switchMode} 外均主线程调用
 * (同步返回,结构非线程安全);{@code switchMode} 任意线程发起、Impl 回主线程、
 * future 在主线程完成——回调里做玩家操作请自行再回主线程。
 */
public interface SessionHandle {

    /** 本句柄定位的会话 id(纯值,会话消亡后仍可读)。 */
    String sessionId();

    /**
     * 现取会话快照(「现取不缓存」):会话已消亡 → {@code empty}。主线程调用。
     * <p>快照即 {@link SessionInfo} record(字段口径见其接口注:members 含宽限期
     * 成员、无实例时 modeId/mapId 空串)。
     */
    Optional<SessionInfo> info();

    /**
     * 将玩家加入本会话——{@code SessionService#join} 的受控暴露(§3.2;含
     * {@code JoinAdmission} 裁决)。引擎既有裁决原样透传:会话不存在(已消亡)/
     * 正在解散 / 入场裁决拒绝 → {@code JoinResult.Rejected}(detail 人读;调用方
     * 兜底提示);成员已在册 → 幂等重入(§3.2)。主线程调用。
     */
    JoinResult join(UUID player);

    /**
     * 将玩家移出本会话——<b>会话域守卫</b>(33 号裁决二):仅当玩家现属会话 ==
     * 本句柄会话才委托 {@code SessionService#leave},否则 no-op;主线程内查后调,
     * 无竞态。玩家不在任何会话 = no-op(幂等,D2)。{@code reason} 决定离开语义
     * (QUIT = 断线宽限在册 / KICK = 立即移除,§3.2)。主线程调用。
     */
    void leave(UUID player, LeaveReason reason);

    /**
     * 切换本会话的模式——{@code SessionService#switchMode} 的受控暴露(§3.2)。
     * <p>失败语义:会话已消亡 → future 完成 {@code Rejected(MODE_NOT_FOUND)}
     * (detail 点名会话 id);其余拒绝经引擎回退管线原样透传;null target →
     * future 异常完成;future 永不悬挂(§3.2)。任意线程发起,Impl 回主线程、
     * future 在主线程完成(§14.1)——回调里做玩家操作请自行再回主线程。
     */
    CompletableFuture<SwitchResult> switchMode(MatchModeSelection target, Handoff payload);
}
