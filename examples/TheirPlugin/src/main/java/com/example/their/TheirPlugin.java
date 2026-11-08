package com.example.their;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import com.hydrokarbs.api.EventSubscription;
import com.hydrokarbs.api.MiniGameApi;
import com.hydrokarbs.api.SessionEventView;
import com.hydrokarbs.api.SessionRecipe;
import com.hydrokarbs.api.view.MapInfo;
import com.hydrokarbs.api.view.SessionInfo;
import com.hydrokarbs.engine.AttributeBag;
import com.hydrokarbs.engine.component.SessionComponent;
import com.hydrokarbs.engine.session.JoinResult;
import com.hydrokarbs.engine.session.LeaveReason;
import com.hydrokarbs.map.MapDefinition;

/**
 * 第三方消费样例插件(13-扩展步骤 §13.4/§13.10/§13.11 落地版):真实消费
 * {@link MiniGameApi} 面——成员操作、事件订阅、SessionRecipe 建会话、注册式地图
 * (解包到本插件数据目录,★v1.8)、模式与会话组件显式注册(★v1.6)、api 缺席拒载
 * (版本协商面已于 ★v1.14/32 号退场)、句柄只在实例字段(14.38)、禁 shade 断言
 * (14.35);外部能力 = 会话组件桥接({@link TheirSessionComponent})。
 *
 * <p><b>onEnable 固定顺序</b>(§13.4/§13.11):首行查 api 在场(缺席即拒载)→
 * registerMode(响亮抛 = 拒载信号)→ registerSessionComponents → 解包模板 →
 * 构造声明 → registerMap → 订阅事件 → 注册命令。玩家可控串一律
 * {@code Component.text(...)},不过 MiniMessage。
 * <b>卸载善后(14.38)</b>:句柄显式 close;地图/模式/贡献显式反注册(正用摘除被拒
 * = 保留,20-D4;框架侧兜底仍在,显式反注册是防御性纪律)。
 */
public final class TheirPlugin extends JavaPlugin {

    /** 注册式地图 id(= 声明目录名,路径派生 id 的目录段;模板随本 jar 分发)。 */
    static final String MAP_ID = "their_arena";
    /** 模板资源根(jar 内资源路径;解包目标 = 本插件数据目录 maps/<id>/,★v1.8 路径直交)。 */
    private static final String TEMPLATE_RESOURCE_ROOT = "maps/templates/" + MAP_ID;
    /** 根命令(自有命令树;plugin.yml 不写 commands: 段——Brigadier 注册)。 */
    private static final String ROOT_COMMAND = "their";
    /**
     * D14 通道一:全局默认贡献(★v1.6 显式注册;所有新会话合并)。Spec 常量
     * 共享跨会话互不串改由 Snapshot 结构保证(收录表 freeze 副作用注)。
     * 包私有:守卫用例(TheirPluginGuardsTest)直读断言。
     */
    static final List<SessionComponent.Specification<?>> SESSION_COMPONENT_SPECS =
            List.of(new TheirSessionComponent.Spec());

    private MiniGameApi api;
    /** 订阅句柄只在插件实例字段中持有(14.38;onDisable 显式 close)。 */
    private EventSubscription eventSubscription;

    @Override
    public void onEnable() {
        api = getServer().getServicesManager().load(MiniGameApi.class);
        if (api == null) {                  // 框架缺席 → 拒载不崩服(版本协商面已退场,★v1.14/32 号)
            getLogger().severe("Hydrokarbs missing, disabling " + getName());
            setEnabled(false);
            return;
        }
        // ★v1.6 显式注册(注册工厂/声明,不是注册成品;§13.4 步骤 2;D14 通道一):
        // 注册闸响亮抛(build/依赖校验/重复 id)——异常上抛 = onEnable 失败 = 拒载信号
        api.registerMode(this, new TheirModeFactory());
        api.registerSessionComponents(this, SESSION_COMPONENT_SPECS);
        registerTheirArenaMap();            // ★v1.2:解包 → 构造声明 → registerMap(14.39)
        subscribeSessionEvents();           // D3:句柄存实例字段
        registerCommands();                 // 自有 Brigadier 命令树
        getLogger().info("TheirPlugin enabled");
    }

