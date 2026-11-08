package com.hydrokarbs.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import io.papermc.paper.command.brigadier.CommandSourceStack;

/**
 * 子命令贡献口:根命令 {@code /hydrokarbs}(别名 {@code hk})下的子树构造器 seam。
 *
 * <p>同 jar 新增命令两步(既有命令类内部逻辑零改动):
 * <ol>
 *   <li>新建 {@code final} 命令类(构造器捕获依赖),实现 {@link #node()} 返回
 *       子树 builder,自带 {@code .requires} 权限门({@code hydrokarbs.command.*});</li>
 *   <li>在 {@code HydrokarbsAssembly.assemble} 的
 *       {@code HydrokarbsCommand.register(plugin, List.of(...))} 装配列表追加一行。</li>
 * </ol>
 *
 * <p>限界(AGENTS §4.11):Brigadier 每次注册交付整棵树,本 seam 仅限同 jar
 * 装配期;跨 jar 消费者一律自立根命令(自有 handler + 权限节点)。
 */
public interface Subcommand {

    /** 返回子树 builder(由 {@code HydrokarbsCommand#buildRoot} 挂到根下;权限门自带)。 */
    LiteralArgumentBuilder<CommandSourceStack> node();
}
