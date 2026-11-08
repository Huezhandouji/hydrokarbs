package com.hydrokarbs.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.hydrokarbs.testkit.StubSessionService;

import org.junit.jupiter.api.Test;

/**
 * 命令树离线树形断言(★9:零 Bukkit import,纯 Brigadier 构树/遍历)。
 *
 * <p>断言面:根名 hydrokarbs/根下恰为 {session}/session 下恰为 {status, end}/
 * 两处 {@code <id>} 参数类型 == {@code greedyString()}(防回退 word()——SessionId
 * 值域含 {@code [0-9A-Za-z_.+-]} 外字符,word() 截断解析 → session(id) 永远 miss;
 * 按 {@code StringType.GREEDY_PHRASE} 枚举判)/requires 门非空/公开权限常量冻结;
 * 另断言全树无 switch 节点(switch 子命令已随 MatchCommand 退场的删除标记)。
 */
class CommandTreeShapeTest {

    /** 离线构树:装配入参即现行装配表(StubSessionService 零行为,树形不执行)。 */
    private static LiteralCommandNode<CommandSourceStack> buildTree() {
        return HydrokarbsCommand.buildRoot(List.of(new SessionCommand(
                new StubSessionService(() -> List.of()))));
    }

    /** 直属子节点名集合。 */
    private static Set<String> childNames(CommandNode<CommandSourceStack> node) {
        Set<String> names = new TreeSet<>();
        for (CommandNode<CommandSourceStack> child : node.getChildren()) {
            names.add(child.getName());
        }
        return names;
    }

    /** 递归收集全树节点名(含根)。 */
    private static Set<String> allNames(CommandNode<CommandSourceStack> node, Set<String> into) {
        into.add(node.getName());
        for (CommandNode<CommandSourceStack> child : node.getChildren()) {
            allNames(child, into);
        }
        return into;
    }

    @Test
    void rootIsHydrokarbsWithSingleSessionChild() {
        var root = buildTree();
        assertEquals("hydrokarbs", root.getName());
        assertEquals(Set.of("session"), childNames(root));
    }

    @Test
    void sessionHasExactlyStatusAndEnd() {
        var session = buildTree().getChild("session");
        assertEquals(Set.of("status", "end"), childNames(session));
    }

    @Test
    void idArgumentsAreGreedyStrings() {
        for (String literal : List.of("status", "end")) {
            var branch = buildTree().getChild("session").getChild(literal);
            assertEquals(Set.of("id"), childNames(branch), literal + " 下应恰有 id 参数");
            var id = branch.getChild("id");
            assertTrue(id instanceof ArgumentCommandNode<?, ?>, literal + " 的 id 应为参数节点");
            ArgumentType<?> type = ((ArgumentCommandNode<?, ?>) id).getType();
            assertTrue(type instanceof StringArgumentType, literal + " 的 id 参数应为 StringArgumentType");
            assertEquals(StringArgumentType.StringType.GREEDY_PHRASE, ((StringArgumentType) type).getType(),
                    literal + " 的 id 参数应为 greedyString(禁 word)");
        }
    }

    @Test
    void permissionGatesPresentAndConstantsFrozen() {
        var session = buildTree().getChild("session");
        var end = session.getChild("end");
        assertNotNull(session.getRequirement(), "session 子树应设 requires 门");
        assertNotNull(end.getRequirement(), "end 子命令应设 requires 门");
        assertEquals("hydrokarbs.command.session", SessionCommand.SESSION_PERMISSION);
        assertEquals("hydrokarbs.command.session.end", SessionCommand.END_PERMISSION);
    }

    @Test
    void noSwitchNodeAnywhere() {
        assertFalse(allNames(buildTree(), new TreeSet<>()).contains("switch"),
                "switch 子命令已随 MatchCommand 退场,全树不应再出现");
    }
}
