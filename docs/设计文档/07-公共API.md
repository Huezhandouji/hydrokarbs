## 7. 对外公共 API:`MiniGameApi`(v2 新增,v3 修订注册面;★v1.6 显式注册收敛;★v1.7 能力标签机制退场;★v1.8 地图声明按路径直交——方法面零变化;★v1.10/28 号(ADR-0016)旧属性袋退场,MapDefinition 三字段定案;★v1.11/29 号(ADR-0017)属性袋纯容器回归,声明词料三件退场;★v1.13/31 号(ADR-0019)缺省去向管线退场——API_VERSION 7→8,MatchModeInfo 快照 9→8 分量;★v1.14/32 号(ADR-0020)API 版本协商机制退场——常量与 SPI 版本声明删除,方法面零变化)

> HydroKarbs MiniGame Framework · v1.14(2026-11-06,修正案 32 号/ADR-0020:API 版本协商机制退场;v1.13 于 2026-10-30 修正案 31 号/ADR-0019:会话模式实例不变式与缺省切换管线退场;v1.12 于 2026-10-29 修正案 30 号:JoinDecision 二值化与 SPECTATE 退场——本篇零改动;v1.11 于 2026-10-28 修正案 29 号/ADR-0017:属性袋纯容器回归与声明词料三件退场;v1.10 于 2026-10-28 修正案 28 号/ADR-0016:旧属性袋退场与 MapDefinition 三字段定案;v1.8 于 2026-10-28 修正案 26 号;v1.7 于 2026-10-28 能力标签机制退场,25 号;v1.6 于 2026-10-27 显式注册收敛,24 号;v1.5 于 2026-10-27 REUSE_DIRTY 副本随会话解散销毁,23 号——公共面零改动;v1.4 于 2026-10-27 API 面收敛与地图注册通道收敛,22 号;v1.3 于 2026-10-25 框架基础设施瘦身与配置中立化,21 号;v1.2 于 2026-10-23 地图注册通道;v1.1 于 2026-10-23 框架独立插件化拆分与 API 面扩充;v1.0 于 2026-10-22 自 SHDFMatch 设计案 v4.1 剥离玩法重构;玩法实现见 Shdf具体玩法设计案/)
>
> 文档版本:v1.14(修正案 32 号/ADR-0020,2026-11-06,**API 版本协商机制退场**——§7.1 契约块删 `int API_VERSION = 8;` 行与 D7 版本协商 javadoc、版本数字轴改历史框架注;§7.2 冻结行补 ★v1.14(BASELINE 删 2 行合法重录)、值对象行 v1.13 重编译句改「按 v1.13 面重编译」、**版本协商行整行改历史口径(退场)**;§7.5 表「版本」行改在场检查口径、示例一版本检查改 api 在场检查;修正案 32 号(ADR-0020)为授权与落盘凭据);v1.13(修正案 31 号/ADR-0019,2026-10-30,**会话模式实例不变式与缺省切换管线退场**——§7.1 契约块 API_VERSION 7→**8**、方法面零变化(17 方法不动,以现行 `MiniGameApi.java` 实签转写);`MatchModeMeta` 缺省去向分量与嵌套枚举 `DefaultTransition` 退场,快照 `MatchModeInfo` 删第九分量(9→8 分量),`MiniGameApiImpl#toModeInfo` 同步;§7.1 示例一版本数字随改;§7.2 冻结行(FROZEN 17 行不动、WHITELIST/BASELINE 合法重录)、值对象行(MatchModeInfo 八分量)、版本协商行(v1.13 = 8;经用户裁决不设双轨——第 4 次)随改;修正案 31 号(ADR-0019)为授权与落盘凭据);v1.11(修正案 29 号/ADR-0017,2026-10-28,**属性袋纯容器回归与声明词料三件退场**——§7.1 契约块 API_VERSION 维持 7、方法面零变化(17 方法不动,以现行 `MiniGameApi.java` 实签转写);`MapDefinition` 第三分量改为 `AttributeBag` 纯容器 + `TAGS` 约定键 + `tags()` 唯一标签读口;`Object metadata`/原标签提取接口/原标签门面三件删除;registerMap 契约行改「词料容器构造即冻结、非空」;§7.2 冻结行/值对象行/版本协商行与 §7.5 地图行随改;修正案 29 号(ADR-0017)为授权与落盘凭据);v1.10(修正案 28 号/ADR-0016,2026-10-28,**属性袋退场,MapDefinition 三字段定案**——§7.1 契约块 API_VERSION 维持 7、方法面零变化(17 方法不动,以现行 `MiniGameApi.java` 实签转写);MapDefinition 形状行按现行实签重录为 `(worldDirectoryPath, ResetStrategy resetStrategy, Object metadata)` + 二参便利构造缺省 `REUSE_DIRTY`;原标签门面收为 `tags(Object)` 读口 + 单方法标签提取约定(约定键/写口/冻结两口随旧属性袋退场);registerMap 契约行去「metaAttributes 构造期冻结」句,改「metadata 按引用存储、视为冻结」;§7.2 冻结行/值对象行/版本协商行与 §7.5 地图行随改;修正案 28 号(ADR-0016)为授权与落盘凭据);v1.8(2026-10-28,修正案 26 号:**地图声明按路径直交 + MapDefinition 瘦身**——§7.1 契约块 API_VERSION 6→7、方法面零变化(17 方法不动,以现行 `MiniGameApi.java` 实签转写);registerMap 契约行按新语义重写(id = 声明目录名路径派生/世界目录存在性直验声明自带路径、模板根约定退场/声明 bag 冻结脱钩);§7.1 第二代码块增 MapDefinition 两分量形状行与约定键注(按现行实签转写);§7.2 冻结行与值对象行加 ★v1.8 注(FROZEN 行不动、BASELINE 重录:MapInfo 收缩为 id/tags/resetStrategy)、版本协商行版本链随改(v1.8 = 7);§7.5 地图注册行与示例一版本数字随改;v1.7(2026-10-28,修正案 25 号:**能力标签机制退场**——§7.1 接口块升 v6:API_VERSION 5→6、冻结面 19→17 方法(-`registerCapability`/`capabilities` 能力两法,以现行 `MiniGameApi.java` 实签转写),能力标签全链退场为历史口径;§7.2 冻结行计数与版本协商行随改(外部能力 = 会话组件桥接,§7.3 注);§7.3 一句话注桥接新形态;§7.5 能力标签行改历史口径、会话组件贡献行与示例一面版本数字随改、增量薄委托注 capability 项历史口径;v1.6 于 2026-10-27,修正案 24 号:**显式注册收敛**——§7.1 接口块升 v5:API_VERSION 4→5、冻结面 15→19 方法(+`registerMode`/`unregisterMode`/`registerSessionComponents`/`unregisterSessionComponents` 四法,以现行 `MiniGameApi.java` 实签转写),跨插件 ServicesManager 提供方通道退场(`SessionComponentProvider` SPI 接口删除、`ProviderServiceListener` 整体删除、`MatchModeRegistry` 的 ServicesManager 半边退场),注册期双口径合一响亮;§7.2「第三方注册模式」行改显式注册四法口径、SPI 注册行标注历史口径;§7.3 贡献通道一表述改「全局贡献 `registerSessionComponents`」;§7.5 会话定制行与示例一版本数字随改;v1.4 于 2026-10-27,修正案 22 号:API 面收敛与地图注册通道收敛——两参 `createSession(String,String)` 删除,`createSession(SessionRecipe)` 为公共 API 建会话唯一形态,API_VERSION=4、冻结面 16→15(§7.1/§7.2);地图注册收敛为唯一注册通道,文件式装载退场,§7.1/§7.5 措辞随改;§7.1 代码块随施工实况逐行对正——api 侧会话快照定名 `SessionInfo`(阶段 7 README §5,2026-10-25)、stats/top 两方法悬置不在本面(回填裁决 21 §6-Q1);v1.3 于 2026-10-25,框架基础设施瘦身——「查战绩」面随 R1 悬置(§7.1/§7.5 注;API_VERSION 维持 3,方法集不变),白名单反面词表历史口径注(§7.2),21-D1;v1.2 于 2026-10-23 地图注册通道——§7.1 增 registerMap/unregisterMap、API_VERSION=3、§7.5 权限面加行;v1.1 于 2026-10-23 框架独立插件化拆分与 API 面扩充——§7.1 升 v2、§7.2 加三行、§7.3 SPI 未决项由 D14 裁决关闭、新增 §7.5 跨插件消费;v4.1 于 2026-10-21 进程级服务层与 ServiceRegistry;会话级组件层——两级挂载;新增 §7.3、§7.4)。

框架化的最后一块拼图:本框架**自己也要作为一个平台**被别的插件消费——与它消费任一宿主服务的 ServicesManager 是同一机制。同一套纪律,换个方向(框架自身消费宿主服务的实例属 SHDF 扩展线,见 Shdf具体玩法设计案/)。

### 7.1 接口(小而深;方法集冻结;★v1.1:v2 增量,只增不改;★v1.2:v3 增量;★v1.4:v4 收敛——两参 createSession 删除,冻结面 16→15;★v1.6:v5 增量——四法显式注册,冻结面 15→19,ServicesManager 提供方通道退场;★v1.7:v6 收敛——能力标签两法删除,冻结面 19→17,外部能力 = 会话组件桥接;★v1.8:v7——方法面零变化,地图声明按路径直交;★v1.11:v7 维持——第三分量 = `AttributeBag` 纯容器,标签读口 = `tags()`;★v1.13:v8——方法面零变化,缺省去向管线退场,`MatchModeInfo` 9→8 分量;★v1.14:API_VERSION 常量与版本协商面退场,方法面维持 17 法)

```java
package com.hydrokarbs.api;

/** 本框架的对外公共面。经 Bukkit ServicesManager 暴露(MiniGameApi 自身的注册
 *  仍是 ServicesManager;★v1.6:第三方经本面的注册/贡献**不再走 ServicesManager
 *  提供方通道**——模式/会话组件改经本面四法显式注册,24 号)。
 *  ★v1.14(32 号):API 版本协商面退场——API_VERSION 常量与 MatchModeProvider
 *  的 requiredApiVersion() 声明删除(数字轴 1~8 转 CONTEXT「历史术语」);消费方
 *  onEnable 首行仅查 api 在场(缺席 → SEVERE + setEnabled(false),拒载不崩服)。
 *  历史版本注(冻结面沿革;数字轴已于 ★v1.14 终结):v1.0 = 1;v1.1 = 2;
 *  v1.2 = 3;v1.3 维持 3(战绩面悬置,21-D1);
 *  v1.4 = 4(22 号:两参 createSession 删除);
 *  v1.6 = 5(24 号:显式注册收敛——冻结面 15 → 19 方法,ServicesManager 提供
 *  方通道退场);
 *  ★v1.7(25 号)= 6:能力标签机制退场——★v1.1-D4 能力标签两法与全链字符串
 *  标签机制(注册表/A2 校验/模式与快照分量)删除;外部能力改由会话组件桥接表达
 *  (§7.3 注);冻结面 19 → 17 方法 = 查询 3 + 控制 1 + 建会话 1 + 成员 2 +
 *  订阅 1 + 地图 2 + 模式显式注册 2 + 会话组件显式注册 2 + 目录 3;「只增不改」
 *  由 17 方法清单续行守卫(★44 ApiSurfaceTest / MiniGameApiSurfaceTest 反射冻结,
 *  FROZEN 17 行、BASELINE 随本裁决合法重录)。
 *  ★v1.8(26 号)= 7:地图声明按路径直交 + MapDefinition 瘦身——方法面零变化
 *  (17 方法签名一字不动);「模板寄居框架数据目录 maps/templates/」约定退场,
 *  MapDefinition 自带 worldDirectoryPath,注册校验 = 世界目录存在且为目录、查重
 *  统一到路径派生 id(版本常量随裁决合法更新:FROZEN 17 行不动、BASELINE 重录)。
 *  ★v1.10/28 号(ADR-0016,2026-10-28)= 7(维持):旧属性袋退场,MapDefinition 定案
 *  三字段(第三分量 Object);★v1.11/29 号(ADR-0017)= 7(维持):第三分量改为
 *  AttributeBag 纯容器,标签经 MapDefinition.TAGS + tags() 读取(缺省空集)、
 *  resetStrategy 缺省 REUSE_DIRTY;方法面零变化,BASELINE 随声明形状合法重录。
 *  ★v1.13/31 号(ADR-0019)= 8:缺省去向管线退场——MatchModeMeta 的
 *  defaultTransition 分量与嵌套枚举 DefaultTransition 删除,快照 MatchModeInfo
 *  的同名分量同步删除(9 → 8);无显式去向的结束一律回落 session.fallback-mode
 *  (默认 = 出厂空模式兜底)。方法面零变化;删公共快照分量 = 跨 jar 破坏
 *  (与删公共方法同构),版本常量升以锚定;经用户裁决不设双轨过渡期
 *  (第 4 次,同 ★v1.4/★v1.6/★v1.7 先例);WHITELIST/BASELINE 随分量删除合法重录。
 *  线程契约:除 switchMode(任意线程发起,Impl 内部回主线程,future 主线程完成,
 *  §14.1)与订阅回调(事件发布线程)外,各方法均主线程调用(§10.5)。 */
public interface MiniGameApi {

    // ── 查询(不可变快照;现取不缓存,14.31) ─────────────────
    //    api 侧快照 record 定名 SessionInfo(与 MatchModeInfo/MapInfo 命名对齐;
    //    阶段 7 README §5 定名裁决,2026-10-25——原骨架名 SessionView 让位
    //    engine/port/SessionView,消歧见 §7.3)。
    Optional<SessionInfo> sessionOf(UUID player);
    Optional<SessionInfo> session(String sessionId);
    List<SessionInfo> sessions();

    // ── 控制(与 /sm 命令同权) ────────────────────────────────
    /** 切换某会话的模式。就是 SessionService#switchMode 的受控暴露(§3.2)。 */
    CompletableFuture<SwitchResult> switchMode(String sessionId, MatchModeSelection target, Handoff payload);
    /** 创建会话(★v1.1-D14 通道二:会话配方,单次贡献会话组件),见 §7.5。
     *  ★v1.4(22 号):本方法是公共 API 建会话的唯一形态——两参重载已删除,
     *  调用方迁移 SessionRecipe 形态(等价空贡献 = SessionRecipe.of(modeId, mapId))。
     *  失败语义(§7.3):initialModeId 未注册 / 组件 id 查重冲突 / 缺必需依赖
     *  → 响亮抛,会话不创建、不留半装配态(与既有会话创建语义一致)。主线程调用。 */
    SessionInfo createSession(SessionRecipe recipe);

    // ── 成员操作(★v1.1-D2;★42) ────────────────────────────
    /** 将玩家加入会话(= SessionService#join 的受控暴露,§3.2;含 JoinAdmission 裁决)。主线程调用。 */
    JoinResult joinSession(String sessionId, UUID player);
    /** 将玩家移出其当前会话;不在任何会话 = no-op(幂等)。主线程调用。 */
    void leaveSession(UUID player, LeaveReason reason);

    // ── 会话事件订阅(★v1.1-D3;★43) ─────────────────────────
    /** 订阅会话事件(八事件快照族,§3.1/D3)。返回句柄,close() 注销;订阅者异常被隔离(镜像总线既有隔离)。 */
    EventSubscription subscribeSessionEvents(Consumer<SessionEventView> handler);

    // ── 能力标签(★v1.1-D4;★v1.7 已删除) ────────────────────
    // ★v1.7(25 号):原 registerCapability(Plugin, String) / capabilities() 两法
    //   随能力标签机制全链退场删除(用户裁决:字符串契约不严谨)——此段为历史
    //   口径注,不再构成可调用的公共面方法。外部能力新形态 = 会话组件桥接:
    //   提供方(或宿主自持防腐层)经 registerSessionComponents/SessionRecipe
    //   贡献类型化桥接会话组件,消费方组件 requires/requiresOptional 类型化声明
    //   依赖,装配期响亮、运行期 quarantine(机制在框架内既有;见 §7.3 注、★21/★60)。

    // ── 地图注册(★v1.2;★54;★v1.8 声明按路径直交) ──────────
    /** 注册地图(声明 = 纯静态 MapDefinition,直接交成品——与模式必须交工厂相对,20-D1;
     *  ★v1.8:声明按路径直交,「模板寄居 maps/templates/」约定退场)。注册期校验
     *  (响亮抛,20-D3):id 查重(id = 声明目录名,路径派生;同一路径再注册必撞,
     *  重复点名先到者)、世界目录存在性(声明自带 worldDirectoryPath 存在且为目录,
     *  缺失/非目录响亮抛带完整绝对路径;14.39:先落盘世界目录 → 构造 MapDefinition
     *  → registerMap)。★v1.11/29 号(ADR-0017):`metadata` 为 `AttributeBag` 纯容器
     *  (非空——无词料传 `AttributeBag.empty()`;构造即冻结),标签经 `MapDefinition#tags()`
     *  唯一读口提取。成功返回 MapInfo
     *  快照并进 maps() 目录,可被 switchMode
     *  选中。主线程调用。 */
    MapInfo registerMap(org.bukkit.plugin.Plugin provider, MapDefinition map);
    /** 摘除地图(20-D4)。该图为任一存活会话当前实例所用 → 摘除被拒(detail 点名
     *  会话——图随会话打完自然退出,或 dissolve 后再摘;这是特性不是障碍)。
     *  幂等:已不在目录 = no-op。提供方插件 disable → 框架兜底摘除(正用则保留并
     *  WARN)。主线程调用。 */
    void unregisterMap(org.bukkit.plugin.Plugin provider, String mapId);

    // ── 模式显式注册(★v1.6;24 号:ServicesManager 提供方通道退场) ──
    /** 注册模式工厂(★v1.6 显式注册通道;07 §7.2 表「第三方注册模式」行的本面落点)。
     *  注册工厂,不是注册成品(§13.4):MatchModeProvider 交声明不交实例——聚合根
     *  由框架以自己的配置源 build(cfg) 产出(modes/<id>.yml 的生命周期归框架,§10.2;
     *  工厂不持成品状态),注册闸(build → verifyDependencies → id 查重)全响亮:
     *  坏声明/重复 id 原样抛给调用方(onEnable 注册失败 = 拒载信号;原运行期事件回调
     *  的「不上抛」口径随 ServicesManager 通道退场)。成功返回 MatchModeInfo 快照
     *  并进 modes() 目录,可被 switchMode 选中。A9 三限制见 §7.2 注。
     *  provider 用于归属簿记:disable 兜底按提供方摘除(14.38);unregisterMode 只能
     *  摘自己注册的 id。主线程调用。 */
    MatchModeInfo registerMode(org.bukkit.plugin.Plugin provider, MatchModeProvider factory);
    /** 摘除本提供方注册的一个模式(★v1.6)。归属簿记在框架侧:只准摘自己注册的 id
     *  ——id 属于其他提供方(含本 jar 出厂模式)→ IllegalStateException 响亮拒;
     *  id 不存在 → 幂等 no-op。摘除后执行摘除回退编排(§13.4/§3.2 二级):逐存活
     *  会话当前模式 ∈ 被摘集——session.fallback-mode 已配置且已注册 → force 切换
     *  回退(豁免 min-dwell,§3.5);否则 FALLBACK_DISSOLVE 解散;回退被拒
     *  (SWITCH_IN_PROGRESS)WARN 放弃不自旋(§14.17)。主线程调用。 */
    void unregisterMode(org.bukkit.plugin.Plugin provider, String modeId);

    // ── 会话组件显式注册(★v1.6;D14 通道一的显式面) ─────────
    /** 注册会话组件贡献(★v1.6 显式注册通道;D14 通道一,07 §7.3「受限开放」):
     *  提供宿主级会话策略(自定义重连/宽限/准入记账等),模式仍无权贡献(v4-D4
     *  不变)、无运行期增删(贡献只发生在会话创建时,装配点唯一不变)。干跑合并
     *  校验(组件 id 查重/必需依赖可满足)失败响亮上抛给调用方(显式注册可捕获;
     *  注册闸只有一份——原事件通道「SEVERE 拒收录、会话照常」口径随通道退场);
     *  通过后所有后续 createSession 合并其贡献(注册序)。同一提供方重复注册 =
     *  幂等重收录(旧条目先摘再干跑,不留半收录态)。主线程调用。 */
    void registerSessionComponents(org.bukkit.plugin.Plugin provider,
                                   List<SessionComponent.Specification<?>> specs);
    /** 摘除本提供方的全部会话组件贡献(★v1.6;幂等——未注册 = no-op):后续会话
     *  不再合并;已装配会话不受影响(无运行期拆卸,随 dissolve 自然消亡,D14)。
     *  主线程调用。 */
    void unregisterSessionComponents(org.bukkit.plugin.Plugin provider);

    // ── 目录(顺序 = 注册序,不排序不过滤——14.21「大厅特判」反弹规避) ─────
    //    第三方模式经 registerMode 显式注册(★v1.6;原 ServicesManager 收录口径
    //    退场)入目录后服主经本组方法可见,与其他模式同列、无特判(§7.2/§13.4;
    //    框架不设出厂模式,21-D3)。
    List<MatchModeInfo> modes();
    /** 全部已注册地图快照(注册序;空表合法)。主线程调用。 */
    List<MapInfo> maps();
    /** 按模式 id 取快照;缺失 → empty(不抛)。主线程调用。 */
    Optional<MatchModeInfo> mode(String modeId);

    // ── 战绩 ─────────────────────────────────────────────────
    // ★v1.3(21-D1/R1):本族随持久化出框架而【悬置】——归宿主线消费,冻结面
    //   不声明 stats/top 两方法(签名类型 PlayerStats/LeaderboardRow 原住 L5
    //   data/model,已随 L5 出框架;缺席 = 7.1 施工落定口径)。缺席语义与类型
    //   归宿的回填裁决登记 21 §6-Q1,待宿主线持久化落定后回填。
}
```

```java
package com.hydrokarbs.api;

