package com.hydrokarbs.engine.session;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.hydrokarbs.engine.instance.Participant;

/**
 * 会话名册:成员的可变登记簿,「原地位移」的位移主体(§3.1)。
 *
 * <p>名册是玩家进服/退服/被踢的唯一登记处。原地位移是<b>对象恒等</b>、不是数据拷贝:
 * 内部 map 为 final,切换前后名册同一实例、成员集合恒等(含 DISCONNECTED 成员)——
 * 任何「切换时重建名册」的写法直接测试红。位移的另一半在实例侧:
 * {@link #toParticipants()} 每局新造参赛者视图,名册不动。
 *
 * <p>写点收敛:成员变更只有本类四个变更口(join/remove/markDisconnected/
 * markReconnected),外部一律经 {@link #all()} 只读快照,不得绕过名册改成员。
 */
public final class SessionRoster {

    /** 在册成员(加入序 = 声明序,遍历稳定)。 */
    private final LinkedHashMap<UUID, SessionMember> members = new LinkedHashMap<>();

    /** 断线记账:uuid → 断线时刻毫秒(markDisconnected 时记入,重连/移除时销账)。 */
    private final Map<UUID, Long> disconnectedSince = new HashMap<>();

    /** 加入(在册即幂等返回;新成员 ONLINE,joinedAtMillis 取系统时钟)。 */
    public void join(UUID uuid) {
        join(uuid, System.currentTimeMillis());
    }

    /** 加入的可控时钟重载(离线测试与登记路由用;在册即幂等返回)。 */
    public void join(UUID uuid, long nowMillis) {
        Objects.requireNonNull(uuid, "uuid");
        if (members.containsKey(uuid)) {
            return;
        }
        members.put(uuid, new SessionMember(uuid, SessionMember.ConnectionStatus.ONLINE, nowMillis));
    }

    /** 真正移除(宽限期满清点或显式离开时由会话编排调;不在册为 no-op)。 */
    public void remove(UUID uuid) {
        members.remove(uuid);
        disconnectedSince.remove(uuid);
    }

    /**
     * 置 DISCONNECTED,<b>不移除</b>——宽限期内成员仍在册、重连即恢复(§3.1),
     * 且切换前后恒等;同时记入断线时刻供 {@link #disconnectedBefore} 判定。
     */
    public void markDisconnected(UUID uuid, long atMillis) {
        SessionMember current = members.get(uuid);
        if (current == null) {
            return;
        }
        members.put(uuid, withStatus(current, SessionMember.ConnectionStatus.DISCONNECTED));
        disconnectedSince.put(uuid, atMillis);
    }

    /** 置回 ONLINE(未断线或不在册为 no-op)。 */
    public void markReconnected(UUID uuid) {
        SessionMember current = members.get(uuid);
        if (current == null || current.connection() != SessionMember.ConnectionStatus.DISCONNECTED) {
            return;
        }
        members.put(uuid, withStatus(current, SessionMember.ConnectionStatus.ONLINE));
        disconnectedSince.remove(uuid);
    }

    /** 在册快照(不可变;迭代序 = 加入序;外部修改抛 {@link UnsupportedOperationException})。 */
    public List<SessionMember> all() {
        return List.copyOf(members.values());
    }

    public int size() {
        return members.size();
    }

    public boolean contains(UUID uuid) {
        return members.containsKey(uuid);
    }

    /** 在线成员数(DISCONNECTED 不计)。 */
    public int onlineCount() {
        int count = 0;
        for (SessionMember member : members.values()) {
            if (member.connection() == SessionMember.ConnectionStatus.ONLINE) {
                count++;
            }
        }
        return count;
    }

    /**
     * 宽限已过的成员 uuid 列表(供清点;nowMillis - 断线时刻 &gt;= graceMillis 即算已过——
     * 到点即宽限耗尽)。不含未断线成员。
     */
    public List<UUID> disconnectedBefore(long nowMillis, long graceMillis) {
        List<UUID> expired = new ArrayList<>();
        for (Map.Entry<UUID, Long> entry : disconnectedSince.entrySet()) {
            if (nowMillis - entry.getValue() >= graceMillis) {
                expired.add(entry.getKey());
            }
        }
        return expired;
    }

    /**
     * 名册原地位移的实例侧落点:按当前成员集生成参赛者视图——<b>每局新造</b>
     * {@link Participant}(§12.2 位移点;初始标记 JOINING,§3.2-B4)。名册自身不动。
     */
    public Map<UUID, Participant> toParticipants() {
        Map<UUID, Participant> result = new LinkedHashMap<>();
        for (SessionMember member : members.values()) {
            result.put(member.uuid(), new Participant(member.uuid()));
        }
        return result;
    }

    private static SessionMember withStatus(SessionMember member, SessionMember.ConnectionStatus status) {
        return new SessionMember(member.uuid(), status, member.joinedAtMillis());
    }
}
