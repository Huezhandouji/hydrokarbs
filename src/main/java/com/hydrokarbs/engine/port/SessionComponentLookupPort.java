package com.hydrokarbs.engine.port;

import java.util.List;
import java.util.Optional;

import com.hydrokarbs.engine.component.SessionComponent;

/**
 * 会话内组件查找端口:按类型 / 按 id(02-核心概念 §2.5.1)。
 *
 * <p>会话组件协作的唯一取物口(要协作就按类型取);实现是每会话一份的
 * {@code SessionComponentRegistry}(声明序),同一会话内恒返回同一实例,跨会话互不可见。
 */
public interface SessionComponentLookupPort {

    /** 按类型取<b>声明序第一个</b>可赋值组件;未注册返回 null(不抛)。 */
    <T extends SessionComponent> T get(Class<T> type);

    /** 按类型取全部可赋值组件(声明序);无符合者返回空列表。 */
    <T extends SessionComponent> List<T> getAll(Class<T> type);

    /** 按 id 取组件(装配时绑入的 id);不存在返回 empty。 */
    Optional<SessionComponent> byId(String id);
}
