package com.hydrokarbs.listener;

import java.util.Optional;
import java.util.UUID;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import com.hydrokarbs.engine.event.SessionEvent;
import com.hydrokarbs.engine.session.Session;
import com.hydrokarbs.engine.session.SessionService;

/**
 * 会话入口监听:进服/退服/被踢/重生四事件路由到会话服务(§15.1:NORMAL、
 * ignoreCancelled=false;回调内只路由不做重活):
 * <ul>
 *   <li>进服:断线成员宽限销账 + 置回在线 + 发重连事件;未入会话的新玩家仅
 *       唯一存活会话(rotation)时自动收录,多会话(arena)不自动路由——
 *       定向入口(牌子/NPC)归外部插件;</li>
 *   <li>退服:断线宽限标记(宽限语义见会话服务);被踢:立即移除(宽限仅
 *       服务连接意外中断);</li>
 *   <li>重生:定位所属会话与当前实例即返回——安置是组件层职责,不抢占。</li>
 * </ul>
 */
public final class SessionIngestListener implements Listener {

    private final SessionService sessions;

    public SessionIngestListener(SessionService sessions) {
        this.sessions = sessions;
    }

    /** 进服:宽限期内重连恢复;否则(唯一会话形态)自动收录。 */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID player = event.getPlayer().getUniqueId();
        Optional<Session> owning = sessions.sessionOf(player);
        if (owning.isPresent()) {
            Session session = owning.get();
            session.tracker().onReconnect(player);        // 宽限销账(此后清点不再点名)
            session.roster().markReconnected(player);     // 置回在线(未断线为 no-op)
            session.events().publish(SessionEvent.MemberReconnected.of(session.id(), player));
            return;
        }
        var alive = sessions.all();
        if (alive.size() == 1) {                          // rotation 单会话:全员进它(§3.6)
            Session the = alive.iterator().next();
            sessions.join(the.id(), player);
        }
        // 多会话(arena):新玩家的定向入口归外部插件,不自动路由
    }

    /** 退服:断线视同离开(宽限标记 + 即发离开事件;宽限期内重连即恢复)。 */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        sessions.leave(event.getPlayer().getUniqueId(), com.hydrokarbs.engine.session.LeaveReason.QUIT);
    }

    /** 被踢:立即移除(不享受宽限)。 */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerKick(PlayerKickEvent event) {
        sessions.leave(event.getPlayer().getUniqueId(), com.hydrokarbs.engine.session.LeaveReason.KICK);
    }

    /** 重生:路由到所属会话的当前实例(安置点归组件层,本阶段只定位不动作)。 */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Optional<Session> owning = sessions.sessionOf(event.getPlayer().getUniqueId());
        if (owning.isEmpty()) {
            return;
        }
        // 定位到所属会话的当前实例即返回——重生安置归组件层,会话入口不抢占
        owning.get().currentInstance();
    }
}