    @Override
    public void onDisable() {
        if (eventSubscription != null) {                 // 句柄显式回收(14.38)
            eventSubscription.close();
            eventSubscription = null;
        }
        if (api != null) {
            try {
                api.unregisterMap(this, MAP_ID);          // §13.11 步骤 6(正用被拒 = 保留)
            } catch (RuntimeException refused) {
                getLogger().warning("Map removal refused, map kept: "
                        + refused.getMessage());
            }
            api.unregisterSessionComponents(this);        // 镜像 unregisterMap 纪律(幂等;兜底仍存在)
            api.unregisterMode(this, TheirModeFactory.MODE_ID);   // 幂等;兜底仍存在(回退编排同款)
        }
        api = null;
    }

    // ── D3:会话事件订阅(快照消费演示;句柄存实例字段) ──────────────────

    private void subscribeSessionEvents() {
        eventSubscription = api.subscribeSessionEvents(this::onSessionEvent);
    }

    /** 快照消费:只读、现取、不持活引用(14.31 同款禁令;看板粒度止于会话级,§13.10 例二)。 */
    private void onSessionEvent(SessionEventView view) {
        if (view instanceof SessionEventView.Dissolved dissolved) {
            getLogger().info("[Board] Session dissolved: " + dissolved.session().value()
                    + " (reason " + dissolved.reason() + ")");
        } else if (view instanceof SessionEventView.ModeSwitchCompleted completed) {
            getLogger().info("[Board] Switch completed: " + completed.session().value()
                    + " " + completed.from() + " -> " + completed.to());
        }
    }

    // ── ★v1.2:注册式地图(onEnable 顺序 = 解包 → 构造 → registerMap,14.39) ──

    private void registerTheirArenaMap() {
        // ① 解包模板落盘(★v1.8 路径直交:模板住本插件自己的数据目录 maps/<id>/,
        //    不再寄居框架数据目录)
        Path worldDir = getDataFolder().toPath().resolve("maps").resolve(MAP_ID);
        extractTemplate(TEMPLATE_RESOURCE_ROOT + "/level.properties",
                worldDir.resolve("level.properties"));
        extractTemplate(TEMPLATE_RESOURCE_ROOT + "/region/placeholder.txt",
                worldDir.resolve("region").resolve("placeholder.txt"));
        // ② 构造声明(★v1.8 两参新形状:路径 + 词料容器;纯静态值,代码构造
        //    合法——13 §13.11 步骤 3,声明走 yml 亦可;标签进 MapDefinition.TAGS 词料键)
        MapDefinition map = new MapDefinition(worldDir,
                new AttributeBag.Builder().put(MapDefinition.TAGS, Set.of("demo")).build());
        // ③ 注册(注册期校验:世界目录存在性带绝对路径/id 查重点名先到者——缺失响亮抛,
        //    20-D3;本样例按设计让异常上抛 = 拒载信号,onEnable 失败即插件禁用)
        MapInfo info = api.registerMap(this, map);
        getLogger().info("Map registered: " + info.id());
    }

