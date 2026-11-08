package com.hydrokarbs.command;

import java.util.List;

import com.mojang.brigadier.tree.LiteralCommandNode;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * 根命令属主:{@code hydrokarbs}(别名 {@code hk})经 {@code LifecycleEvents.COMMANDS}
 * 单点注册(主类 {@code getLifecycleManager} 挂接,重载后自动重新注册);
 * {@code plugin.yml} 不声明 {@code commands:} 段(与 Brigadier 同名双注册冲突,
 * AGENTS §6 技术基线)。
 *
 * <p><b>子命令 seam</b>:子命令由装配方传入 {@link Subcommand} 列表({@link #register}
 * 的 {@code subcommands} 参数即装配清单)——新增命令 = 新类 + 装配列表加一行。
 * 根节点不设 {@code requires},权限门在子命令上。
 */
public final class HydrokarbsCommand {

    private static final String ROOT = "hydrokarbs";
    private static final List<String> ALIASES = List.of("hk");

    private HydrokarbsCommand() {
    }

    /** 在主类的命令生命周期事件上注册命令树(重载后自动重新注册)。 */
    public static void register(JavaPlugin plugin, List<Subcommand> subcommands) {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(buildRoot(subcommands), "Hydrokarbs 主命令", ALIASES));
    }

    /** 组命令树(包内可见:离线断言树形用;不触 Bukkit 静态)。 */
    static LiteralCommandNode<CommandSourceStack> buildRoot(List<Subcommand> subcommands) {
        var root = Commands.literal(ROOT);
        for (Subcommand subcommand : subcommands) {
            root.then(subcommand.node());
        }
        return root.build();
    }
}
