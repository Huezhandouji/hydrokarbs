package com.hydrokarbs.engine.session;

import java.util.Optional;

/**
 * 目标模式选择:切换指令的「下一站」描述(03-会话与模式切换 §3.2)。
 * mapId 为空 → 按 mode 支持的 tag 经地图挑选策略挑一张;引擎对「下一站」零理解,
 * 投票/轮换/管理员指定只是产出本类型的不同上游。force 显式豁免人数窗下限等防护
 * (调用方显式选择,走 review 条款)。
 */
public record MatchModeSelection(String modeId, Optional<String> mapId, boolean force) {

    /** 单参工厂:只指定模式,不带地图、不带豁免。 */
    public static MatchModeSelection of(String modeId) {
        return new MatchModeSelection(modeId, Optional.empty(), false);
    }
}