// api/SessionEventView —— ★v1.1-D3:不可变快照族(八事件各一 record:§3.1 七事件
// 载荷 from/to、reason、时间戳 + ComponentQuarantined;不含活会话/活组件引用——
// 镜像 ★29 现取不缓存纪律)
public sealed interface SessionEventView {
    record Created(SessionId session, long tick) implements SessionEventView {}
    record MemberJoined(SessionId session, UUID player, long tick) implements SessionEventView {}
    record MemberLeft(SessionId session, UUID player, String reason, long tick) implements SessionEventView {}
    record MemberReconnected(SessionId session, UUID player, long tick) implements SessionEventView {}
    record ModeSwitchStarted(SessionId session, String from, String to, long tick) implements SessionEventView {}
    record ModeSwitchCompleted(SessionId session, String from, String to, boolean ok, long tick) implements SessionEventView {}
    record Dissolved(SessionId session, String reason, long tick) implements SessionEventView {}
    record ComponentQuarantined(SessionId session, String componentId, String cause, long tick) implements SessionEventView {}
}

// api/EventSubscription —— ★v1.1-D3:订阅句柄(close() 注销;句柄只在插件实例字段中持有,onDisable 显式 close,14.38)
public interface EventSubscription extends AutoCloseable { @Override void close(); }

