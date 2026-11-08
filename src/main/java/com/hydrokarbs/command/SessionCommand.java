package com.hydrokarbs.command;

import java.util.Optional;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.command.CommandSender;

import com.hydrokarbs.engine.session.DissolveReason;
import com.hydrokarbs.engine.session.Session;
import com.hydrokarbs.engine.session.SessionId;
import com.hydrokarbs.engine.session.SessionMember;
import com.hydrokarbs.engine.session.SessionService;

/**
 * 会话管理子命令(经 {@link Subcommand} seam 挂到 {@code /hydrokarbs} 根):
 * {@code session} 列出存活会话;{@code session status <id>} 单会话详情;
 * {@code session end <id>} 强制解散(委托
 * {@link SessionService#dissolve(SessionId, DissolveReason)} 的 {@code ADMIN} 通道,
 * 幂等,REUSE_DIRTY 副本随解散连带销毁)。
 *
 * <p>权限:查询走 {@code hydrokarbs.command.session},解散走 {@code hydrokarbs.command.session.end}
 * (树形父子:有 end 无 session 则 end 不可见;节点不进 plugin.yml,默认 op)。
 *
 * <p>{@code <id>} 写死 {@code greedyString()}:SessionId 值域 = 宿主任意非空串
 * (含空格/Unicode),{@code word()} 遇空格截断、真实 id 会误报不存在,不反向
 * 收紧其字符集(冻结值对象);status 用显式 {@code literal} 防与 end 撞名。
 */
public final class SessionCommand implements Subcommand {

    /** 会话查询的权限节点(status 同门;子树级)。 */
    public static final String SESSION_PERMISSION = "hydrokarbs.command.session";

    /** 强制解散会话的权限节点(树形父子:有 end 无 session 则 end 不可见)。 */
    public static final String END_PERMISSION = "hydrokarbs.command.session.end";

    private final SessionService sessions;

    public SessionCommand(SessionService sessions) {
        this.sessions = sessions;
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("session")
                .requires(source -> source.getSender().hasPermission(SESSION_PERMISSION))
                .executes(context -> executeList(context.getSource()))
                .then(Commands.literal("status")
                        .then(Commands.argument("id", StringArgumentType.greedyString())
                                .suggests(suggestSessionIds(sessions))
                                .executes(context -> executeStatus(
                                        context.getSource(), context.getArgument("id", String.class)))))
                .then(Commands.literal("end")
                        .requires(source -> source.getSender().hasPermission(END_PERMISSION))
                        .then(Commands.argument("id", StringArgumentType.greedyString())
                                .suggests(suggestSessionIds(sessions))
                                .executes(context -> executeEnd(
                                        context.getSource(), context.getArgument("id", String.class)))));
    }

    /** {@code session}:列出存活会话与当前模式。 */
    private int executeList(CommandSourceStack source) {
        CommandSender sender = source.getSender();
        var alive = sessions.all();
        if (alive.isEmpty()) {
            sender.sendMessage(Component.text("当前没有存活会话", NamedTextColor.YELLOW));
            return com.mojang.brigadier.Command.SINGLE_SUCCESS;
        }
        sender.sendMessage(Component.text("存活会话 " + alive.size() + " 个:", NamedTextColor.AQUA));
        for (Session session : alive) {
            sender.sendMessage(Component.text("  " + session.id().value()
                    + " → " + currentMode(session) + "(成员 " + session.roster().size() + ",状态 "
                    + session.state() + ")", NamedTextColor.GRAY));
        }
        return com.mojang.brigadier.Command.SINGLE_SUCCESS;
    }

    /** {@code session status <id>}:单会话详情。 */
    private int executeStatus(CommandSourceStack source, String rawId) {
        CommandSender sender = source.getSender();
        Optional<Session> target = findSession(rawId);
        if (target.isEmpty()) {
            sender.sendMessage(Component.text("会话不存在: " + rawId, NamedTextColor.RED));
            return com.mojang.brigadier.Command.SINGLE_SUCCESS;
        }
        Session session = target.get();
        sender.sendMessage(Component.text("会话 " + session.id().value() + "(状态 "
                + session.state() + ",当前模式 " + currentMode(session) + ",成员 "
                + session.roster().all().size() + ")", NamedTextColor.AQUA));
        for (SessionMember member : session.roster().all()) {
            sender.sendMessage(Component.text("  " + member.uuid() + "("
                    + (member.connection() == SessionMember.ConnectionStatus.ONLINE ? "在线" : "断线")
                    + ")", NamedTextColor.GRAY));
        }
        return com.mojang.brigadier.Command.SINGLE_SUCCESS;
    }

    /** {@code session end <id>}:强制解散(存在性先查——命令与 dissolve 同在主线程,单线程契约下无 TOCTOU)。 */
    private int executeEnd(CommandSourceStack source, String rawId) {
        CommandSender sender = source.getSender();
        if (findSession(rawId).isEmpty()) {
            sender.sendMessage(Component.text("会话不存在: " + rawId, NamedTextColor.RED));
            return com.mojang.brigadier.Command.SINGLE_SUCCESS;
        }
        try {
            // 幂等(护栏吸收异常态);REUSE_DIRTY 副本随解散连带销毁——命令侧零善后
            sessions.dissolve(new SessionId(rawId), DissolveReason.ADMIN);
        } catch (RuntimeException err) {
            sender.sendMessage(Component.text("解散执行异常: " + err, NamedTextColor.RED));
            return com.mojang.brigadier.Command.SINGLE_SUCCESS;
        }
        sender.sendMessage(Component.text("会话 " + rawId + " 已强制解散(原因 ADMIN)",
                NamedTextColor.GREEN));
        return com.mojang.brigadier.Command.SINGLE_SUCCESS;
    }

    /** 按 id 解析会话(blank → empty:greedyString 可解析出空串,而 SessionId 构造拒空白)。 */
    private Optional<Session> findSession(String rawId) {
        return rawId.isBlank() ? Optional.empty() : sessions.session(new SessionId(rawId));
    }

    /** 当前模式 id(无实例/切换中间态 → 「(无实例)」)。 */
    private static String currentMode(Session session) {
        return session.currentInstance()
                .map(instance -> instance.modeId())
                .orElse("(无实例)");
    }

    /** id 建议列表:直取全部存活会话 id(免 Bukkit 名字解析)。 */
    private static SuggestionProvider<CommandSourceStack> suggestSessionIds(SessionService sessions) {
        return (context, builder) -> {
            for (Session session : sessions.all()) {
                builder.suggest(session.id().value());
            }
            return builder.buildFuture();
        };
    }
}
