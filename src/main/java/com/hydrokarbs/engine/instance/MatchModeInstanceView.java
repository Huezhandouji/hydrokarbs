package com.hydrokarbs.engine.instance;

import com.hydrokarbs.engine.component.MatchModeComponent;
import com.hydrokarbs.engine.port.SessionView;
import com.hydrokarbs.engine.session.MatchModeConfig;
import com.hydrokarbs.map.MapDefinition;

/**
 * 实例只读读口:内容侧(判定器等)消费实例数据的最小面。
 *
 * <p>由实例容器的内部实现体承载,经 {@code MatchModeInstance#view()} 暴露;
 * 方法面随消费方只增不改。{@code component(Class)} 未命中返回 null
 * (与组件查找端口同口径)。{@code session()} 返回只读视图
 * {@link SessionView}(与 {@code MatchModeInstancePort#session()} 同型)。
 */
public interface MatchModeInstanceView {

    /** 按类型取第一个可赋值组件;未注册返回 null(不抛)。 */
    <T extends MatchModeComponent> T component(Class<T> type);

    /** 本次运行的模式参数(只读配置视图)。 */
    MatchModeConfig cfg();

    /** 本次运行的地图。 */
    MapDefinition map();

    /** 实例已存活 tick 数。 */
    long tick();

    /** 所属会话的只读视图(跨级只读)。 */
    SessionView session();
}