// api/SessionComponentProvider —— ★v1.6(24 号):【已删除】——原 ★v1.1-D14 通道一
//  的全局默认贡献 SPI(经 ServicesManager 注册、ServiceRegisterEvent 收录、坏
//  provider SEVERE 拒收录会话照常)随显式注册收敛整体退场;全局贡献改经
//  MiniGameApi#registerSessionComponents(Plugin, specs) 显式注册(干跑合并校验
//  失败响亮上抛给调用方)。此行为历史口径注,不再构成可 import 的公共类型。

// api/SessionRecipe —— ★v1.1-D14 通道二:单次贡献(★v1.4:两参重载已删除,
// 本 record 是 createSession 的唯一参数形态)
public record SessionRecipe(String initialModeId, Optional<String> initialMapId,
                            List<String> mapPool,
                            List<SessionComponent.Specification<?>> extraComponents) {
    public static SessionRecipe of(String modeId, String mapId) { /* mapPool/extra 空表;mapId null 视同未钉图 */ }
}

// map/MapDefinition —— ★v1.11/29 号(ADR-0017):三字段,按现行实签转写(原 id/displayName/
// tags/bounds/spawns/regions/interactPoints 等结构分量早在 26 号随瘦身删除;第三分量为
// AttributeBag 纯容器;resetStrategy 为强类型字段——id 由声明路径派生:目录查重/副本命名/
// SessionRecipe 钉图键统一到本派生;结构合法性由调用方构造期自理)
public record MapDefinition(Path worldDirectoryPath, ResetStrategy resetStrategy, AttributeBag metadata) {
    /** 标签词料键:名字 "tags",值类型 Set<String>。 */
    public static final AttributeBag.Key<Set<String>> TAGS = AttributeBag.Key.of("tags", stringSetType());
    public MapDefinition { /* 构造即权威:路径 toAbsolutePath().normalize() 规范化入库;
                                缺目录名(id 无从派生)响亮抛;resetStrategy/metadata 非空;
                                metadata 为纯容器,构造即冻结(无词料传 AttributeBag.empty()) */ }
    /** 二参便利构造:resetStrategy 缺省 REUSE_DIRTY;第二参是词料容器,不是策略。 */
    public MapDefinition(Path worldDirectoryPath, AttributeBag metadata) { ... }
    /** 地图 id = 声明目录名(路径派生)。 */
    public String id() { /* worldDirectoryPath.getFileName() */ }
    /** 全框架唯一标签读口:TAGS 缺失或值不是 Set → 空集。 */
    public Set<String> tags() { /* metadata.getOrDefault(TAGS, Set.of()) */ }
}

