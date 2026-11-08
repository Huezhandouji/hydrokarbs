package com.example.their;

import java.util.UUID;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.hydrokarbs.engine.component.MatchModeComponent;
import com.hydrokarbs.engine.instance.Participant;
import com.hydrokarbs.engine.port.MatchModeInstanceServices;

/**
 * 第三方私有组件:模式包内自建组件的基类纪律最小样例(§7.2「第三方组件」/§2.4)。
 * 纪律要点:{@code create} 是唯一装配点(构造私有);svc 构造期注入先于一切钩子;
 * 玩家可见副作用最早发生在 {@link #start()}(awake 只解析依赖);异常只隔离自己
 * 的实例(框架兜底,无需自包 try/catch);播报用 {@code Component.text} 固定文案,
 * 零 MiniMessage 注入面(§14.10)。
 */
public final class TheirBannerComponent extends MatchModeComponent {

    /** 装配描述符:泛型实参声明本组件类型,工厂 {@code create} 是唯一创建点(§2.4)。 */
    public static final class Spec extends MatchModeComponent.Specification<TheirBannerComponent> {

        public Spec() {
            super("their_banner");
        }

        @Override
        public TheirBannerComponent create(String id, MatchModeInstanceServices svc) {
            return new TheirBannerComponent(id, svc);
        }
    }

    private TheirBannerComponent(String id, MatchModeInstanceServices svc) {
        super(id, svc);
    }

    /** 开局播报一句(玩家可见副作用最早在这里;零其他副作用,§2.4)。 */
    @Override
    public void start() {
        for (Participant participant : svc().self().participants()) {
            UUID uuid = participant.uuid();
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendMessage(Component.text("[TheirMode] 欢迎来到第三方模式示例!",
                        NamedTextColor.GOLD));
            }
        }
    }
}
