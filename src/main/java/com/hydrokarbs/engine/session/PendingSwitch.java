package com.hydrokarbs.engine.session;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.hydrokarbs.engine.mode.MatchMode;
import com.hydrokarbs.engine.port.WorldHandle;
import com.hydrokarbs.map.MapDefinition;

/**
 * 挂起执行计划:阶段 A 完成与阶段 B 执行之间的<b>唯一交接物</b>(§3.2)。
 *
 * <p><b>字段封闭</b>:全部字段在挂起时刻冻结——B 段从计划执行,<b>不再回查注册表</b>
 * (唯一例外:执行前的人数重校验,防护③,§3.5)。
 *
 * <p><b>两阶段生命周期</b>:同步段先造计划并挂上会话(占位守卫:阻止切换窗口内的
 * 并发请求);世界准备成功回调填入 {@link #world} 并置 {@link #worldReady},
 * 此后下一 tick 才执行 B 段;失败/超时/作废时 future 以拒绝完结。
 * 包级可见,不进公共 API 面。
 */
final class PendingSwitch {

    /** 调用方的切换 future:完成点收敛在节拍器 B 段(成功)或主线程执行器(拒绝),绝不在异步线程。 */
    final CompletableFuture<SwitchResult> future;

    /** 目标模式(A1 冻结)。 */
    final MatchMode mode;

    /** 目标地图(A3 冻结)。 */
    final MapDefinition map;

    /** 目标模式的只读配置视图。 */
    final MatchModeConfig cfg;

    /** 外部交接载荷(B3 与旧实例自报合并,external 覆盖)。 */
    final Handoff payload;

    /** 目标选区(A 段裁决的输入快照)。 */
    final MatchModeSelection selection;

    /** 触发原因(外部调用 = nowhere;自动切换 = 结束请求原样透传)。 */
    final EndResult cause;

    /** 发起时的会话 tick 计数(诊断与防护量纲基准)。 */
    final long initiatedAtTick;

    /** 世界句柄(A6 成功回调填入;此前为 null)。 */
    WorldHandle world;

    /** A6 是否已就绪(B 段执行条件)。 */
    volatile boolean worldReady;

    /** 是否被作废(会话解散/竞态失败;作废后 B 段不再执行)。 */
    volatile boolean aborted;

    PendingSwitch(CompletableFuture<SwitchResult> future, MatchMode mode, MapDefinition map,
                  MatchModeConfig cfg, Handoff payload, MatchModeSelection selection,
                  EndResult cause, long initiatedAtTick) {
        this.future = future;
        this.mode = mode;
        this.map = map;
        this.cfg = cfg;
        this.payload = payload;
        this.selection = selection;
        this.cause = cause;
        this.initiatedAtTick = initiatedAtTick;
    }

    /** 是否带豁免标记(防护①③的豁免口径:人数下限与最短驻留)。 */
    boolean forced() {
        return selection.force();
    }

    /** 诊断用目标描述。 */
    String describe() {
        return mode.id() + Optional.ofNullable(selection.mapId().orElse(null))
                .map(id -> "@" + id).orElse("");
    }
}
