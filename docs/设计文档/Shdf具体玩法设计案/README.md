# Shdf具体玩法设计案 —— SHDF 玩法线总览(框架的第一个宿主)

> SHDF 具体玩法设计案 · 玩法线(基于 HydroKarbs MiniGame Framework · **v1.14(现行)**;正文四篇与施工子阶段文档写定于 v1.0,自 SHDFMatch 设计案 v4.1 剥离玩法重构而来;**v1.1~v1.14 对齐注与玩法线 v2.0 大厅设计变更注见下,优先级高于正文旧口径**)。
> **本目录全部内容基于 HydroKarbs MiniGame Framework(见 [../](../README.md))实现,是框架的第一个宿主(SHDF)**;框架本体为纯架构设计案(零玩法细节),大厅/四模式等具体玩法的设计、实现、配置与验收全部在本目录。
> 玩法线术语表见 [CONTEXT-玩法术语.md](CONTEXT-玩法术语.md);框架侧术语见 [../CONTEXT.md](../CONTEXT.md)。
>
> **★v1.1/v1.2/v1.3 对齐注(v1.3 时点基准;正文未随 v1.1~v1.3 改写,凡与正文旧口径冲突以本注为准;现行读数以 ⑨ 为准)**:框架侧此后经历 [../19-v1.1修正案](../19-v1.1修正案-框架独立插件化拆分与API面扩充.md)(框架独立插件化拆分与 API 面扩充,ADR-0007)、[../20-v1.2修正案](../20-v1.2修正案-地图注册通道.md)(地图注册通道,ADR-0008)与 [../21-v1.3修正案](../21-v1.3修正案-框架基础设施瘦身与配置中立化.md)(框架基础设施瘦身与配置中立化,ADR-0009——**R1 持久化整体出框架:宿主自建 DB 栈(框架 data//PersistencePort/database: 块废除,★10~★12 命题随宿主线执行);R2 表现层出框架:DisplayComponent/DisplayModel 族与渲染器迁本目录(新篇 [10-表现层组件](10-表现层组件.md));R3 发行配置中立化:框架 `initial-mode`/`fallback-mode` 默认改空——**宿主必须显式填自己的模式 id**,空 fallback → 切换失败直接 dissolve**)。重读规则:
> ①**包根**:正文旧包根 `com.shdf.match.*` 一律重读为——框架类 `com.hydrokarbs.*`(D13 全库更名,框架线 7.5 落地;`META-INF/services/com.shdf.match.engine.mode.MatchModeProvider` 同步为 `com.hydrokarbs.engine.mode.MatchModeProvider`);宿主(SHDF)代码另立独立顶级包 `com.shdf.*`(旧根不复用;如 `com.shdf.mode.*`、`com.shdf.bridge.role`、`com.shdf.component.role`)。
> ②**主类与装配**:宿主不再共用框架主类——框架插件为 `com.hydrokarbs.HydroKarbsPlugin`/`HydroKarbsAssembly`;宿主是独立插件,自有主类与装配(如 `com.shdf.ShdfPlugin`);`SHDFMatchPlugin`/`SHDFMatchAssembly` 为 v1.0 前旧名。
> ③**交付形态与 API 消费**:G1(框架线 7.5)起宿主代码按 API 白名单纪律写,仅 L0 装配/监听/命令三处豁免可触内部面;G2(框架线 8.1)后宿主插件 `plugin.yml` 写 `depend: [HydroKarbs]`、禁 shade(14.35);消费方 `onEnable` 首行检查 `API_VERSION`(v1.1 = 2,v1.2 = 3;14.36)。宿主的编排/感知诉求优先经扩充后的 `MiniGameApi`(成员操作/事件订阅/能力标签注册/会话组件贡献/地图注册,../07 §7.1/§7.5、../13 §13.10~§13.11)。
> ④**验收计数**:§16.2 现为 **★1~★55 共 55 条**(v1.1/1.2 新增 ★41~★55,框架线 7.5/8.1 建立);本目录旧文的"40 条"为 v1.0 时点数。
> ⑤**术语**:v3.2 起无 `joinPolicy` 字段(入场裁决 = JoinControlComponent,../03 §3.2.1);引擎切换 B3 角色兜底 v3.1 已删(清场单闸 = `RoleComponent.stop`,08/09)。
> ⑥**★v1.7 对齐注(25 号修正案,能力标签机制退场)**:框架侧字符串能力标签机制全链退役(注册表/A2 校验/`registerCapability` 两法/各声明分量)——本目录 §8.8 的标签软依赖口径与玩法线施工子阶段 **3.3(Capability 软依赖机制)** 读作**历史口径**;**外部能力 = 会话组件桥接**:提供方(或宿主自持防腐层)经 `registerSessionComponents`/`SessionRecipe` 贡献类型化桥接会话组件,消费方组件 `requires`/`requiresOptional` 类型化声明依赖(装配期响亮、运行期 quarantine;软依赖 = `requiresOptional`,★21/★60);`API_VERSION` ★v1.7 起 = 6、验收计数现为 **60 条**(本行读数优先于 ③/④ 的旧值);玩法线正文不改写。
> ⑦**★v1.8 对齐注(26 号修正案,地图注册去模板根——声明直交路径 + MapDefinition 瘦身)**:框架侧「模板寄居框架数据目录 `maps/templates/`」约定退场——`MapDefinition` 收为 `(worldDirectoryPath, metaAttributes)` 两分量:世界目录由声明直指任意路径、**注册前落盘就位**(先落盘世界目录 → 构造声明 → registerMap,14.39),tags/处置策略走 `MetaAttributes` 约定键 `map.tags`/`map.reset-strategy`,id = 声明目录名路径派生(目录查重/副本命名/钉图键同源);本目录涉及 `registerMap`/地图构造的正文段落读作**新形状**;`API_VERSION` ★v1.8 起 = 7、验收计数现为 **64 条**(本行读数已被 ⑨ 终结);玩法线正文不改写。
> ⑧**★v1.4~v1.6 对齐注(22~24 号修正案,补记;正文旧口径以下述为准)**:[v1.4](../22-v1.4修正案-API面收敛与地图注册单通道.md)——两参 `createSession(String, String)` 重载删除,`createSession(SessionRecipe)` 成为建会话唯一形态(便捷工厂 `SessionRecipe.of(modeId, mapId)`);「配置文件声明地图」通道整体退场(`maps/*.yml` 启动扫描/`YamlCodec.readMap`/`MapDefinition.from` 全删)——地图仅经 `registerMap` 进目录、目录顺序 = 注册序,**宿主如需配置驱动的地图清单须自理解析**(本线 v2.0 落地为 `games/<玩法>/` 配置目录,04 §4.5)。[v1.5](../23-v1.5修正案-REUSE_DIRTY副本随会话解散销毁.md)——REUSE_DIRTY 副本随会话解散销毁(WorldPort 内部口 `discardSession`),宿主零感知。[v1.6](../24-v1.6修正案-API面显式注册模式与会话组件.md)——第三方模式注册改**显式**:`registerMode(Plugin, MatchModeProvider)`/`unregisterMode`/`registerSessionComponents(Plugin, specs)`/`unregisterSessionComponents(Plugin)` 四法进冻结面;SPI 提供方通道(ServicesManager 注册 MatchModeProvider/SessionComponentProvider)退场——**宿主 jar 不再写 `META-INF/services` 模式清单**(2.5 旧步骤 8「SPI 追加两行」等口径废止);注册时序反转:宿主 `plugin.yml` `depend: [Hydrokarbs]` + onEnable load 后显式注册。
> ⑨**★v1.9~v1.14 对齐注(现行;读数优先于 ③/⑥/⑦ 中的旧值)**:[v1.9](../27-v1.9修正案-切换与解散的世界清场.md)——切换/解散的世界清场(遗留人员传送),同图重开自出生点起步;依赖跨局位置保留的组件须自读 Handoff 自行传送。[v1.10+v1.11](../29-v1.11修正案-属性袋纯容器回归与声明词料三件退场.md)——`MapDefinition` **三字段定案** `(Path worldDirectoryPath, ResetStrategy resetStrategy, AttributeBag metadata)`(⑦ 的两分量/MetaAttributes 口径废止);`AttributeBag` = 纯不可变容器(宿主自定 `Key` 同袋存放,框架唯一解释 `TAGS` 约定键,`MapDefinition.TAGS`);框架 map/ 包的 Bounds/RegionSet/NamedRegion/SpawnPoint/InteractPoint 整体删除——**出生点/区域/交互点词料归宿主**(放 metadata 玩法命名空间键,04 §4.5)。[v1.12](../30-v1.12修正案-JoinDecision二值化与SPECTATE退场.md)——JoinDecision 二值 ADMIT/REJECT,SPECTATE 退场;观战安置是组件侧词汇(05 §5.1「对局中 SPECTATE」读作「组件侧观战安置」)。[v1.13](../31-v1.13修正案-会话模式实例不变式与缺省切换管线退场.md)——会话模式实例不变式(任何时刻必有一实例,首切失败泊回退不再悬空);缺省切换管线退场——`MatchModeMeta.defaultTransition`/`DefaultTransition` 删除,meta 收敛三字段 `(recordsHistory, minParticipants, maxParticipants)`(§4.1 表该行与 05 §5.8 meta 行均为历史口径);无显式去向一律回落 `session.fallback-mode`(默认 = 出厂空模式 **limbo**;框架恰设一个非玩法兜底模式)。[v1.14](../32-v1.14修正案-API版本协商机制退场.md)——**`API_VERSION` 常量删除,数字轴 1~8 终结**:消费方 onEnable 首行只做 `api == null` 在场检查(③/⑥/⑦ 中「API_VERSION=N」的读数全部废止,不再对版本);兼容性陈述 = 版本链文档 + 冻结面测试;**验收计数现为 ★1~★70 共 70 条**;本目录正文旧口径凡涉上述机制,均按本注重读。
> ⑩**★玩法线 v2.0 大厅设计变更注(2026-11-07,本线设计决策)**:大厅退出框架模式体系——**大厅设在服务器主世界,由宿主插件(SHDF)自管**(职责暂定只有**模式投票与准备**);大厅判定可以开启对局后,后续流程调 `MiniGameApi`(`createSession`/`joinSession`)把对局托管给框架;**不同玩法走不同的会话模式切换流程**(arena 单场解散回大厅 / rotation 同会话局末投票续局链);地图与玩法配置由宿主自解析(每个玩法下属一个配置目录 `games/<玩法>/`,内存放对应地图的该玩法配置),与框架交互时装配成 `MapDefinition` 三字段。落点:**[04-大厅模式](04-大厅模式.md) 全文重写**(原「大厅也是一个模式」设计废止,存档摘要 04 §4.9);05/08/09/10 与施工子阶段正文不改写——lobby 列整列、「回大厅」去向、`maps/lobby_main.yml`/`modes/lobby.yml`、`defaultTransition` 行、宿主 SPI 清单等旧口径一律按本注重读;**2.5 重写为「主世界大厅与最小闭环」**;★13 的玩法线半段随变更撤销(命题由框架线闸门独扛),新验收口径见 04 §4.8。

## 定位

- **HydroKarbs MiniGame Framework(v1.14 现行)** = 纯框架:会话层 / 模式声明层 / 组件体系 / 进程级服务 / 切换与交接 / 世界 / 公共 API / 路线图与验收(见 [../](../README.md);持久化 v1.3 起出框架归宿主)。框架不含任何具体玩法(大厅/竞技模式/TDM/炸弹/首领战/角色桥接)的设计细节,确需提及处只留指向注。
- **SHDF** = 宿主工程与玩法包代号:基于上述框架实现玩法(**主世界大厅(宿主自管,v2.0)** + 三竞技模式:炸弹攻防 / 团队死斗(TDM) / 首领战),并把外部角色系统(ShadowHunterRolesPlugin)经桥接层与角色组件接入框架——**角色接入属 SHDF 宿主扩展,不是框架组成**(框架仅提供类型化的会话组件桥接机制,v1.7 起)。
- **历史沿革**:本目录 04/05/08/09 四篇自 SHDFMatch 设计案 v4.1 同源迁入(代号 SHDFMatch 为历史名,存档于 [../17-v4变更指示书.md](../17-v4变更指示书.md)、[../18-v4.1修正案-进程级服务层与ServiceRegistry.md](../18-v4.1修正案-进程级服务层与ServiceRegistry.md) 与 [../docs/adr/](../docs/adr/),原样保留不改)。

## 篇目索引

| 篇 | 内容 | 对应框架概念(../) |
|---|---|---|
| [04-大厅模式](04-大厅模式.md) | **(v2.0 重写)**主世界大厅(宿主自管)与开局托管:职责边界(模式投票+准备)、开局判定 → `createSession`/`joinSession` 托管、arena/rotation 两种会话模式切换流程、`games/<玩法>/` 配置目录自解析 → `MapDefinition` 三字段装配、散场接引回大厅;原"大厅也是一个模式"设计废止(存档摘要 §4.9) | MiniGameApi 公共面(07)、地图注册(../20/26/28/29 号修正案)、会话与模式切换(03)、MatchMode 聚合根(02) |
| [05-四模式矩阵](05-四模式矩阵.md) | 四模式总览与差异化实现(lobby / bomb / tdm / boss):差异与复用矩阵、阶段表/目标/结束条件、责任切分表、配置示例;§5.5~§5.8 为自框架侧迁入的原文(挂法矩阵、Display 三函数、Interaction 原文、BombDefusalModes 工厂与包结构落位) | 组件清单(06)、模式工厂(12)、配置分工(15) |
| [08-角色桥接](08-角色桥接.md) | 角色系统桥接(防腐层):`RoleFacade` / `RoleStateAccess`、角色事件合成、`RolePolicy`、Capability 与软依赖(§8.8,含 plugin.yml/gradle 原文)、`RoleSignalListener` 骨架(§8.9)、RoleAPI 对照表(§8.10) | L1 Bridge 分层(01)、能力标签与开局校验(02/03)、验收条款口径(16) |
| [09-角色组件层](09-角色组件层.md) | `RoleComponent`(桥接之上的游戏侧封装):四 facet、生命周期逐钩子、与桥接分工矩阵、三模式+大厅接入矩阵、测试面;§9.8 为自框架侧迁入的全程时序图(技能释放→角色系统→事件总线→模式规则→持久化) | MatchModeComponent 生命周期(02)、实例事件总线(11) |
| [10-表现层组件](10-表现层组件.md) | **(★v1.3 迁入)**`DisplayComponent` 的数据驱动设计:`DisplayModel`/`DisplayLine`/`Countdown`/`Progress` 中立模型、构建函数示例、宿主侧落地口径——自框架 06 §6.2 v1.3 迁入,原文完整 | 共享组件挂载(06,已迁出)、组件登记回收(02 §2.5) |
| [CONTEXT-玩法术语](CONTEXT-玩法术语.md) | 玩法线领域术语表:自框架侧 CONTEXT 迁出/标注为 SHDF 扩展线的术语(Lobby、角色桥接/RoleFacade/RoleComponent 等)+ 玩法专有概念 | [../CONTEXT.md](../CONTEXT.md)(框架机制术语) |

## 玩法线施工计划

- 位置:[施工计划/](施工计划/)——主世界大厅与最小闭环(2.5,★v2.0 重写)、角色桥接与角色组件(阶段3:3.1 RoleFacade 防腐层 / 3.2 类型化访问与策略数据件 / 3.3 Capability 软依赖机制(历史口径,README ⑥) / 3.4 RoleComponent 四 facet / 3.5 监听器与击杀归属)、TDM 首模式(5.1 工厂 / 5.2 端到端验收)、回合制与首领战(阶段6:6.1~6.4)、旧插件迁移替换(7.3)。
- 玩法线里程碑自框架线施工计划整体迁入(框架线计划与总览见 [../施工计划/](../施工计划/README.md));两线口径:**框架线的引擎零改动验收在玩法线每次接入时复测,`engine/**` 与既有 `component/**` 的 PR diff 必须为空**。
- ★ 机械验收编号沿用框架/玩法两线**同一套编号**,只在口径上标注归属(口径注见 [../16-路线图与验收.md](../16-路线图与验收.md) §16.2;玩法线条目随文档迁本目录施工计划)。

## 迁出原文落点对照(v1.0 拆分)

> 框架侧 06/11/12/16 迁出的玩法实现原文,在本目录的接收落点如下(框架侧相应处仅留指向注):

| 框架侧来源 | 迁出内容 | 本目录落点 |
|---|---|---|
| 06-共享组件 §6.1 | 组件清单 × 四模式使用矩阵原表全文(含 #20~#22 玩法专用件) | [05 §5.5](05-四模式矩阵.md) |
| 06-共享组件 §6.2 | `DisplayComponent` 三模式构建函数(lobbyDisplay/bombDisplay/bossDisplay)与原引语 | [05 §5.6](05-四模式矩阵.md) |
| 06-共享组件 §6.3 | `InteractionComponent` 示例原文(plant_a/defuse_a/range_button 等) | [05 §5.7](05-四模式矩阵.md) |
| 11-事件流 §11.1 | 全程文本时序图(技能释放→角色系统→事件总线→模式规则→持久化)与读回路径 | [09 §9.8](09-角色组件层.md) |
| 12-代码骨架 §12.1 | 包结构树玩法行(mode/lobby\|bomb\|tdm\|boss、modes/maps 配置实例、测试族) | [05 §5.8](05-四模式矩阵.md);RoleSignalListener 树行见 [08 §8.9](08-角色桥接.md) |
| 12-代码骨架 §12.2 | `BombDefusalModes` 玩法工厂块 | [05 §5.8](05-四模式矩阵.md) |
| 12-代码骨架 §12.2 | `RoleSignalListener` 监听器骨架;plugin.yml softdepend 与 build.gradle 角色依赖坐标 | [08 §8.9](08-角色桥接.md);[08 §8.8 尾](08-角色桥接.md) |
| 16-路线图与验收 §16.3 | 与角色系统 `RoleAPI` 的对照表(桥接层实现清单) | [08 §8.10](08-角色桥接.md) |
| 16/施工计划总览 | 玩法线施工里程碑(阶段3 全部、2.5、5.1/5.2、阶段6 全部、7.3) | [施工计划/](施工计划/) |

## 附录:v1.0 拆分改注对照(备查;接收自框架侧 12-代码骨架的迁出块,原文照录)

| 位置 | 原文 | 框架侧现状 |
|---|---|---|
| 12 §12.1 树根/主类 | `SHDFMatch/`、`SHDFMatchPlugin.java`、`SHDFMatchLoader.java` | `HydroKarbs/`、`HydroKarbsPlugin.java`、`HydroKarbsLoader.java`(G4;包名 v1.1 起更名 `com.hydrokarbs`(D13),宿主另立独立顶级包 `com.shdf.*`,旧根不复用) |
| 12 §12.2 装配 | `SHDFMatchAssembly` / 「定名 SHDFMatchAssembly」 | `HydroKarbsAssembly`(G4) |
| 12 §12.2 plugin.yml | `name: SHDFMatch`、命令 `shdfmatch` | `name: HydroKarbs`、命令 `hydrocarbs`(别名 `[sm]` 保留,与全文 /sm 引用兼容) |
| 12 §12.2 StatsSignalListener | 「角色系统可能会取消伤害(免疫、格挡)」 | 「宿主插件可能会取消伤害(免疫、格挡)」 |
| 06 §6.1 表 | 大厅/炸弹攻防/团队死斗/首领战四列挂法矩阵;#20/#21/#22 行 | 矩阵四列整列迁出;#20~#22 留指向行(本目录 05 §5.5 存原文) |
| 11 §11.2 图 | 「RoleComponent.stop 全员清角」行、lobby→bomb 示例 | 图原样保留,读图要点加「(SHDF 示例/扩展线)」注 |

---

*SHDF 具体玩法设计案 · 玩法线(v1.0 写定;v1.1~v1.14 对齐注 ①~⑨ 与玩法线 v2.0 大厅设计变更注 ⑩ 见上;04 篇随 ⑩ 全文重写、10-表现层组件自框架 v1.3 迁入);基于 HydroKarbs MiniGame Framework v1.14(见 [../](../README.md))。*
