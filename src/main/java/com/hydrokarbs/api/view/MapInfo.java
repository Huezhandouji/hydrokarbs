package com.hydrokarbs.api.view;

import java.util.Set;

/**
 * 地图快照:api 侧不可变 record(07 §7.1/§7.2 值对象行;数据源 = 纯静态
 * {@code MapDefinition},20-D1)。★v1.8 视图收缩:只留框架有消费面的字段——
 * {@code id} = 声明目录名(路径派生);{@code tags} 取声明唯一读口(缺省空集);
 * {@code resetStrategy} 存声明枚举的 {@code name()}(枚举不进冻结面,§0.5.3)。
 */
public record MapInfo(String id, Set<String> tags, String resetStrategy) {

    public MapInfo {
        tags = Set.copyOf(tags);
    }
}
