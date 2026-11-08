package com.hydrokarbs.engine.port;

import java.util.Objects;

/**
 * 引擎服务集:{@code MatchMode#createInstance} 的末位形参(02-核心概念 §2.3)。
 *
 * <p>按最小装配需要定义(仅平台端口——实例侧延迟任务等调度口经它注入);
 * 成员按消费方(容器/会话)扩充、只增不改,扩充须过设计审查。
 */
public record EngineServices(PlatformPort platform) {

    public EngineServices {
        Objects.requireNonNull(platform, "platform");
    }
}
