package com.hydrokarbs.api.view;

import java.util.List;
import java.util.UUID;

/**
 * 会话快照:api 侧不可变 record(07 §7.1/§7.2 值对象行),字段只取会话层事实
 * (§3.1:id/名册/当前实例/状态机)。与引擎侧只读视图 {@code engine/port/SessionView}
 * (07 §7.3)职责相近但不同物——公共面只暴露本 record。
 *
 * <p>{@code members} 含断线宽限期成员(roster 全量 uuid);{@code modeId}/
 * {@code mapId} 无当前实例(含切换间隙)为<b>空串</b>;{@code state} 为状态机
 * 名字串({@code RUNNING/ENDING/SWITCHING/DISSOLVING},§3.1 四态)。
 *
 * <p><b>快照纪律</b>:转换点一次性取值、集合 {@code copyOf}(7.1 要点 1)——绝不把
 * {@code Session}/名册/实例/组件放进 record(泄漏活引用 = 跨线程读非线程安全结构,
 * §10.5)。<b>刻意不含</b> team/role——会话成员没有实例语义(14.18)。
 */
public record SessionInfo(String sessionId, String modeId, String mapId,
                          List<UUID> members, String state) {

    public SessionInfo {
        members = List.copyOf(members);
    }
}