    /** 单文件解包(目标 = 框架数据目录;幂等覆盖,重复 enable 安全)。 */
    private void extractTemplate(String resourcePath, Path target) {
        InputStream source = getResource(resourcePath);
        if (source == null) {
            throw new IllegalStateException("模板资源缺席: " + resourcePath
                    + "(14.39:解包先于注册,资源必须随 jar 分发)");
        }
        try (source) {
            Files.createDirectories(target.getParent());
            Files.copy(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("模板解包失败: " + resourcePath + " → " + target, e);
        }
    }

    // ── 自有 Brigadier 命令树(/their;演示 D2/D3/D14 面) ──────────────

    private void registerCommands() {
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(ROOT_COMMAND)
                    .requires(source -> source.getSender() instanceof Player)
                    .then(Commands.literal("join")
                            .then(Commands.argument("sessionId", StringArgumentType.string())
                                    .executes(ctx -> joinCommand(ctx,
                                            StringArgumentType.getString(ctx, "sessionId")))))
                    .then(Commands.literal("leave")
                            .executes(this::leaveCommand))
                    .then(Commands.literal("recipe")
                            .then(Commands.argument("modeId", StringArgumentType.word())
                                    .executes(ctx -> recipeCommand(ctx,
                                            StringArgumentType.getString(ctx, "modeId"), null))
                                    .then(Commands.argument("mapId", StringArgumentType.word())
                                            .executes(ctx -> recipeCommand(ctx,
                                                    StringArgumentType.getString(ctx, "modeId"),
                                                    StringArgumentType.getString(ctx, "mapId"))))))
                    .then(Commands.literal("events")
                            .executes(this::toggleEventsCommand));
            event.registrar().register(root.build(),
                    "TheirPlugin 消费样例:join/leave/recipe/events", List.of("theirp"));
        });
    }

    /** D2 编排:先出局(hub 出局,幂等;LeaveReason 仅 QUIT/KICK 两值,§3.2)再入局(含裁决;Rejected 就地提示,§7.5 示例一)。 */
    private int joinCommand(CommandContext<CommandSourceStack> ctx, String sessionId) {
        Player player = (Player) ctx.getSource().getSender();
        api.leaveSession(player.getUniqueId(), LeaveReason.KICK);
        JoinResult result = api.joinSession(sessionId, player.getUniqueId());
        if (result instanceof JoinResult.Rejected rejected) {
            player.sendMessage(Component.text("入场被拒: " + rejected.detail(), NamedTextColor.RED));
            return 0;
        }
        player.sendMessage(Component.text("已入会话 " + sessionId, NamedTextColor.GREEN));
        return 1;
    }

    /** D2:出局(幂等,不在会话 = no-op)。 */
    private int leaveCommand(CommandContext<CommandSourceStack> ctx) {
        Player player = (Player) ctx.getSource().getSender();
        api.leaveSession(player.getUniqueId(), LeaveReason.KICK);
        player.sendMessage(Component.text("已移出当前会话(若在)", NamedTextColor.GREEN));
        return 1;
    }

    /** D14 通道二:SessionRecipe 单次建会话(查重/缺依赖响亮拒,装配期失败就地提示)。 */
    private int recipeCommand(CommandContext<CommandSourceStack> ctx, String modeId, String mapId) {
        Player player = (Player) ctx.getSource().getSender();
        try {
            SessionInfo info = api.createSession(SessionRecipe.of(modeId, mapId));
            player.sendMessage(Component.text("会话已创建: " + info.sessionId()
                    + "(随 dissolve 全灭;D14 通道二)", NamedTextColor.GREEN));
            return 1;
        } catch (RuntimeException rejected) {
            player.sendMessage(Component.text("建会话被拒(响亮失败): " + rejected.getMessage(),
                    NamedTextColor.RED));
            return 0;
        }
    }

    /** D3:订阅开关(演示句柄 close;快照消费见 onSessionEvent)。 */
    private int toggleEventsCommand(CommandContext<CommandSourceStack> ctx) {
        Player player = (Player) ctx.getSource().getSender();
        if (eventSubscription != null) {
            eventSubscription.close();
            eventSubscription = null;
            player.sendMessage(Component.text("事件订阅已关闭", NamedTextColor.YELLOW));
        } else {
            subscribeSessionEvents();
            player.sendMessage(Component.text("事件订阅已开启", NamedTextColor.GREEN));
        }
        return 1;
    }
}
