package com.hydrokarbs.engine.event;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 实例事件总线:{@link MatchModeInstanceEventPort} 实现体(§2.5/§11.1)。
 *
 * <ul>
 *   <li>类型可赋值匹配;优先级降序,同级按订阅序;</li>
 *   <li>主线程同步派发(派发中订阅者会改状态,异步派发等于数据竞争,§11.1);</li>
 *   <li>逐订阅者异常隔离:单个订阅者抛异常不中断其他订阅者,记 WARN 后继续(§11.3);</li>
 *   <li>{@link #clear()}:实例 close 时调用,组件订阅统一回收——组件自己不注销。</li>
 * </ul>
 */
public final class MatchModeInstanceEventBus implements MatchModeInstanceEventPort {

    private static final Logger LOG = Logger.getLogger(MatchModeInstanceEventBus.class.getName());

    /** 默认优先级(未指定时)。 */
    public static final int DEFAULT_PRIORITY = 0;

    /** 订阅登记项:seq 保证同优先级稳定次序(订阅序)。 */
    private record Registration<E extends MatchModeInstanceEvent>(Class<E> type, int priority, long seq,
                                                                  Consumer<E> subscriber) {
    }

    private final List<Registration<?>> registrations = new ArrayList<>();
    private long nextSeq;

    @Override
    public void publish(MatchModeInstanceEvent event) {
        if (event == null) {
            return;
        }
        for (Registration<?> registration : orderedSnapshot()) {
            if (!registration.type().isInstance(event)) {
                continue;
            }
            dispatch(registration, event);
        }
    }

    /** 单订阅者派发:isInstance 已过滤,cast 安全;异常隔离记 WARN 后继续。 */
    private <E extends MatchModeInstanceEvent> void dispatch(Registration<E> registration,
                                                             MatchModeInstanceEvent event) {
        try {
            registration.subscriber().accept(registration.type().cast(event));
        } catch (Throwable t) {
            LOG.log(Level.WARNING,
                    "Instance event subscriber threw (isolated): " + event.getClass().getName(), t);
        }
    }

    @Override
    public <E extends MatchModeInstanceEvent> void subscribe(Class<E> type, Consumer<E> subscriber) {
        subscribe(type, DEFAULT_PRIORITY, subscriber);
    }

    @Override
    public <E extends MatchModeInstanceEvent> void subscribe(Class<E> type, int priority, Consumer<E> subscriber) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(subscriber, "subscriber");
        registrations.add(new Registration<>(type, priority, nextSeq++, subscriber));
    }

    /** 清除全部订阅(实例 close 时调用;幂等)。 */
    public void clear() {
        registrations.clear();
    }

    /** 快照 + 排序:派发中订阅者再订阅/退订都不破坏本轮(写时复制口径)。 */
    private List<Registration<?>> orderedSnapshot() {
        List<Registration<?>> snapshot = List.copyOf(registrations);
        if (snapshot.size() < 2) {
            return snapshot;
        }
        List<Registration<?>> ordered = new ArrayList<>(snapshot);
        ordered.sort(Comparator.comparingInt((Registration<?> r) -> r.priority()).reversed()
                .thenComparingLong(r -> r.seq()));
        return ordered;
    }
}
