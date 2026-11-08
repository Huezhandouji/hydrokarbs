package com.hydrokarbs.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import com.hydrokarbs.api.view.MapInfo;
import com.hydrokarbs.api.view.MatchModeInfo;
import com.hydrokarbs.api.view.SessionInfo;
import com.hydrokarbs.engine.component.SessionComponent;
import com.hydrokarbs.engine.mode.MatchModeProvider;
import com.hydrokarbs.engine.session.Handoff;
import com.hydrokarbs.engine.session.JoinResult;
import com.hydrokarbs.engine.session.LeaveReason;
import com.hydrokarbs.engine.session.MatchModeSelection;
import com.hydrokarbs.engine.session.SwitchResult;
import com.hydrokarbs.map.MapDefinition;

/**
 * 本框架的对外公共面,经 Bukkit ServicesManager 暴露(07-公共API §7.1/§7.2)。
 *
 * <p><b>v1 冻结面(§7.2;现 17 方法)</b>:方法集冻结、<b>只增不改</b>
 * (★19/★44 反射守卫,删/改签名即测试红);引擎契约类型({@code SwitchResult}/
 * {@code MatchModeSelection}/{@code Handoff})直接 import 自 {@code engine.session},
 * 不复制(§16.2)。API 版本协商面已退场(★v1.14,32 号)。
 *
 * <p><b>A9 三限制(§0.2-A9/§7.2)</b>:①聚合根 {@code build()} 后不可变;②第三方
 * 不能给已注册模式追加组件或改组件表;③要「变体」就注册一个新 id。
 *
 * <p><b>线程契约</b>(§10.5/§14.1/§11.1):查询/建会话/成员/注册/目录方法主线程调用
 * (同步返回快照,结构非线程安全);{@code switchMode} 任意线程发起、Impl 回主线程、
 * future 在主线程完成;订阅回调发生在事件发布线程(会话事件发布在主线程)。
 */
public interface MiniGameApi {

    // ── 查询(不可变快照;现取不缓存,14.31 同款纪律) ────────────────────────

    /** 玩家 → 所属会话快照(名册查询);未入任何会话 → empty。主线程调用。 */
    Optional<SessionInfo> sessionOf(UUID player);

    /** 按会话 id 取快照(只读查询);不存在 → empty。主线程调用。 */
    Optional<SessionInfo> session(String sessionId);

    /** 全部存活会话快照(引擎遍历序);空表合法(零会话起服为合法状态,§3.6)。主线程调用。 */
    List<SessionInfo> sessions();

    // ── 控制(与 /sm 命令同权,§7.1) ────────────────────────────────────────

    /**
     * 切换某会话的模式——{@code SessionService#switchMode} 的受控暴露(§3.2)。
     * <p>失败语义:会话 id 不在注册表 → future 完成 {@code Rejected(MODE_NOT_FOUND)};
     * 其余拒绝经引擎回退管线原样透传;空白 id / null target → future 异常完成;
     * future 永不悬挂(§3.2)。任意线程发起,Impl 回主线程、future 在主线程完成
     * (§14.1)——回调里做玩家操作请自行再回主线程。
     */
    CompletableFuture<SwitchResult> switchMode(String sessionId, MatchModeSelection target,
                                               Handoff payload);

    /**
     * 创建会话(★v1.1-D14 通道二:会话配方,单次贡献会话组件)——公共 API 建会话的
     * 唯一形态(★v1.4)。合并序 = 已收录 provider 贡献(注册序)→
     * {@code recipe.extraComponents}(装配点唯一;模式无权贡献,无运行期增删)。
     * <p>装配期响亮失败(§7.3):{@code initialModeId} 未注册 / 组件 id 查重冲突 /
     * 缺必需依赖 → 抛,会话不创建、不留半装配态。主线程调用。
     */
    SessionInfo createSession(SessionRecipe recipe);

    // ── 成员操作(★v1.1-D2;★42) ────────────────────────────────────────────

    /**
     * 将玩家加入会话——{@code SessionService#join} 的受控暴露(§3.2;含
     * {@code JoinAdmission} 裁决)。引擎既有裁决原样透传:会话不存在 / 正在解散 /
     * 入场裁决拒绝 → {@code JoinResult.Rejected}(detail 人读;调用方兜底提示);
     * 成员已在册 → 幂等重入(§3.2)。主线程调用。
     */
    JoinResult joinSession(String sessionId, UUID player);

    /**
     * 将玩家移出其当前会话;不在任何会话 = no-op(幂等,D2)。主线程调用。
     * {@code reason} 决定离开语义(QUIT = 断线宽限在册 / KICK = 立即移除,§3.2)。
     */
    void leaveSession(UUID player, LeaveReason reason);

    // ── 会话事件订阅(★v1.1-D3;★43) ────────────────────────────────────────

