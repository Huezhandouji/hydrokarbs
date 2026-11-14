package com.hydrokarbs;

import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import com.hydrokarbs.api.HydrokarbsApi;
import com.hydrokarbs.api.HydrokarbsApiImpl;
import com.hydrokarbs.bridge.config.ConfigRepository;
import com.hydrokarbs.bridge.world.BukkitWorldLoader;
import com.hydrokarbs.bridge.world.WorldPortImpl;
import com.hydrokarbs.command.HydrokarbsCommand;
import com.hydrokarbs.command.SessionCommand;
import com.hydrokarbs.engine.EngineTicker;
import com.hydrokarbs.engine.component.SessionComponentContributionRegistry;
import com.hydrokarbs.engine.event.SessionEventBus;
import com.hydrokarbs.engine.mode.MatchModeRegistry;
import com.hydrokarbs.engine.port.ConfigView;
import com.hydrokarbs.engine.port.EngineServices;
import com.hydrokarbs.engine.port.PlatformPort;
import com.hydrokarbs.engine.service.ServiceRegistry;
import com.hydrokarbs.engine.service.ServiceSpec;
import com.hydrokarbs.engine.session.DissolveReason;
import com.hydrokarbs.engine.session.SessionPolicy;
import com.hydrokarbs.engine.session.SessionService;
import com.hydrokarbs.engine.session.SessionServiceImpl;
import com.hydrokarbs.listener.ProviderUnloadListener;
import com.hydrokarbs.listener.SessionIngestListener;
import com.hydrokarbs.service.GlobalBoard;
import com.hydrokarbs.service.GlobalLedger;
import com.hydrokarbs.util.Threads;

/**
 * L0 装配编排:单一静态装配类 + 单一编排函数(18 号修正案 D12)。
 * {@link #assemble} 一函承载 onEnable 全部装配序列(线性,自上而下可读完),
 * {@link #shutdown} 镜像 onDisable;主类退化为两行委托。
 *
 * <p>硬约束:①静态类不许有静态可变状态;②框架不创建任何会话——唯一创建路径 =
 * 宿主编排点调 {@code SessionService.create(SessionSpec)}(§3.2;拓扑 = 宿主调几次
 * create,§3.6 引擎零感知),「零会话起服」合法(空集空转/解散均幂等);
 * ③装配异常上抛 → 服务端禁用插件——不吞、不半启动。
 */
public final class HydrokarbsAssembly {

    private HydrokarbsAssembly() {
    }

    /**
     * 装配产物(不可变 record,由插件实例字段持有)。
     * {@code worldIo} 为世界端口 IO 执行器,onDisable 收口(防重载后线程残留);
     * {@code api} 为对外公共面实现(★19 冻结面;shutdown 先摘,防 reload 窗口悬挂引用)。
     */
    public record Context(ServiceRegistry services, EngineTicker ticker, SessionService sessions,
                          ExecutorService worldIo, HydrokarbsApi api) {
    }

