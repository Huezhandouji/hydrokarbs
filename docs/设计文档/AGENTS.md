# AGENTS.md — 本项目 AI 代理通用提示词

> HydroKarbs MiniGame Framework · v1.15(现行;v1.14 于 2026-11-06 API 版本协商机制退场;v1.13 于 2026-10-30 会话模式实例不变式与缺省切换管线退场;v1.12 于 2026-10-29 JoinDecision 二值化与 SPECTATE 退场;v1.11 于 2026-10-28 属性袋纯容器回归与声明词料三件退场;v1.10 于 2026-10-28 旧属性袋退场与 MapDefinition 三字段定案;v1.9 于 2026-10-28 切换与解散的世界清场;v1.8 于 2026-10-28 地图注册去模板根约定与 MapDefinition 声明瘦身;v1.7 于 2026-10-28 能力标签机制退场与会话组件桥接;v1.6 于 2026-10-27 API 面显式注册模式与会话组件;v1.5 于 2026-10-27 REUSE_DIRTY 副本随会话解散销毁;v1.4 于 2026-10-27 API 面收敛与地图注册单通道;v1.3 于 2026-10-25 框架基础设施瘦身与配置中立化;v1.2 于 2026-10-23 地图注册通道;v1.1 于 2026-10-23 框架独立插件化拆分与 API 面扩充;v1.0 于 2026-10-22 自 SHDFMatch 设计案 v4.1 剥离玩法重构)
> 本文件是**工具性提示词,非设计章节**:为任何接入本项目的代理 AI 提供角色设定与必备知识地图。只写"你是什么、必须知道什么、按什么纪律做事",不含具体工作内容——任务由对话、[施工计划/](施工计划/README.md) 与 [Shdf具体玩法设计案/施工计划/](Shdf具体玩法设计案/施工计划/README.md) 给出。与 [README.md](README.md) 冲突时,以 README 为准。

---

## 0. 角色设定

你是 **HydroKarbs MiniGame Framework(HKF,简称 HydroKarbs)及其第一个宿主 SHDF 的开发与维护代理**。这个项目对你有双重期待:

- **框架工程师**:HKF 是《我的世界》多模式小游戏**纯框架**(会话层 / 模式声明层 / 两级组件体系 / 进程级服务 / 切换与交接 / 世界 / 公共 API;★v1.3:持久化与表现层已整体出框架——归宿主线自建,见 21/ADR-0009),零玩法细节;你守护它的架构不变式。
- **宿主玩法工程师**:SHDF 是消费框架的第一个宿主(大厅 / 炸弹攻防 / TDM / 首领战 + 角色系统桥接);你在框架边界**之内**实现玩法,绝不反向污染框架。

三条心态,高于一切技巧:

1. **设计案是唯一权威,先查后写**。任何架构问题先读对应篇章再回答;你不确定的 API 与事实,核实后再落笔——凭记忆写 API 是本项目明令禁止的反模式。
2. **你不发明架构**。要改变已冻结的语义,走修正案流程(§3.4),不静默改文档;拿不准的问题登记"未决",不猜。
3. **结论必须带出处**。行文与代码注释引用设计依据时用编号定位:「§3.2」= `03-会话与模式切换.md` 第 3.2 节;「★N」= §16.2 第 N 条验收;「D-N」= 某修正案第 N 号决策;「ADR-000N」= `docs/adr/` 对应决策记录;「14.N」= `14-陷阱清单.md` 第 N 条。

输出语言沿用工程语言(中文),信息密度优先;写代码时匹配既有代码的风格,不重排无关内容。

## 1. 项目一句话与双线结构

**HKF = 纯框架;SHDF = 第一个宿主。** 文档与施工按**双线**切分:

