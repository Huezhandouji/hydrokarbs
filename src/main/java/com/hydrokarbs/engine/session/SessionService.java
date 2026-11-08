package com.hydrokarbs.engine.session;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 会话服务:会话的创建/查询/切换/进出/解散面(03-会话与模式切换 §3.2 逐方法)。
 *
 * <p><b>切换的挂起-驱动模型</b>(§16.4-三律①):同步段(A1~A5 与防护闸)内存
 * 只读检查,世界准备(A6)异步;A 完成后不在当前调用栈执行 B,而是挂起执行计划、
 * 由节拍器下一 tick 驱动(事件派发栈内不发切换)。future 完成点收敛在节拍器 B 段
 * (成功)或主线程执行器(拒绝),绝不在异步线程,永不悬挂。
 *
 * <p>线程契约:全部方法主线程调用。
 */
public interface SessionService {

    /** 创建会话(建骨架 → 发创建事件 → 走首次切换装配初始模式;重复 id 响亮抛)。 */
    Session create(SessionSpec spec);

    /** 玩家 → 所属会话(名册查询;未入任何会话返回 empty)。 */
    Optional<Session> sessionOf(UUID player);

    /** 按 id 取会话(只读查询)。 */
    Optional<Session> session(SessionId id);

    /** 全部存活会话(只读快照;节拍器每 tick 遍历它)。 */
    Collection<Session> all();

    /**
     * ★ 框架提供的「切换模式」一等方法(两阶段算法,§3.2):
     * 准备阶段可异步、对旧实例零副作用;执行阶段节拍器驱动、主线程单 tick 原子。
     * 失败三级回退(目标装配失败 → 回退模式 → 解散),绝不停在中间态;
     * 原请求以 {@link SwitchResult.Rejected} 完结,最终去向经会话事件告知。
     */
    CompletableFuture<SwitchResult> switchMode(SessionId id, MatchModeSelection target, Handoff payload);

    /** 名册登记 + 实例入场裁决(经入场裁决端口,§3.2.1;REJECT 不收、名册不登记)。 */
    JoinResult join(SessionId id, UUID player);

    /** 成员离开(断线走宽限标记,被踢立即移除;未入会话为 no-op)。 */
    void leave(UUID player, LeaveReason reason);

    /** 解散会话(幂等;关闭实例 → 成员传送 → 兜底清态 → 摘除 → 发解散事件)。 */
    void dissolve(SessionId id, DissolveReason reason);
}