    /** onEnable 全部装配序列(线性;失败上抛 = 插件拒载)。 */
    public static Context assemble(JavaPlugin plugin) {
        Threads.init(plugin);                                  // ① 平台回程口(既有)

        // ── 配置与模式注册(装载时序不变式:saveDefaults 先行 → loadAll → discover)──
        var dataDir = plugin.getDataFolder().toPath();
        var configRepository = new ConfigRepository(dataDir,
                ConfigRepository.DefaultResourceSource.of(plugin));
        try {
            configRepository.saveDefaults();                   // ② 首次释放默认资源(幂等)
            configRepository.loadAll();                        // ③ 全量装载(单文件失败跳过+SEVERE)
        } catch (IOException e) {
            throw new IllegalStateException("配置装载失败(数据目录不可写?): " + dataDir, e);
        }
        var modeRegistry = new MatchModeRegistry(configRepository::modeConfig);
        modeRegistry.discover();                               // ④ 模式双发现(0 个模式属正常)

        ConfigView cfg = configRepository;                     // ⑥ 惰性只读配置面

        // ── 世界端口(复制源 = 声明自带路径 ★v1.8;副本根 = 世界容器;零模板预复制 §14.2)──
        var worldIo = Executors.newVirtualThreadPerTaskExecutor();   // 副本复制/删除 = 虚拟线程(§3.7)
        var worldPort = new WorldPortImpl(
                new BukkitWorldLoader(),
                Bukkit.getWorldContainer().toPath(),
                Threads::runOnMain,                             // 世界装卸 = 主线程(§3.7)
                worldIo,
                Duration.ofSeconds(30),                         // 获取超时(§3.5 防护④)
                plugin.getLogger());

        // ── 会话服务(平台触点全部收敛为注入函数) ──────────────────────────
        var sessionEvents = new SessionEventBus();             // ⑨ 服务订阅面汇聚点(D3 镜像目的地)
        var sessionSvc = new SessionServiceImpl.Builder()
                .modes(modeRegistry)
                .worlds(worldPort)
                .maps(configRepository.maps())
                .configSource(configRepository::modeConfig)
                .policy(sessionPolicy(cfg))
                // 回退目标 = session.fallback-mode(★v1.13/31 号;空串/未注册 = 无效回退,
                // 回退需求时 SEVERE + 强制解散)
                .fallbackModeId(cfg.get("session.fallback-mode", ""))
                .engineServices(new EngineServices(platformPort(plugin)))
                .mainExecutor(Threads::runOnMain)              // future 拒绝完成点回主线程
                .hubRelocator(hubRelocator())                  // 解散传送主城出生点(Bukkit 触点在此)
                .safeStateCleaner(safeStateCleaner())          // 三级兜底:清通用安全态
                .worldClearer(worldClearer())                  // ★v1.9(27 号):切换清场——旧世界遗留人员 → 目标世界出生点
                .hubWorldClearer(hubWorldClearer())            // ★v1.9(27 号):解散清场——地图世界遗留人员 → 主城出生点(先名册后世界)
                .sessionEventMirror(sessionEvents::publish)    // ★v1.1-D3 镜像:会话事件同步汇聚到 ⑨(7.5 决策 12)
                .build();                                      // ⑧

        var contributions = new SessionComponentContributionRegistry();   // D14 收录表(进程级)
        var services = new ServiceRegistry(plugin, cfg);
        services.install(ServiceSpec.of("global-ledger", GlobalLedger.class,
                        env -> new GlobalLedger(sessionEvents, sessionSvc, session -> Map.of()))
                .build());
        services.install(ServiceSpec.of("global-board", GlobalBoard.class,
                        env -> new GlobalBoard(sessionEvents))
                .build());
        services.seal();                                       // ⑩ 校验 + 拓扑 + 启动

        var ticker = new EngineTicker(plugin, sessionSvc);     // ⑪ 全局节拍(周期读配置)
        ticker.start(cfg.get("engine.tick-period", 1));

        plugin.getServer().getPluginManager()
                .registerEvents(new SessionIngestListener(sessionSvc), plugin);   // ⑫ 会话入口监听
        // ⑬ Brigadier 命令树(新增命令 = 新类 + 本列表加一行)
        HydrokarbsCommand.register(plugin, List.of(new SessionCommand(sessionSvc)));

        // ⑭ 公共 API 注册(ServicesManager,§7.2 表;★v1.7 冻结面 = 17 方法;
        //    fallbackModeId 惰性读配置;回退模式经 SPI 注册,无提供方归属,第三方不可摘)
        var hydrokarbsApi = new HydrokarbsApiImpl(sessionSvc, modeRegistry,
                configRepository.maps(), sessionEvents, contributions,
                Threads::runOnMain, () -> cfg.get("session.fallback-mode", ""));
        plugin.getServer().getServicesManager()
                .register(HydrokarbsApi.class, hydrokarbsApi, plugin, ServicePriority.Normal);
        // ⑭b 卸载兜底监听(★v1.2/★v1.6):按提供方摘模式/地图/贡献(模式摘除带回退编排)
        plugin.getServer().getPluginManager().registerEvents(
                new ProviderUnloadListener(hydrokarbsApi), plugin);

        return new Context(services, ticker, sessionSvc, worldIo, hydrokarbsApi);
    }

