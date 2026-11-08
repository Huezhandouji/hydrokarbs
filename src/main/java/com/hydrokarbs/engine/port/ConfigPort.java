package com.hydrokarbs.engine.port;

import java.util.Optional;

import com.hydrokarbs.engine.session.MatchModeConfig;
import com.hydrokarbs.map.MapDefinition;

/**
 * 配置端口:模式配置与地图声明的统一取物口(最小落点)。
 *
 * <p>成员按消费方(会话装配/切换准备)扩充、只增不改;
 * 扩充须过设计审查(与 svc 三成员同一纪律)。实现体归 L1 适配层。
 */
public interface ConfigPort {

    /** 取指定模式的配置视图({@code modes/<id>.yml});无该模式配置返回 empty。 */
    Optional<MatchModeConfig> modeConfig(String modeId);

    /** 取指定 id 的地图声明;不存在返回 empty。 */
    Optional<MapDefinition> map(String mapId);
}
