package com.hydrokarbs.engine.session;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 连接追踪:断线宽限的记账员(03-会话与模式切换 §3.1;默认 60s,§15.2
 * {@code disconnect-grace-seconds})。只管记账/销账/到期清点,名册增删的执行权
 * 在会话编排(到期者由其调 {@link SessionRoster#remove(UUID)} 除名)。
 * 不自起任务(14 §14.6):清点由 {@link Session#idleTimers(long)} 每 tick 调
 * {@link #sweep(long)} 驱动。
 */
public final class ConnectionTracker {

    /** 默认宽限毫秒(§15.2 {@code disconnect-grace-seconds: 60})。 */
    public static final long DEFAULT_GRACE_MILLIS = 60_000L;

    private final long graceMillis;

    /** 断线记账:uuid → 断线时刻毫秒(LinkedHashMap 保插入序,清点结果稳定)。 */
    private final Map<UUID, Long> disconnectedAt = new LinkedHashMap<>();

    public ConnectionTracker() {
        this(DEFAULT_GRACE_MILLIS);
    }

    public ConnectionTracker(long graceMillis) {
        if (graceMillis < 0) {
            throw new IllegalArgumentException("宽限毫秒不得为负: " + graceMillis);
        }
        this.graceMillis = graceMillis;
    }

    /** 宽限毫秒(只读)。 */
    public long graceMillis() {
        return graceMillis;
    }

    /** 断线记账(重连后再断线覆盖为最新时刻)。 */
    public void onDisconnect(UUID uuid, long nowMillis) {
        Objects.requireNonNull(uuid, "uuid");
        disconnectedAt.put(uuid, nowMillis);
    }

    /** 重连销账(此后清点不再返回该成员;未记账为 no-op)。 */
    public void onReconnect(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        disconnectedAt.remove(uuid);
    }

    /**
     * 清点:返回宽限<b>已过</b>的成员并销账(nowMillis - 断线时刻 &gt;= graceMillis
     * 即算已过——到点即宽限耗尽;扫过即出账,同一批不重复返回)。
     * 各成员独立判定,互不影响。
     */
    public List<UUID> sweep(long nowMillis) {
        List<UUID> expired = new ArrayList<>();
        Iterator<Map.Entry<UUID, Long>> it = disconnectedAt.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            if (nowMillis - entry.getValue() >= graceMillis) {
                expired.add(entry.getKey());
                it.remove();
            }
        }
        return expired;
    }
}
