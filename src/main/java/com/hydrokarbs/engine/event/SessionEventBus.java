package com.hydrokarbs.engine.event;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 会话事件总线(§11.1):订阅返回句柄可显式回收——服务与跨切面订阅者
 * 在自身 stop 中注销(D5 通道①)。
 *
 * <ul>
 *   <li>类型可赋值匹配;优先级降序,同级按订阅序;句柄 close 注销(幂等);</li>
 *   <li>同线程同步派发(派发中订阅者会改状态,异步派发等于数据竞争,§11.1);</li>
 *   <li>逐订阅者异常隔离:单个订阅者抛异常不中断其他订阅者,记 WARN 后继续(§11.3);</li>
 *   <li>{@link #clear()}:会话解散时调用;{@link #setMirror} 全局汇聚镜像,默认关闭。</li>
 * </ul>
 *
 * <p>内部总线,不进 Bukkit 总线(§15.1)。
 */
public final class SessionEventBus {

    private static final Logger LOG = Logger.getLogger(SessionEventBus.class.getName());

    /** 默认优先级(未指定时)。 */
    public static final int DEFAULT_PRIORITY = 0;

    /** 订阅句柄:close 注销该订阅(幂等;不抛)。 */
    public interface Subscription extends AutoCloseable {
        /** 注销本订阅;重复 close 无害。 */
        @Override
        void close();
    }

    /** 订阅登记项:seq 保证同优先级稳定次序(订阅序)。 */
    private record Registration<E extends SessionEvent>(Class<E> type, int priority, long seq,
                                                        Consumer<E> subscriber) {
    }

    private final List<Registration<?>> registrations = new ArrayList<>();
    private long nextSeq;

    /**
     * 全局汇聚镜像:非 null 时 {@link #publish} 先把事件同步递给镜像消费者
     * (全局汇聚总线的数据源),再走本会话订阅派发;「加性」点,
     * 不改派发序与订阅语义。
     *
     * <p>红线:镜像异常不得影响原总线派发(try/catch 隔离记 WARN);
     * 默认 null,可重设/可置 null,装配点一处挂接。
     */
    private volatile Consumer<SessionEvent> mirror;

    /** 挂全局汇聚镜像(幂等可重设;null = 关闭镜像)。装配点一处挂接。 */
    public void setMirror(Consumer<SessionEvent> mirror) {
        this.mirror = mirror;
    }

    /** 发布事件:同步派发给全部类型可赋值的订阅者(优先级降序、同级按订阅序);镜像先于订阅派发。 */
    public void publish(SessionEvent event) {
        if (event == null) {
            return;
        }
        Consumer<SessionEvent> sink = mirror;
        if (sink != null) {
            try {
                sink.accept(event);     // 镜像先于订阅派发(汇聚面看到的事件序 = 发布序);异常隔离
            } catch (Throwable t) {
                LOG.log(Level.WARNING, "Session event mirror threw (isolated)", t);
            }
        }
        for (Registration<?> registration : orderedSnapshot()) {
            if (!registration.type().isInstance(event)) {
                continue;
            }
            dispatch(registration, event);
        }
    }

    /** 单订阅者派发:isInstance 已过滤,cast 安全;异常隔离记 WARN 后继续。 */
    private <E extends SessionEvent> void dispatch(Registration<E> registration, SessionEvent event) {
        try {
            registration.subscriber().accept(registration.type().cast(event));
        } catch (Throwable t) {
            LOG.log(Level.WARNING,
                    "Session event subscriber threw (isolated): " + event.getClass().getName(), t);
        }
    }

    /** 按事件类型订阅(默认优先级);返回可显式回收的句柄。 */
    public <E extends SessionEvent> Subscription subscribe(Class<E> type, Consumer<E> subscriber) {
        return subscribe(type, DEFAULT_PRIORITY, subscriber);
    }

    /** 按事件类型 + 优先级订阅(优先级降序,同级按订阅序);返回可显式回收的句柄。 */
    public <E extends SessionEvent> Subscription subscribe(Class<E> type, int priority, Consumer<E> subscriber) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(subscriber, "subscriber");
        Registration<E> registration = new Registration<>(type, priority, nextSeq++, subscriber);
        registrations.add(registration);
        return () -> unsubscribe(registration);
    }

    /** 清除全部订阅(会话终结时调用;幂等)。 */
    public void clear() {
        registrations.clear();
    }

    /** 登记项自注销口(句柄目标):从登记表移除本登记,幂等。 */
    private void unsubscribe(Registration<?> registration) {
        registrations.remove(registration);
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