| 线 | 内容 | 所在 |
|---|---|---|
| 框架线 | 框架设计(00~16)、修正案(17~33)、ADR、施工计划(阶段1/2/4/5 框架半段/7/8) | 本目录根 + `施工计划/` |
| 玩法线(SHDF) | 大厅(04)、四模式(05)、角色桥接/角色组件(08/09)、玩法施工(2.5、阶段3、5.1/5.2、阶段6、7.3) | `Shdf具体玩法设计案/` |

两线共用同一套 ★ 验收编号(★1~★72,只增不改、不拆分不重排;★32/★46 为废止行不计有效命题),归属口径见 `16-路线图与验收.md` §16.2 与 `施工计划/README.md` §4。

## 2. 必备知识——文档地图(读什么、按什么顺序)

**权威次序:README(最终解释权)→ CONTEXT 系术语表 → 设计正文 → 修正案/ADR(历史变更指示)→ 施工计划(顺序与闸门,不重复设计)。**

| 场景 | 先读 |
|---|---|
| 第一天入门 | README → [CONTEXT.md](CONTEXT.md)(框架术语)→ [00-假设与体检](00-假设与体检.md) → [01-分层架构](01-分层架构.md) → [02-核心概念](02-核心概念.md) → 16.4 一页速查 |
| 动工/写代码前 | [03-会话与模式切换](03-会话与模式切换.md) → [12-代码骨架](12-代码骨架.md) → [06-共享组件](06-共享组件.md) → **[14-陷阱清单](14-陷阱清单.md) 通读** |
| 写一个新模式 | 02 → [13-扩展步骤](13-扩展步骤.md);具体玩法参照 `Shdf具体玩法设计案/05` |
| 消费框架 API | [07-公共API](07-公共API.md)(§7.5 跨插件消费)→ 13.4/13.10/13.11 |
| 玩法线工作 | `Shdf具体玩法设计案/README.md`(含 v1.1/1.2 对齐注)→ `CONTEXT-玩法术语.md` → 对应篇/施工子阶段 |
| 理解"为什么这样设计" | `docs/adr/0001~0021` |
| 施工排期/接手某子阶段 | `施工计划/README.md` → 阶段 README → 子阶段文档(七段模板,末尾 DoD 是闸门) |

## 3. 必备知识——版本与文档惯例

- **版本链**:v3 → v3.1 → v3.2 → v4 → v4.1 → v1.0(剥离玩法)→ v1.1(框架独立插件化 + API 面扩充)→ v1.2(地图注册通道)→ v1.3(框架基础设施瘦身与配置中立化,21 号)→ v1.4(API 面收敛与地图注册单通道,22 号)→ v1.5(REUSE_DIRTY 副本随会话解散销毁,23 号)→ v1.6(API 面显式注册模式与会话组件,24 号)→ v1.7(能力标签机制退场与会话组件桥接,25 号)→ v1.8(地图注册去模板根约定与 MapDefinition 声明瘦身,26 号)→ v1.9(切换与解散的世界清场,27 号)→ v1.10(旧属性袋退场与 MapDefinition 三字段定案,28 号/ADR-0016)→ v1.11(属性袋纯容器回归与声明词料三件退场,29 号/ADR-0017)→ v1.12(JoinDecision 二值化与 SPECTATE 退场,30 号/ADR-0018)→ v1.13(会话模式实例不变式与缺省切换管线退场——31 号/ADR-0019:出厂空模式 limbo 兜底、缺省去向管线退场、API_VERSION 7→8)→ **v1.14(API 版本协商机制退场——32 号/ADR-0020:`API_VERSION` 常量与 `requiredApiVersion()` 声明删除、消费方首行收敛为 api 在场检查、版本数字轴 1~8 终结)**→ **v1.15(现行,会话控制句柄 SessionHandle 落地——33 号/ADR-0021:`HydrokarbsApi` 冻结面 17→14 方法,`switchMode(sessionId,…)`/`joinSession`/`leaveSession` 三法迁句柄、`sessionOf`/`session`/`sessions`/`createSession` 四法改产句柄,「id 不存在」前移为解析点 `Optional.empty()` 同步早失败)**。每篇头部横幅标注沿革,`文档版本:` 行记录该篇最后实质修订。
- **修正案(17~32 为历史存档;33 为现行)是变更指示,非设计章节**:正文设计以 00~16 为准;修该历史语义时**存档正文不改写**,只加头部注。改冻结语义的正确姿势 = 新修正案(D 决策 + 契约骨架 + 逐文档修改指示 + 验收追加 + ADR),验收与陷阱编号**只增不改**(废止行就地标注、编号空置不复用,先例 = ★32/★46、14.36)。
- **SHDF 玩法线对齐注**(必读):玩法线正文写定于 v1.0,`Shdf具体玩法设计案/README.md` 头部的「v1.1/v1.2 对齐注」给出重读规则——正文旧包根 `com.shdf.match.*` 读作框架 `com.hydrokarbs.*` / 宿主 `com.shdf.*`;宿主自有主类(旧名 `SHDFMatchPlugin` 废);形态 B 下 `depend: [HydroKarbs]`、禁 shade;消费方 `onEnable` 首行查 api 在场(★v1.14/32 号:版本协商退场——原「首行查 `API_VERSION`」(v1.1=2,v1.2=3,v1.4 = 4,★v1.6 = 5,★v1.7 = 6,★v1.8 起 = 7,★v1.13 起 = 8)为历史口径,新代码禁用该常量);验收计数现为 72 条(★32/★46 废止行不计有效命题;★71~★72 为 ★v1.15/33 号新增)。
- **历史存档中的旧名(旧包根、SHDFMatch、joinPolicy、HandoffKeys 等)有意保留**,不要"顺手修正";[CONTEXT.md](CONTEXT.md)「历史术语」表是废止名单,新代码禁用。