    /**
     * 订阅会话事件(八事件快照族,§3.1)。返回句柄,{@code close()} 注销(句柄纪律
     * 见 {@link EventSubscription});订阅者异常被隔离(WARN 后其余订阅者照常)。
     * 顺序承诺:单会话内事件有序;跨插件订阅者间不承诺送达顺序。快照不可变、
     * 不含活会话/活组件引用;实例事件不经本面暴露。
     */
    EventSubscription subscribeSessionEvents(Consumer<SessionEventView> handler);

    // ── 地图注册(★v1.2;★54) ───────────────────────────────────────────────

    /**
     * 注册地图(声明 = 纯静态 {@link MapDefinition} 直接交成品;★v1.8 按路径直交)。
     * 注册期校验<b>响亮抛</b>(20-D3):id 查重(id = 声明目录名,路径派生,同一路径
     * 再注册必撞)、世界目录存在性(缺失/非目录抛带完整绝对路径;先落盘后注册,
     * 14.39)。成功返回 {@link MapInfo} 并进 {@link #maps()} 目录。主线程调用。
     */
    MapInfo registerMap(org.bukkit.plugin.Plugin provider, MapDefinition map);

    /**
     * 摘除地图(20-D4)。该图为任一存活会话当前实例所用 → 摘除被拒(detail 点名
     * 会话;图随会话打完自然退出,或 dissolve 后再摘)。已不在目录 = no-op(幂等)。
     * 提供方插件 disable → 框架兜底摘除(正用则保留并 WARN)。主线程调用。
     */
    void unregisterMap(org.bukkit.plugin.Plugin provider, String mapId);

    // ── 模式显式注册(★v1.6;24 号修正案:ServicesManager 提供方通道退场) ──

    /**
     * 注册模式工厂(★v1.6):<b>注册工厂,不是注册成品</b>(§13.4)——聚合根由框架
     * 以自己的配置源 {@code build(cfg)} 产出,工厂不持成品状态。注册闸(build →
     * verifyDependencies → id 查重)<b>全响亮</b>(坏声明/重复 id 原样抛;注册失败 =
     * 拒载信号);成功返回快照并进 {@link #modes()} 目录。{@code provider} 归属簿记:
     * {@link #unregisterMode} 只能摘自己注册的 id。主线程调用。
     */
    MatchModeInfo registerMode(org.bukkit.plugin.Plugin provider, MatchModeProvider factory);

    /**
     * 摘除本提供方注册的模式(★v1.6;归属簿记在框架侧)。id 属其他提供方(含出厂
     * 模式)→ {@code IllegalStateException} 响亮拒;id 不存在 → 幂等 no-op。摘除后走
     * <b>摘除回退编排</b>(§13.4/§3.2):当前模式 ∈ 被摘集的存活会话——
     * {@code session.fallback-mode} 已配置且已注册 → force 切换回退(豁免 min-dwell,
     * §3.5);否则 {@code FALLBACK_DISSOLVE} 解散;回退被拒 WARN 放弃不自旋(§14.17)。主线程调用。
     */
    void unregisterMode(org.bukkit.plugin.Plugin provider, String modeId);

    // ── 会话组件显式注册(★v1.6;D14 通道一的显式面) ─────────────────────

    /**
     * 注册会话组件贡献(★v1.6;D14 通道一,§7.3「受限开放」):提供宿主级会话策略
     * (自定义重连/宽限/准入记账等);<b>模式仍无权贡献</b>、无运行期增删(贡献只发生在
     * 会话创建时)。干跑合并校验(id 查重/必需依赖)失败<b>响亮上抛</b>(注册失败 =
     * 拒载信号);通过后所有后续 {@link #createSession} 合并其贡献(注册序)。同一
     * 提供方重复注册 = 幂等重收录。主线程调用。
     */
    void registerSessionComponents(org.bukkit.plugin.Plugin provider,
                                   List<SessionComponent.Specification<?>> specs);

    /**
     * 摘除本提供方的全部会话组件贡献(★v1.6;幂等——未注册 = no-op):后续会话
     * 不再合并;已装配会话不受影响(无运行期拆卸,随 dissolve 自然消亡,D14)。
     * 主线程调用。
     */
    void unregisterSessionComponents(org.bukkit.plugin.Plugin provider);

    // ── 目录(顺序 = 注册序,不排序不过滤、无特判——14.21;框架不设出厂模式,21-D3) ──

    /** 全部已注册模式快照(注册序,含第三方模式;空表合法(0 个模式属正常))。主线程调用。 */
    List<MatchModeInfo> modes();

    /** 全部已注册地图快照(注册序;空表合法)。主线程调用。 */
    List<MapInfo> maps();

    /** 按模式 id 取快照;缺失 → empty(不抛)。主线程调用。 */
    Optional<MatchModeInfo> mode(String modeId);
}