// engine/AttributeBag —— ★v1.11/29 号(ADR-0017):声明词料纯不可变容器
//   Key<T>(name, type) 二元组(无命名空间)/ Builder 唯一写侧 / Map.copyOf 收口 /
//   get、getOrDefault、has 三读口 / empty() 共享空实例
// 原标签提取接口与原标签门面 —— 两件均不存在(29 号/ADR-0017 退场)
```

> ★v1.1-D3 边界:**实例事件(`MatchModeInstanceEvent`)第一版不经此面暴露**——玩法侧组件经自身实例总线已有全量;跨插件订阅实例事件登记为未决(19-§7-Q5,看板类需求先验证会话快照 + D3 是否够用),不裁。顺序承诺:单会话内事件有序;跨插件订阅者间不承诺送达顺序。`MiniGameApiImpl` 增量实现均为薄委托:`joinSession/leaveSession → SessionService`;`subscribeSessionEvents → 各 Session.events() 聚合 + 会话注册表遍历挂接(新会话创建时自动接入)`;`registerCapability → CapabilityRegistry`(★v1.7 历史口径:该委托随能力两法删除,25 号);**★v1.6 增量(24 号)**:`registerMode/unregisterMode → MatchModeRegistry#registerProvider/unregister`(响应亮闸,归属簿记 `modeClaims` 在 Impl 伴生,镜像原 `capabilityClaims` 先例——该簿记随能力标签机制退场删除,★v1.7);`registerSessionComponents/unregisterSessionComponents → SessionComponentContributionRegistry`(干跑合并校验响亮上抛);**摘除回退编排自原 `ProviderServiceListener` 迁入 Impl**(通道退场,编排逻辑一份不写两处)。