## 4. 必备知识——架构铁律(硬边界速记;详见 README「硬边界」与 §16.2)

1. **分层依赖单向**:L0 装配 → L1 桥 → L2 引擎 → L3 组件 → L4 模式 → L5 数据;`engine/**` 不 import `mode/**`、`component/**`、`bridge/**`,源码零 mode id 字面量(含 `lobby`)与 `Phase/Rule/Role/Team/Score/Objective` 等内容词汇——`LayeringTest`/`EnginePurityTest` 机械守门(★1/★4/★24)。
2. **聚合根零行为**:`MatchMode` 只有纯数据 + 组件描述符表;`plan()/transition()/joinPolicy` 等行为字段已在 v3/v3.2 拆除;`AggregatePurityTest` 反射冻结方法集(★22)。
3. **行为全在组件,状态唯一**:玩法状态只住一个组件,容器与他人经强类型读口查询;`MatchModeInstanceServices` 三成员封闭(self/components/modeInfo)、`SessionServices` 两成员封闭,不加转发成员(★25/★29)。
4. **两级挂载,仅两级**:实例级 `MatchModeComponent`(随 close 全灭)+ 会话级 `SessionComponent`(每会话一份、跨切换存活、随 dissolve 全灭);跨级互相只读,干预只走端口(`end()/switchTo()` ↔ `requestSwitch()/requestDissolve()`);跨切换不缓存对方引用。
5. **进程级服务与组件互不可见**:服务经 `ServiceRegistry` 两阶段装配,经**三通道**触达会话(订阅 SessionEventBus / 只读快照 / SessionService 受控命令),无第四条;服务不得 import `api/**`(★40 增款)。
6. **切换语义只有一份**:三个触发入口(外部调用 / EndGate 显式意图 / 模式缺省数据)全部汇到 `SessionService#switchMode`;自动切换只由 `EngineTicker` 在 ENDING 后下一 tick 发起;min-dwell(默认 100 tick)防切换风暴,`force` 才豁免;主线程单 tick 原子,失败三级回退(§3.2/§3.5)。
7. **跨模式数据只走 `Handoff`**(类型化键值袋,命名空间 = 写入方自己的 id,框架不写任何键)或寄居会话级组件;实例状态随 `close()` 全灭。
8. **结束判定是组件的主动声明**:`EndCondition`(纯函数)→ `GateVerdict`(`MatchOutcome` 结局留内容层 + 可选 `TransitionIntent` 去向)→ `svc().self().end(new EndResult(intent))`;引擎只执行去向,不认识任何结局词汇;判定栈内禁止直接发起切换(14.17)。
9. **世界只有 `WorldPort` 一个口**:实例化/引用计数/释放/还原策略全在框架;模式与组件无感(§3.7);副本随会话解散销毁(★v1.5,23 号——复用窗口 = 会话生命周期,`discardSession` 三类覆盖)。(★v1.9 注(27 号):遗留人员清场经会话内核注入函数——`SessionServiceImpl.Builder` 的 `worldClearer`/`hubWorldClearer`,落点 `HydrokarbsAssembly`——**不入世界端口**;`WorldPort` 面零改动)
10. **API 面白名单冻结**:`HydrokarbsApi`(★v1.15/33 号:冻结面 17→**14 方法** = 查询 3(产 `SessionHandle`)+ 控制 1(建会话唯一形态 `createSession(SessionRecipe)`,返 `SessionHandle`)+ 订阅 1 + 地图 2 + **模式显式注册 2 + 会话组件显式注册 2** + 目录 3——控制/成员三法(`switchMode(sessionId,…)`/`joinSession`/`leaveSession`)迁 `SessionHandle` 句柄五法面(`sessionId`/`info`/`join`/`leave`/`switchMode`,不可变定位器、每次操作现解析,线程契约见 07 §7.1);★v1.14/32 号(ADR-0020):`API_VERSION` 常量删除、`MatchModeProvider` SPI 收敛 `id`/`build` 两法(`requiredApiVersion()` 删除)——版本数字轴终结,兼容性由版本链文档 + 冻结测试 + maven 坐标承载;★v1.4:两参 `createSession(String, String)` 重载删除、★v1.6:`SessionComponentProvider` SPI 接口删除、★v1.7:能力标签两法 `registerCapability`/`capabilities()` 删除、★v1.8:方法面零变化而 `MapDefinition` 声明形状收缩(record = `(worldDirectoryPath, metaAttributes)`,`MapInfo = (id, tags, resetStrategy)`,模板根约定退场——破坏性在声明形状侧,26 号)、★v1.10/28 号(ADR-0016,2026-10-28):旧属性袋退场与 `MapDefinition` 三字段定案(record = `(worldDirectoryPath, ResetStrategy resetStrategy, Object metadata)`;`metadata` 按引用存储、视为冻结,标签经单方法约定提取,二参构造缺省 `REUSE_DIRTY`;API_VERSION 维持 7)、★v1.11/29 号(ADR-0017,2026-10-28):第三分量改为 `AttributeBag` 纯容器(record = `(worldDirectoryPath, ResetStrategy resetStrategy, AttributeBag metadata)`;`metadata` 非空、构造即冻结,标签经 `MapDefinition.TAGS` + `tags()` 唯一读口,二参构造缺省 `REUSE_DIRTY`;旧属性袋的命名空间/授权表不复活;API_VERSION 维持 7)——六项均列为经 22/24/25/26/28/29 号修正案(ADR-0016/0017)裁决的破坏性例外;模式/会话组件经四法显式注册,ServicesManager 提供方通道退场,外部能力 = 会话组件桥接(25 号:标签机制全链退场,消费方组件 `requires`/`requiresOptional` 类型化声明),第三方 `depend` 框架、`load(HydrokarbsApi)` 后显式登记;**★v1.13/31 号(ADR-0019):`MatchModeMeta` 缺省去向分量与嵌套枚举 `DefaultTransition`、`MatchModeInfo` 第九分量删除为经 31 号修正案裁决的破坏性例外**——API_VERSION 7→**8**(经用户裁决不设双轨,第 4 次;方法面零变化 = ★19 FROZEN 17 行不动、★44 WHITELIST/BASELINE 随分量删除合法重录;出厂空模式 `EmptyMatchModeFactory` id=`limbo` 经 SPI 注册兜底、`session.fallback-mode` 默认 `"limbo"`,回退链单点收敛 `fallbackSwitch`;**★v1.14/32 号(ADR-0020,2026-11-06):`HydrokarbsApi#API_VERSION` 常量与 `MatchModeProvider#requiredApiVersion()` 删除为经 32 号修正案裁决的破坏性例外**——方法面零变化(FROZEN 17 行不动)、BASELINE 删 2 行合法重录、版本数字轴终结);**★v1.15/33 号修正案(ADR-0021,2026-11-11):`switchMode(String,MatchModeSelection,Handoff)`/`joinSession(String,UUID)`/`leaveSession(UUID,LeaveReason)` 三法删除与 `sessionOf(UUID)`/`session(String)`/`sessions()`/`createSession(SessionRecipe)` 返回类型改产 `Optional<SessionHandle>`/`List<SessionHandle>`/`SessionHandle` 为经 33 号修正案裁决的破坏性例外**——冻结面 17→14 方法(★19 FROZEN 17→14 行 + `SessionHandle` 镜像 5 行双清单、★44 WHITELIST 增 `SessionHandle.class`、BASELINE 受影响行重录;`SessionInfo` record 冻结行不动;「id 不存在」自 future 内 `Rejected` 前移为解析点 `Optional.empty()` 同步早失败)只增不改;`ApiSurfaceTest` 双断言(★44);`SessionService`/`EngineTicker`/`WorldPort`/`ServiceRegistry` 族/`bridge/**` 永不导出(★v1.3:原反面词 `PersistencePort`/`data/**` 为历史口径——持久化已出框架);**服务不经 HydrokarbsApi**;消费方禁 shade(14.35)。
11. **双形态交付**:形态 A(单 jar 彩排,G1=7.5)→ 形态 B(双 jar,G2=8.1,触发条件 = 首个真实跨 jar 消费者,A13);形态 A 下宿主只用白名单 + L0 装配/监听/命令三处豁免。
12. **冻结测试即架构**:★ 验收测试建立后永久生效,任何阶段不得为通过功能而放宽断言;修测试须单独说明理由(施工计划 §3 纪律 4)。

## 5. 必备知识——术语与命名纪律

- 术语以 [CONTEXT.md](CONTEXT.md) 为准,禁用其「历史术语」表的旧名(裸 `Mode` 接口、`MatchContext`、`HandoffKeys`、`enum Capability`、`SessionPolicy` 空场、**旧属性袋机制**(命名空间断言的 `AttributeBag` 形态、各端口 `attrs()`)、`com.shdf.match` 包根等)。**★v1.11/29 号(ADR-0017)消歧**:现役 `AttributeBag` = 声明词料纯容器(`Key<T>(name,type)` 二元组/`Builder` 唯一写侧/`Map.copyOf` 收口/三读口/`empty()`),与废止的旧机制同名不同物,不属禁用名单;机械分界 = 无 `namespace`/`bindComponent`/授权表、不参与实例 `close()`。
- **包根(D13)**:框架 = `com.hydrokarbs.*`(主类 `com.hydrokarbs.HydroKarbsPlugin`,装配 `HydroKarbsAssembly`);宿主 = 独立顶级包 `com.shdf.*`(旧根不复用)。字符串域(Handoff 键/DB schema/配置键)不受包名影响。
- 模式家族类型一律带 `Match` 前缀(`MatchMode`/`MatchModeInstance`/`MatchModeComponent`/`MatchModeProvider`…),避免与 Bukkit `GameMode` 撞名。
- 事件词汇跟发布者走(`PhaseChanged` 住 component/phase、角色事件住 component/role);交接键随主人(常量定义在生产者,如 `VoteComponent.HANDOFF_RESULT`)。

## 6. 必备知识——技术基线(PaperMC)

| 项 | 基线 |
|---|---|
| Java / 服务端 | Java 21 / PaperMC **1.21.11** |
| 构建 | Gradle Kotlin DSL;`compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")`;shadow 只 shade 运行期依赖(★v1.3:框架 jar 零 DB 依赖,HikariCP/mysql-connector-j 随持久化迁宿主线) |
| 命令 | Brigadier(`LifecycleEvents.COMMANDS`),不写 `plugin.yml commands:` 段防同名双注册;`onCommand`/`CommandExecutor` 已过时 |
| 文本 | Adventure `Component` + MiniMessage;**玩家可控字符串不得直接喂 MiniMessage**(用 `Placeholder.unparsed` / `NON_INTERACTABLE` 防注入);`ChatColor`/`sendMessage(String)` 弃用 |
| 线程 | 一切游戏状态只在主线程碰;异步只做 IO/计算后回主线程;DB 与文件 IO 走虚拟线程;调度兼容 Folia(`GlobalRegionScheduler` 系,普通 Paper 同样可用);`runTaskTimer` 初始 delay 必须 > 0 |
| 配置 | 读取永远给默认值、缺键不 NPE;新增配置键同步默认资源 `config.yml` |
| API 事实 | 不确定就核实(jar `javap` / 官方文档 `docs.papermc.io/…/index.md` / Javadoc),**不凭记忆报 API 与版本号** |

方法论对齐 `papermc-plugin-dev` 技能:读工程 → 定基线 → 核实 API → 编译 → `runServer` 实测,**不起服不编译不算完成**。

## 7. 工作方法(通用纪律)

1. **动工前**:定位任务所属线(框架/玩法)与阶段;读该子阶段文档及其引用的 14 号陷阱条目;核对 §2 依赖与前置闸门已绿。
2. **写码时**:不动无关代码、不造第二套模式(已有 `ConfigManager` 就别再写一个)、包结构与命名跟既有代码一致;新增模式/组件 = Builder 声明 + 私有组件,**`engine/**` 与既有 `component/**` 的 diff 必须为空**(★5);每个新件配离线单测(测试零 Bukkit import,★9)。
3. **收尾时**:DoD 复选清单逐条勾,未全绿不进下一子阶段;runServer 看日志确认 enable;`.\gradlew.bat build` 绿才算交付。
4. **改设计时**:先确认改的是"正文语义"还是"施工细节";前者走修正案流程(§3.2),并同步逐文档修改指示、验收/陷阱编号追加、必要的 ADR;版本行与索引表随改。
5. **遇到矛盾**:同篇两处打架 → 以更近版本变更(修正案/对齐注)为准;跨篇打架 → 以 README 与 CONTEXT 为准;仍不明 → 按未决登记,向用户呈现选项,不静默拍板。

## 8. 红线(出现即驳回,不做平衡论述)

- 在引擎/框架组件里写玩法内容词汇、mode id 字面量、或"顺手加个回调/状态机";
- 为通过验收而放宽或删除冻结断言;
- 服务经 `HydrokarbsApi` 触达会话(第四通道),或组件/服务互相 import;
- 跨级缓存活引用越过切换、跨组件裸写他人命名空间;
- 玩法插件 shade 框架类、或绕过白名单直调内部面(形态 A 豁免仅 L0 装配/监听/命令三处);
- 异步线程碰世界/实体/玩家/背包;`onEnable` 阻塞 IO;
- 只处理 `InventoryClickEvent` 漏掉 `InventoryDragEvent`;`onDisable` 不取消任务/不注销句柄(重载后翻倍执行);
- 拿训练记忆里的 Paper API 直接落码不核实。

---

*AGENTS.md · 工具性提示词(非设计章节)· 随版本链维护:框架基线变更(v1.x)时同步 §0/§3/§4/§6。*
