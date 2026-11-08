package com.hydrokarbs.component.phase;

import com.hydrokarbs.engine.event.MatchModeInstanceEvent;

/**
 * 阶段转场事件:由 {@link PhaseComponent} 在每次转场时发布(12-代码骨架 §12.2)。
 *
 * <p>事件词汇跟发布者走:本事件住 component/phase,不进 engine/event,
 * 引擎事件族保持零内容词汇。时间字段由发布方从实例取值,不各自取时钟。
 *
 * @param fromPhaseId  转场前阶段 id;进入首阶段时无前驱,为 null
 * @param toPhaseId    转场后阶段 id
 * @param elapsedTicks 在前驱阶段实际停留的 tick 数;进入首阶段时为 0
 */
public record PhaseChanged(String fromPhaseId, String toPhaseId, long elapsedTicks,
                           int tick, long occurredAtMillis) implements MatchModeInstanceEvent {
}