    /** onDisable 镜像:先摘公共面 → 停节拍 → 解散全部会话(传主城,含世界句柄释放) → 逆序关停服务 → 收执行器。 */
    public static void shutdown(Context ctx) {
        // 公共面先摘(幂等):防 reload 窗口第三方 load() 拿到悬挂实现
        Bukkit.getServicesManager().unregister(HydrokarbsApi.class, ctx.api());
        ctx.ticker().close();           // ★ 必取消,否则重载后任务翻倍(14.6)
        for (var session : ctx.sessions().all()) {
            ctx.sessions().dissolve(session.id(), DissolveReason.SHUTDOWN);   // §12.2 关停驱动
        }
        ctx.services().shutdown();      // 逆序 safeStop + 幂等(★38)
        ctx.worldIo().shutdown();       // 世界 IO 执行器收口(已提交任务放行完成,不阻塞等待)
        Threads.shutdown();
    }

    // ── 配置块 → 引擎值(缺键回落默认,§15.2 铁律 1) ────────────────────

    /**
     * {@code session:} 块 → 会话策略(min-dwell 与断线宽限是内核节律,与谁建会话无关)。
     * {@code topology} 字段无框架行为消费(仅 create 日志回显),装配固定
     * {@link SessionPolicy.Topology#ROTATION} 占位——部署拓扑由宿主经 create 次数表达(§3.6)。
     */
    private static SessionPolicy sessionPolicy(ConfigView cfg) {
        return new SessionPolicy(
                SessionPolicy.Topology.ROTATION,
                cfg.get("session.min-dwell-ticks", SessionPolicy.DEFAULT_MIN_DWELL_TICKS),
                cfg.get("session.disconnect-grace-seconds", SessionPolicy.DEFAULT_DISCONNECT_GRACE_SECONDS));
    }

    // ── 平台函数注入(引擎不直碰 Bukkit;传送/清态/世界清场落点,★v1.9/27 号)──

    /** 平台端口:日志/插件句柄/一次性延迟任务(实例侧 later 的调度口)。 */
    private static PlatformPort platformPort(JavaPlugin plugin) {
        return new PlatformPort() {
            @Override
            public java.util.logging.Logger logger() {
                return plugin.getLogger();
            }

            @Override
            public Plugin plugin() {
                return plugin;
            }

            @Override
            public AutoCloseable scheduleOnce(long delayTicks, Runnable task) {
                var handle = Bukkit.getGlobalRegionScheduler()
                        .runDelayed(plugin, scheduled -> task.run(), Math.max(1L, delayTicks));
                return handle::cancel;
            }
        };
    }

    /** 主城出生点单一来源(★v1.9/27 号):服务器主世界出生点;空表返 null。 */
    private static Location hubSpawn() {
        List<World> worlds = Bukkit.getWorlds();
        return worlds.isEmpty() ? null : worlds.get(0).getSpawnLocation();
    }

    /** 解散成员传送:主城出生点 = 服务器主世界出生点(设计未细化,最小自洽)。 */
    private static Consumer<Collection<UUID>> hubRelocator() {
        return members -> {
            Location spawn = hubSpawn();       // 出生点单一来源 = hubSpawn()(逐次解析)
            if (spawn == null) {
                return;
            }
            for (UUID uuid : members) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    player.teleport(spawn);
                }
            }
        };
    }

    /**
     * 切换世界清场(★v1.9/27 号):旧世界全员(不以会话名册为界)传往目标副本世界出生点。
     * 调用点 = 切换内核 B3.5 与 B5 finally;个别传送异常由内核 best-effort WARN 兜住。
     */
    private static BiConsumer<World, World> worldClearer() {
        return (from, to) -> {
            Location spawn = to.getSpawnLocation();
            for (Player player : from.getPlayers()) {
                player.teleport(spawn);
            }
        };
    }

    /**
     * 解散世界清场(★v1.9/27 号):地图世界全员传主城出生点(与 hubRelocator 共用 hubSpawn)。
     * 调用点 = dissolveNow finally:先名册后世界、discardSession 之前;best-effort。
     */
    private static Consumer<World> hubWorldClearer() {
        return world -> {
            Location spawn = hubSpawn();
            if (spawn == null) {
                return;
            }
            for (Player player : world.getPlayers()) {
                player.teleport(spawn);
            }
        };
    }

    /** 三级兜底清态:通用安全态(生存模式/取消隐身)——设计未细化,最小自洽。 */
    private static Consumer<Collection<UUID>> safeStateCleaner() {
        return members -> {
            for (UUID uuid : members) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    player.setGameMode(GameMode.SURVIVAL);
                    player.setInvisible(false);
                }
            }
        };
    }
}