### 7.2 注册、冻结与扩展点

| 事项 | 做法 |
|---|---|
| 注册 | `onEnable`:`getServer().getServicesManager().register(MiniGameApi.class, impl, this, ServicePriority.Normal)`——与消费任一宿主服务的 ServicesManager 同一机制,对称 |
| 冻结 | `MiniGameApiSurfaceTest` 反射冻结方法集(同款"服务面冻结测试"在 SHDF 扩展线的先例 `RoleApiSurfaceTest`,见 Shdf具体玩法设计案/):删/改签名即测试红。**冻结面 17 方法 = 查询 3 + 控制 1 + 建会话 1(`SessionRecipe` 唯一形态)+ 成员 2 + 订阅 1 + 地图 2 + 模式显式注册 2(`registerMode`/`unregisterMode`)+ 会话组件显式注册 2(`registerSessionComponents`/`unregisterSessionComponents`)+ 目录 3**(★v1.4/22 号:16 → 15,两参 `createSession` 删除属破坏性收敛;★v1.6/24 号:15 → 19,四法显式注册——ServicesManager 提供方通道退场,`SessionComponentProvider` SPI 删除属破坏性变更,未设双轨窗口;**★v1.7/25 号:19 → 17,能力标签两法 `registerCapability`/`capabilities` 删除(用户裁决:字符串契约不严谨),外部能力 = 会话组件桥接(§7.3 注);冻结测试方法清单与 API_VERSION 随裁决合法更新,FROZEN 17 行、BASELINE 重录;**★v1.8/26 号:方法面零变化 = FROZEN 17 行不动,但 MapInfo 收缩(id/tags/resetStrategy)、MapDefinition 两分量——BASELINE 合法重录(背书 = 26 号修正案);计数不含悬置的战绩两方法,见 §7.1 注**;**★v1.10/28 号(ADR-0016,2026-10-28):旧词料容器整类退场,再次方法面零变化 = FROZEN 17 行仍不动;BASELINE 再录(删旧容器七行与各端口 `attrs()`/`MatchModeInstanceView#attr()` 行,MapDefinition 三分量行,增原标签提取接口一行);★v1.11/29 号(ADR-0017,2026-10-28):第三分量改 `AttributeBag` 纯容器,方法面仍零变化 = FROZEN 17 行不动;BASELINE 再录(增回 `AttributeBag` 及其 `Key`/`Builder`/`empty`/三读口,删原标签提取接口一行,MapDefinition 第三分量类型改 `AttributeBag`)——背书 = ADR-0017**;★v1.13/31 号(ADR-0019,2026-10-30):缺省去向管线退场——方法面仍零变化 = FROZEN 17 行不动;WHITELIST 删 `DefaultTransition.class`、BASELINE 删 7 行冻结签名(`MatchModeMeta` 三分量构造/访问器族与 `MatchModeInfo` 分量族)后合法重录(背书 = ADR-0019);`API_VERSION` 7→8;★v1.14/32 号(ADR-0020,2026-11-06):API 版本协商面退场——BASELINE 删 `#API_VERSION:int` 与 `#requiredApiVersion():int` 两行后合法重录(背书 = ADR-0020),FROZEN 17 行仍不动** |
| 值对象 | `SessionInfo` / `MatchModeInfo` / `MapInfo` 全是不可变 record,不泄漏引擎内部可变状态(`SessionInfo` 是快照,不含活实例引用;api 侧原骨架名 `SessionView` 施工定名让位——阶段 7 README §5,2026-10-25,见 §7.3)。**★v1.8/26 号:`MapInfo` 收缩为 `(id, tags, resetStrategy)` 三分量**——id = 声明目录名(路径派生),tags = 声明 `MapDefinition#tags()` 唯一读口(`TAGS` 约定键,缺省空集)、resetStrategy = 声明字段(★v1.11/29 号(ADR-0017):词料容器与标签读口定案;枚举存 `name()`,枚举不进冻结面);原 `displayName`/`world` 分量随 MapDefinition 瘦身删除(声明文件系统路径不进冻结面);**★v1.13/31 号(ADR-0019):`MatchModeInfo` = 八分量 `(id, displayName, description, icon, mapTags, recordsHistory, minParticipants, maxParticipants)`——第九分量 `defaultTransition` 随缺省去向管线退场删除(消费过该分量的旧编译物需按 v1.13 面重编译——API 版本常量已随 ★v1.14 退场)**;MapDefinition 形状行见 §7.1 第二代码块(按现行实签转写) |
| **第三方注册模式** ★v3;**★v1.6 显式注册口径(24 号)** | 注册面 = **`MiniGameApi#registerMode(Plugin, MatchModeProvider)`**(本面四法之一)——聚合根是 final 类不是接口,Builder 即声明语言,工厂接口即注册载荷;**注册工厂,不是注册成品**(§13.4)。注册闸(build → verifyDependencies → id 查重)**全响亮**:坏声明/重复 id 原样抛给调用方(onEnable 注册失败 = 拒载信号;显式调用方可捕获,原「运行期事件回调不上抛」口径随通道退场);成功返回 `MatchModeInfo` 并进 `modes()` 目录。摘除 = `unregisterMode(Plugin, modeId)`——只准摘自己注册的 id(越权/含本 jar 出厂模式 → 响亮拒;不存在 → 幂等 no-op),摘除后执行**回退编排**:`session.fallback-mode` 已配置且已注册 → force 切换(豁免 min-dwell);否则 `FALLBACK_DISSOLVE` 解散(§13.4/§3.2 二级;回退被拒 WARN 放弃不自旋)。**注册时序反转(★v1.6)**:第三方必须 `depend: [HydroKarbs]`、`load(MiniGameApi)` 检查版本后**在 onEnable 里显式登记**——原「先启补收录」(`ServiceRegisterEvent` 动态收录,兼容先启后注册的时序)随 ServicesManager 提供方通道退役为历史口径(§13.4;`MatchModeRegistry` 的 ServiceLoader 半边——本 jar `META-INF/services` 出厂模式——保留不动)。**别的插件不改我们的 jar 就能加模式**,这是"框架"二字成立的验收标准(同款"装配即校验"纪律的先例 `RoleLoader#loadInto` 属 SHDF 扩展线,见 Shdf具体玩法设计案/) |
| 第三方组件 | 不开放。第三方模式复用我们已发布的 L3 组件(`MatchModeComponent` 基类与 `Specification` 在 api 包导出稳定副本)+ 模式包内自建私有组件 |
| **API 编译面白名单** ★v1.1(D6) | **不移动引擎类型进 api/**(避免 engine→api 依赖倒挂),改为白名单冻结:玩法/伴随插件 compileOnly 依赖框架 jar 时允许 import 的包集 = `api/**`、`engine.mode/**`、两级组件基类(`MatchModeComponent`/`SessionComponent` + Specification/Factory,不含 Registry)、`engine.session` 值类型(Handoff/EndResult/TransitionIntent/MatchModeSelection/SwitchResult/JoinResult/LeaveReason/SessionId/DissolveReason)、`engine.port` 契约、`engine.event/**`、`component/**` 各包公开数据件与事件类型、`map/` 读面(逐包清单落 7.5 施工文档)。**机械守卫**:`ApiSurfaceTest` 双断言(★44)——①`MiniGameApi` 全部方法签名只引用白名单类型;②白名单内类型的公开签名**只增不改**(反射快照)。**永不导出**(白名单反面,断言显式列出):`SessionService(Impl)`、`EngineTicker`、`WorldPort`、`ServiceRegistry` 族、`ComponentRegistry` 族、`bridge/**`、各 Impl(★v1.3:原反面词 `PersistencePort`/`data/**` 为历史口径——持久化已整体出框架基线,该包不存在、断言空转不减,21-D1)。跨 jar 场景下进程内全权访问特权仅保留给形态 A 的宿主 L0 装配/监听/命令(彩排纪律:形态 A 下宿主代码也应只用白名单 + L0 装配点);玩法插件侧自建 ArchUnit 模板随 7.5 施工文档提供(框架无法跨 jar 强制,review 条款兜底) |
| **版本协商(★v1.14 历史口径)** | **机制已整体退场(32 号/ADR-0020,2026-11-06)**:`MiniGameApi.API_VERSION` 常量与 `MatchModeProvider#requiredApiVersion()` SPI 声明删除——运行期版本对照是自我比较(三方同源,恰好实现检查的消费方必然刚重编译过,真正的崩点 `NoSuchMethodError` 不经过版本数字),兼容性陈述改由版本链文档(README/AGENTS §3)+ 冻结测试(★19/★44 只增不改)+ maven 坐标承载;框架启动与注册日志无版本字段;消费方 `onEnable` 首行仅查 **api 在场**(`api == null` → SEVERE + `setEnabled(false)`,拒载不崩服语义保留) | 原 D7 沿革(数字轴 1~8,已终结):v1.0 = 1,v1.1 = 2,v1.2 = 3,v1.3 维持 3,v1.4 = 4,v1.6 = 5,v1.7 = 6,v1.8 = 7,v1.13 = 8;One-Version Rule「破坏性升版 + 至多一个次要版本双轨」经五次用户裁决豁免(★v1.4/★v1.6/★v1.7/★v1.13/★v1.14——本次连常量一并退场,该规则的执行载体改归冻结行与修正案流程) |
| **禁 shade** ★v1.1(D7/14.35) | 玩法/伴随插件 **compileOnly** 依赖框架 jar + 运行期 `depend: [HydroKarbs]`;构建脚本断言消费方 jar 内不得出现 `com.hydrokarbs.` 前缀类(消费方各用自有包根,宿主 = `com.shdf.*`)——shade 框架类会导致双引擎/ClassCastException/模式注册进错误注册表 |

> 为什么把模式注册放行给第三方、而组件不放行:模式是**声明级**扩展(Builder 拼数据,风险面小——坏声明在 `verifyDependencies`/`checkMap` 就被拒);组件会挂进我们的 tick 循环,坏组件能拖垮 TPS。先开放前者,后者等有真实需求再加(Assumption-driven,不做 hypothetical seam)。
> **v3 补充的刻意限制(A9)**:聚合根 `build()` 后不可变,第三方**不能**给已注册模式追加组件或改组件表——要"变体"就注册一个新 id。这杜绝了运行期装饰带来的加载顺序地狱。

### 7.3 会话级组件的 API 边界(★v4.0 增补;★v1.1 修订:SPI 未决项由 D14 裁决受限开放,查询面未决维持)

v4.0 引入**会话级组件**(`SessionComponent`:每会话一份、跨模式切换存活、随 `dissolve` 全灭;生命周期与跨级查询见 §3.8,契约骨架见 §12.2)。它住在引擎内部,对外公共面在 v4.0 的边界结论:

| 事项 | v4.0 边界 |
|---|---|
| `MiniGameApi` 冻结面 | **第一版不进 `MiniGameApi` 冻结面**:不新增任何会话级组件相关的方法;装配来源、派发节拍、故障隔离都由引擎/L0 持有——会话级组件不是对外契约,§7.1 方法集与 §7.2 的冻结纪律原样有效(**★v1.1 修订**:本行"不新增会话级组件相关方法"由 19-D14 受限突破——新增的是 `createSession(SessionRecipe)` 重载与 `SessionComponentProvider` 注册面,仍走"只增不改";**★v1.6(24 号)**:原 `SessionComponentProvider` 注册面已删除,全局贡献改经 `registerSessionComponents`/`unregisterSessionComponents` 两法进冻结面——见下行 SPI 行) |
| 会话快照定名与消歧 | **api 侧定名 `SessionInfo`**(施工定名,阶段 7 README §5,2026-10-25:原骨架名 `SessionView` 让位,与 `MatchModeInfo`/`MapInfo` 命名对齐):`api/view/SessionInfo`(不可变 record 快照,§7.1/§7.2 冻结面)与 `engine/port/SessionView`(★v4.0 实例侧只读会话视图**接口**,§12.2)不再同名——公共面只暴露前者,引擎契约不属 `api/`;两者不得混用。★v1.1:api/ 新增的 `SessionEventView` 是会话事件快照族,与两者均不同义 |
| 第三方会话组件 SPI | **未决项已裁决关闭(★v1.1 修正案 19-D14,修订原"第一版不开放")→ 受限开放;★v1.6(24 号)贡献通道一改显式注册**——插件(宿主/伴随)经两条通道贡献会话组件:①全局贡献 **`registerSessionComponents(Plugin, specs)`**(显式注册,本面四法之一;干跑合并校验(查重/依赖)失败**响亮上抛**给调用方——注册闸只有一份;同一提供方重复注册 = 幂等重收录;摘除 = `unregisterSessionComponents(Plugin)`,幂等 no-op);②单次 `createSession(SessionRecipe)` 携带 `extraComponents`(查重/缺依赖 → 响亮失败)。合并序 = 宿主组装的 spec.sessionComponents → **已收录贡献(`registerSessionComponents` 注册序)** → recipe;**模式仍无权贡献(v4-D4 不变)、无运行期增删**(贡献只发生在会话创建时,装配点唯一不变)。原 `SessionComponentProvider` SPI(经 ServicesManager 注册、`ServiceRegisterEvent` 收录、坏 provider SEVERE 拒收录会话照常)为 **★v1.6 前历史口径**——接口已删除(见 §7.1 第二代码块注)。防线 = 注册期干跑校验 + 装配期 verifyDependencies + 运行期 quarantine(★30)+ 预算告警(D9-③ 扩至会话组件)。原未决项(17 §11-Q2)就此关闭;**第三方查询会话组件实例维持不开放**(下行) |
| 第三方查询会话组件 | **未决维持**:第三方经 API 查询会话组件实例——第一版明确不进;日后若开放,仍走 §7.2"只增不改"的冻结纪律(19-§7 未决项随 D14 登记口径) |
| 实例组件订阅会话事件 | **未决(变更指示 §11-Q7;19-§7-Q2 维持不开放)**:第一版**不开放**——`SessionInfo` 不提供 `events()`(实例组件不得订阅会话事件,§3.8/§12.2);登记去向:本节与 11(§11.4 注),待裁决 |
| `SessionService` 公开方法签名 | **不变**:v4.0 只在其内部扩展**装配/派发/回收接线**(装配在 `create` 内、先于首个实例;派发在 `tickAll` ③ 步;回收在 `dissolve` 逆序 `stop` + 登记资源回收)——**装配/派发/回收接线扩展不改变公共面**:公开方法签名不变(§3.2),`switchMode` 的受控暴露(§7.1)与实现类的薄委托关系(§7.2)都不受影响 |

> 一句话:v4.0 的插拔发生在**引擎内部**;公共 API 的消费者(别的插件)在 v4.0 里既不能查询会话组件,也不能提供会话组件。**★v1.1 裁决(D14)**:后半句已突破——插件(宿主/伴随)可经两条通道**提供**会话组件(模式仍不可,无运行期增删);**查询**会话组件实例维持不开放(未决)。**★v1.6(24 号)**:全局通道的注册面改显式——`registerSessionComponents`(全局贡献)+ `SessionRecipe`(单次);原 `SessionComponentProvider` SPI 随 ServicesManager 提供方通道退场删除。**★v1.7(25 号)**:能力标签机制退场后,本节两通道即**外部能力的新形态**——外部插件贡献**类型化桥接会话组件**(如角色/经济适配组件)进会话,消费方组件以 `requires`/`requiresOptional` 类型化声明依赖:硬依赖缺失在**装配期响亮**(干跑合并校验/createSession 拒绝),运行期降级/quarantine 由桥接组件自身承接(D14 既有口径;provider 摘除只影响后续会话);软依赖由 `requiresOptional` 独自承载(★21/★60)。

### 7.4 进程级服务的 API 边界(★v4.1 增补)

v4.1 引入**进程级服务**(Service:进程生命周期,`onEnable` 装配 → `onDisable` 关停,经 `ServiceRegistry` 两阶段装配,不随任何会话/实例生灭;概念定义与硬边界见 §2.9,服务 ↔ 会话的三通道见 §3.9,契约骨架见 §12.2)。与会话级组件一样,它住在引擎内部,对外公共面在 v4.1 的边界结论:

| 事项 | v4.1 边界 |
|---|---|
| `MiniGameApi` 冻结面 | **第一版不进 `MiniGameApi` 冻结面**:服务、`ServiceRegistry`、服务查询一律**不进公共面**(修正案 D9)——不新增任何方法;§7.1 方法集与 §7.2 的冻结纪律原样有效,`MiniGameApiSurfaceTest` 现有断言即守卫 |
| 第三方消费服务 | **未决(修正案 §12-Q9)**:`MiniGameApi` 是否暴露服务查询/进程级快照——第一版明确**不进**;登记去向:本节(07 §7.4);日后若开放,仍走 §7.2"只增不改"的冻结纪律 |
| 第三方提供服务 | **未决(修正案 §12-Q9;E 案另见 §12-Q8)**:第三方提供进程级基础设施的版本化 SPI 是否开放、何时——第一版**不开放**;修正案对 §12-Q8 的建议去向为 13 选型节与 ADR-0006(`docs/adr/0006-process-service-registry.md`;修正案原文编号 0005,因 `docs/adr/0005` 已被《架构冻结测试门禁》占用而顺延为 0006)的未决项;本条登记去向:本节(07 §7.4) |
| `SessionService` 公开方法签名 | **不变**:v4.1 不在 `SessionService` 公开面新增任何服务相关方法;服务触达会话走三通道(订阅 `SessionEventBus`、只读快照、受控命令)——受控命令即 `switchMode` / `dissolve` / `createSession` 既有签名(§3.2/§7.1),服务属切换来源②,与 `/sm` 命令同权 |

> 一句话:v4.1 的进程级服务同样是**引擎内部居民**——公共 API 的消费者(别的插件)既查询不到服务,也(第一版)提供不了服务;服务触达会话走三通道,不经 `MiniGameApi`。开放与否留给未决问题(§12-Q9、§12-Q8)裁决。
>
> ★v1.1 注(D10):三通道与 API 面扩充**互不打通**的声明为本节正式口径——服务是引擎内部居民,不消费 `MiniGameApi`;`joinSession`/`leaveSession` 扩大的是**外部调用方**阵营(命令/监听/伴随插件/玩法插件)的执行权,不构成第四通道(ArchUnit 增款:`engine/service/**` 与顶层 `service/**` 不得 import `api/**`,★40 增款;14.37)。本节两个未决项(§12-Q9/Q8)**维持**,不随 v1.1 改变。

### 7.5 跨插件消费(★v1.1:伴随插件的完整权限面)

v1.1 起,框架是**独立插件**、玩法与伴随插件是**消费方**(`depend: [HydroKarbs]`,compileOnly + 禁 shade,14.35)。扩充后的 `MiniGameApi` 给伴随插件(13 §13.9 案②形态)完整权限面——与玩法插件同权,不再是"只能看":

| 能力 | API 面 | 说明 |
|---|---|---|
| 查询 | `sessionOf` / `session` / `sessions` / `modes` / `maps` / `mode` | 不可变 record 快照;不持活引用跨切换(现取不缓存,14.31 同款纪律)。战绩 `stats`/`top` **不在本面**(★v1.3:随 R1 悬置——归宿主线消费,签名类型已随 L5 出框架;缺席语义回填裁决 21 §6-Q1) |
| 控制 | `switchMode` / `createSession(SessionRecipe)`(★v1.4:两参重载已删除,`SessionRecipe` 为建会话唯一形态,22 号) | 与 `/sm` 命令同权;`force` 是显式选择,走 review 条款 |
| 成员 | `joinSession` / `leaveSession`(D2) | = `SessionService#join/leave` 的受控暴露;含 `JoinAdmission` 裁决;leave 幂等;权限/审计口径未决(19-§7-Q3,倾向第一版无权限闸 + SEVERE 审计日志) |
| 事件 | `subscribeSessionEvents`(D3) | `SessionEventView` 不可变快照族(八事件:§3.1 七事件 + ComponentQuarantined);句柄 `close()` 注销;订阅者异常隔离;实例事件不暴露(Q5) |
| 能力标签(★v1.7 历史口径) | **两法已删除**(原 `registerCapability(Plugin, String)` / `capabilities()`,D4——★v1.7/25 号:能力标签机制全链退场,字符串契约不严谨;幂等注册/插件 disable 自动摘除等原语义随机制退役) | **外部能力 = 会话组件桥接**(★21/★60):提供方经 `registerSessionComponents`/`SessionRecipe` 贡献类型化桥接组件(上行行),消费方组件 `requires`/`requiresOptional` 类型化声明——装配期响亮、运行期 quarantine;原「§8 先例 14.22 懒解析与动态刷新纪律」归宿主扩展线自持 |
| 会话组件贡献 | **全局贡献 `registerSessionComponents(Plugin, specs)`** / 单次 `SessionRecipe.extraComponents`(D14;**★v1.6/24 号:原通道一 `SessionComponentProvider` SPI 已删除,全局贡献改本面显式注册**——干跑校验响亮上抛、幂等重收录、`unregisterSessionComponents` 摘除;**★v1.7/25 号:本行即外部能力新形态的落点**——外部能力 = 类型化桥接会话组件,消费方 `requires`/`requiresOptional` 声明,见 §7.3 注) | 插件可"拼"会话;模式仍无权;无运行期增删;合并序与防线见 §7.3;disable 兜底四族含贡献(后续会话不再合并,已装配会话不受影响;**★v1.7:兜底四族收敛三族——能力标签族随机制退场移除**) |
| 地图注册 | `registerMap(Plugin, MapDefinition)` / `unregisterMap(Plugin, String)`(★v1.2) | **唯一地图注册通道**(同一 `MapRegistry` 命名空间,目录顺序 = 注册序;★v1.4/22 号:文件式装载通道已退场);**地图交成品不交工厂**(纯静态值;★v1.11/29 号(ADR-0017):声明 = 三字段 `(worldDirectoryPath, resetStrategy, AttributeBag metadata)`,声明即路径,第三分量为构造即冻结的纯容器);注册期校验:世界目录存在且为目录(直验声明自带路径,缺失/非目录响亮抛带完整绝对路径)、id 查重(id = 声明目录名,路径派生)、`metadata` 非空且构造即冻结(标签 = `MapDefinition#tags()` 唯一读口,处置策略 = `resetStrategy` 声明字段);正用摘除被拒(20-D4 守卫语义,存续);`WorldPort` 不受影响(20-D1/D2/D3/D7;步骤见 §13.11) |
| 版本(★v1.14 历史口径) | **无版本协商面**——`API_VERSION` 常量与 `requiredApiVersion()` 声明已退场(32 号/ADR-0020) | 消费方 `onEnable` 首行仅查 api 在场:缺席 → SEVERE + `setEnabled(false)`(拒载不崩服);兼容范围由版本链文档与依赖坐标(`com.hydrokarbs:Hydrokarbs`)声明 |

**信任边界(D9 三层)**:①装配期拒坏声明(`verifyDependencies`/`checkMap`/注册期 build 校验、provider 干跑合并校验);②运行期隔离(实例组件异常 → quarantine → `end()` → 回退;会话组件/贡献组件异常 → 隔离不解散,★30/★48/★53);③预算观测(`EngineTicker` 对组件 `update` 计时,单组件单 tick 超阈值记 WARNING——会话 id/组件 id/提供方插件;硬熔断未决 Q4)。

**示例一:入口牌子插件**(hub 的牌子/NPC → 编排玩家 hub→arena):

```java
public class JoinSignPlugin extends JavaPlugin {
    private EventSubscription sub;                       // 句柄只在实例字段(14.38)
    private MiniGameApi api;

    @Override public void onEnable() {
        api = getServer().getServicesManager().load(MiniGameApi.class);
        if (api == null) {                               // 框架缺席 → 拒载不崩服(★v1.14:版本协商面已退场,32 号,仅查在场)
            getLogger().severe("HydroKarbs 缺席,禁用"); setEnabled(false); return;
        }
        sub = api.subscribeSessionEvents(ev -> {          // D3:会话事件快照
            if (ev instanceof SessionEventView.Dissolved d) updateSigns(d.session());
        });
    }
    void onSignClick(Player player, String arenaId) {     // 主线程
        api.leaveSession(player.getUniqueId(), LeaveReason.MOVED_OUT);   // D2:hub 出局(幂等)
        api.joinSession(arenaId, player.getUniqueId())                    // D2:arena 入局(含 JoinAdmission 裁决)
           .ifRejected(reason -> player.sendMessage(Text.warn("入场被拒:" + reason)));
    }
    @Override public void onDisable() { if (sub != null) sub.close(); }   // 显式注销(14.38)
}
```

**示例二:跨服看板插件**——`subscribeSessionEvents`(Created/Dissolved/ModeSwitchCompleted)+ `sessions()` 只读快照维护"现存会话"视图;数据一律现取,不持活引用(镜像服务层 `GlobalBoard` 形态,§13.9 案①)。

**示例三:赛程器/第二宿主**——`createSession(new SessionRecipe("mode", Optional.of("map"), pool, List.of(new MyGraceComponent.Spec(...))))` 按需定制竞技场(D14 单次贡献);宿主级策略走 `api.registerSessionComponents(this, List.of(new MyGraceComponent.Spec(...)))` 显式注册(全局贡献;干跑校验失败响亮抛,后续所有新会话合并)。详见 §13.10。

---
