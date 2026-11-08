# CONTEXT-玩法术语.md — SHDF 玩法线领域术语表

> SHDF 具体玩法设计案 · 玩法线(基于 HydroKarbs MiniGame Framework · v1.14(现行);本表与各篇正文写定于 v1.0,旧包根/主类/API_VERSION/能力标签等旧口径按 [README.md](README.md) 对齐注 ①~⑨ 重读;**大厅系词条已随玩法线 v2.0 设计变更注(README ⑩)改写**——04 篇全文重写,本表大厅部分按新口径收录,原词条去向见 04 §4.9 存档摘要)。
> 本表**接收自框架侧 [../CONTEXT.md](../CONTEXT.md) 迁出/标注为 SHDF 扩展线的术语**(Lobby、角色桥接/RoleFacade/RoleComponent 等),并收录玩法线专有概念;框架机制术语(会话/实例/组件/服务/切换/交接等)以框架侧 CONTEXT.md 为准,本表不重复。只放术语与定义,不放实现细节;出处篇目见各条末尾(04/05/08/09 指本目录对应篇)。

## 代号与宿主

| 术语 | 定义 |
|---|---|
| **HydroKarbs MiniGame Framework(简称 **HKF**,现行 v1.14)** | 本玩法线所依赖的纯框架(见 [../](../README.md)):只含框架架构,零玩法细节;SHDF 是它的第一个宿主。 |
| **SHDF(宿主/玩法包代号)** | 框架的第一个宿主:基于 HKF(见上条)实现大厅与四模式玩法,并把外部角色系统经桥接/角色组件接入。框架插件主类/装配为 `HydroKarbsPlugin`/`HydroKarbsAssembly`(包根 `com.hydrokarbs`,★v1.1-D13);宿主为独立插件,自有主类与独立顶级包根 `com.shdf.*`(旧根 `com.shdf.match` 废止不复用)——本表与各篇正文中的旧包根按 [README.md](README.md) 对齐注重读。历史代号 SHDFMatch,存档于 [../17-v4变更指示书.md](../17-v4变更指示书.md)、[../18-v4.1修正案-进程级服务层与ServiceRegistry.md](../18-v4.1修正案-进程级服务层与ServiceRegistry.md) 与 [../docs/adr/](../docs/adr/)。 |
| **ShadowHunterRoles(角色系统)** | 外部角色插件(插件名 `ShadowHunterRolesPlugin`,基线 1.0.0,`mavenLocal` + `compileOnly`):SHDF 玩法线的宿主扩展依赖。不发任何 Bukkit 事件,对外唯一出口 = `RoleAPI`(08 §8.1/§8.10)。 |

## 大厅与竞技模式

| 术语 | 定义 |
|---|---|
| **主世界大厅(MainWorldLobby,v2.0)** | 设在**服务器主世界**的大厅,宿主插件(SHDF)**自管**:不建会话、不注册 MatchMode、不占世界副本;职责暂定只有**模式投票(LobbyVote)+ 准备(ready)**。框架对大厅构造性零感知;解散接引由宿主完成(框架解散只送主世界出生点)(04 §4.1/§4.2/§4.6)。 |
| **开局判定 / MatchBootstrap(开局单闸)** | 大厅侧"可以开启对局"的判定门(人数/票选/地图/模式/角色在场五门,全过才开)与 API 托管入口:全工程**唯一**允许调 `createSession` 的地方——`SessionRecipe.of(票选玩法, 票选图)` 建会话 + 逐人 `joinSession`;被拒者留大厅(04 §4.3)。 |
| **会话流程策略(SessionFlowPolicy:arena / rotation)** | 每玩法一个的宿主配置(`games/<玩法>/game.yml` 的 `session-flow`):**arena** = 一会话一局,局末散场引导 + 空场收尾解散回大厅;**rotation** = 同会话局末投票续局链(`switchMode` 显式 intent)。玩法内部推进(回合/阶段)一律零切换;大厅永不为切换目标(04 §4.4)。 |
| **MatchEndReturn(散场引导组件)** | 宿主会话组件(session 级,随 `SessionRecipe.extraComponents` 贡献):消费局末无续局信号 → 引导全员离场(`leaveSession` + 宿主传送回大厅)→ `EmptyArenaComponent` 空场收尾 `requestDissolve(EMPTY)`。arena 流程的正常出口(04 §4.4/§4.6)。 |
| **玩法配置目录(games/\<玩法\>/)** | 宿主数据目录下每玩法一个配置目录:`game.yml`(玩法参数 + session-flow,工厂闭包注入,不经框架 `modes/<id>.yml` 通道)+ `maps/<图>/map.yml`(该地图的**该玩法视角**词料:出生点分组/区域/交互点);世界目录另置宿主 `worlds/` 仓库(一图一目录,多玩法共用时词料按玩法命名空间并存、tags 取并集)(04 §4.5)。 |
| **ShdfMapAssembler(地图装配器)** | 宿主启动/重载管线:扫描 `games/*/maps/*` → 按世界目录去重合并词料(`AttributeBag` 玩法命名空间 Key + `MapDefinition.TAGS` 并集)→ 构造 `MapDefinition(worldDirectoryPath, resetStrategy, metadata)` 三字段 → 先落盘后 `registerMap`(04 §4.5)。 |
| **LobbyVote(大厅投票,宿主版)** | 大厅"下一场玩什么"投票:多数胜/平局 TieBreak/每人一票可改票/零票 rotation 轮换兜底——语义承接 v1.0 VoteComponent 的大厅用法,实现为宿主普通代码(不经实例);结果消费者是 `MatchBootstrap` 而非 `TransitionIntent`(04 §4.2)。 |
| **炸弹攻防(BombDefusal)** | 回合制竞技模式:ATTACKER/DEFENDER 两队 + 观战;阶段链 Warmup→Prep→Live→RoundEnd 循环至 MatchEnd;回合内禁重生;回合分先到 `bestOf` 胜。独有件:RoundComponent / BombObjective / PlantInteraction(EconomyComponent 可选);对局中入场 REJECT(05 §5.1/§5.2-玩法 A)。默认会话流程 **arena**(单场解散回大厅,04 §4.4)。 |
| **团队死斗(TeamDeathmatch,TDM)** | 连续竞技模式:2~4 队击杀计分(可加权/连杀),分数阈值 / 时限 / 团灭判定;可选 SuddenDeathComponent 加时;TDM_POLICY 宽松可重复、复活即应用(05 §5.2-玩法 B)。默认会话流程 **rotation**(同会话局末投票续局链,04 §4.4)。 |
| **首领战(BossRaid)** | Boss 阵营对抗模式:1 队玩家 vs 1 个 Boss;血线门阶段 P1→P2→P3→Resolution→MatchEnd;限定生命数。独有件:BossComponent / BossPhaseGate(AggroComponent 可选);BOSS_POLICY 锁定池禁换角(05 §5.2-玩法 C)。默认会话流程 **arena**(04 §4.4)。 |
| **RoundComponent** | 回合循环组件:Prep/Live/RoundEnd、回合重置、累计比分;内部消费 PhaseComponent/EndGateComponent;炸弹攻防独有挂载。回合内 WIN 只是回合结束,不触发 switchMode(05 §5.2/§5.3)。 |
| **BombObjective** | 炸弹目标实例(每张图每个炸弹点一个):状态机 UNPLANTED→PLANTED→EXPLODED/DEFUSED + 进度;checkMap 要求地图有 bomb-site 区域(05 §5.2-玩法 A、05 §5.8)。 |
| **PlantInteraction** | 安放/拆除的读条交互(经 InteractionComponent 分发);checkMap 要求地图有交互点(05 §5.8)。 |
| **BombDefusalModes(玩法工厂)** | 炸弹攻防工厂:只有 Builder 声明、零生命周期代码——组件表/阶段表/规则簿/结束门/角色策略全是构造数据(05 §5.8,自框架侧 12 迁入)。 |
| **BombConfig** | 炸弹攻防平衡参数载体(引信/拆弹/爆炸半径等,来自 `modes/bomb_defusal.yml`)(05 §5.4/§5.8)。 |
| **EconomyComponent** | 局内货币、购买菜单、装备重置;炸弹攻防可选件(05 §5.5-#19)。 |
| **SuddenDeathComponent** | 加时组件:关闭重生、缩小边界、双倍分;TDM 可选挂载(05 §5.5-#20)。 |
| **BossComponent** | Boss 实体生成、血线阶段推进(消费 PhaseComponent.jump)、死亡广播;首领战独有;`Spec.checkMap` 前置校验 boss 出生点,缺则切换 A3 即拒(05 §5.2-玩法 C)。 |
| **BossPhaseGate** | Boss 血线阶段门(按 HP 阈值升序判定推进)(05 §5.2-玩法 C)。 |
| **AggroComponent** | 仇恨表、目标选择、仇恨转移;首领战可选件(05 §5.5-#22)。 |
| **IdleActivitiesComponent(已随 v2.0 暂缓)** | 大厅闲玩:靶场/跑酷/热身区,v1.0 为实例组件(地图 regions+interact-points 声明驱动);**玩法线 v2.0 起大厅职责暂定只有模式投票与准备**,该组件暂缓——将来如做,以宿主普通 Bukkit 逻辑实现(04 §4.9;框架侧 00-A2)。 |
| **VoteComponent(局末投票;大厅用法已宿主化)** | 实例内通用投票:**现行用法只剩竞技模式 MatchEnd 阶段投"下一场玩什么/哪张图"**(rotation 流程续局 intent 的来源,结果经 `HANDOFF_RESULT` 交接);v1.0 的"大厅 VOTING 阶段投模式"用法随 v2.0 大厅宿主化改由 **LobbyVote** 承担(04 §4.2/§4.4;原双重用法见 04 v1.0 §4.5,已废止)。 |
| **LobbyCountdownEndCondition(已废止)** | v1.0 大厅模式的结束判定器(COUNTDOWN 到点 → `NEXT` + 票选 intent);**v2.0 起开局不再是切换**,该判定器随"大厅 = 模式"设计整体废止(04 §4.9)。 |

## 角色接入(两层;SHDF 宿主扩展,非框架组成)

| 术语 | 定义 |
|---|---|
| **角色桥接层(bridge/role)** | (自框架侧 CONTEXT 接收)"怎么和角色系统说话":无状态原语层,进程级;无实例状态、无玩法语义(防腐层纪律)。整体属 SHDF 宿主扩展,v1.0 起不在框架基线(08)。 |
| **RoleFacade** | (自框架侧 CONTEXT 接收)防腐层唯一类:全工程**唯一**允许 import 角色插件的地方;RoleAPI 14 方法 + 阵营操作面的类型化壳;懒解析(`@Nullable api` + 首次使用 load);所有方法只能主线程调用;签名只用 JDK/Bukkit 类型,不泄漏 RoleAPI 类型(08 §8.2)。 |
| **RoleStateAccess** | (自框架侧 CONTEXT 接收)字符串操作面 → 类型化 API(energy/sante/buffs 动词面);`OptionalInt` 显式建模"读不到"(操作面 `null` 有双重语义)(08 §8.3)。 |
| **RoleDescriptor / RoleFaction** | 自家值对象(不泄漏 `RoleInfo`:id/名/描述/图标,不含阵营)/ 阵营枚举(SHADOW/HUNTER/UNKNOWN/OTHER,OTHER 兜未来新阵营;读写映射口径见 08 §8.2)。 |
| **RoleCatalog** | 角色目录只读快照(awake 期解析缓存,明确标注 TTL);两边不互缓存对方权威数据,快照除外(08 §8.5、09 §9.4)。 |
| **RoleEventSynth** | 角色事件合成器(RoleComponent 的无状态协作者,包私有):输入 = 原版事件 + RoleFacade 原语,输出 = 本包合成事件;★v3.1 随事件词汇住 `component/role`,不住桥接(08 §8.4)。 |
| **RoleKillEvent / RoleStateChangedEvent** | 合成角色事件词汇(住 `component/role`):击杀归属(LOWEST 读死前角色)/ 状态采样差分(仅真变化才发);模式规则与计分的消费面(08 §8.4、09 §9.4)。 |
| **RoleComponent** | (自框架侧 CONTEXT 接收)"这一局怎么管角色":L3 组件,模式实例内一切"角色系统接入"的**唯一落点**;四个内部 facet——Loadout(选角授权/占用表)、State(数值/采样差分)、Hostility(敌对口径)、Rewards(事件→角色操作);模式只挂这一个组件,其他组件经其强类型读口消费;`stop()` 全员 clearRole 是角色清场唯一闸门(09 §9.3/§9.4)。 |
| **RolePolicy** | (自框架侧 CONTEXT 接收)模式对角色系统的"规则请求":池/去重/换角限额/授权时机/兜底策略,纯数据,`RoleComponent.Spec` 构造参数,由 Loadout facet 解释执行;模式永远不直连 RoleAPI 做策略判断(08 §8.6)。BOMB/TDM/BOSS 三策常量可跨模式安全共享(Spec freeze)。 |
| **RoleReward** | 事件→角色操作的奖励映射(Spec 构造数据,默认空):如 `bomb.defused → addEnergy(+20)`(09 §9.3/§9.6)。 |
| **RoleSnapshot** | 采样差分的上一帧快照(住 RoleComponent,随实例生灭)(09 §9.3)。 |
| **RoleSignalListener** | LOWEST 死亡信号监听器:**只路由,不合成**——把 PlayerDeathEvent 路由给 `RoleComponent.onVanillaDeath`;优先级是正确性的一部分(必须赶在角色系统 NORMAL 清角之前)(08 §8.9,自框架侧 12 迁入)。 |
| **Capability("role" 能力标签;机制已退场,历史口径)** | (自框架侧 CONTEXT 接收)v1.0~v1.6 的外部能力软依赖机制:字符串标签由桥接层注册/注销、`MatchModeMeta.requiredCapabilities` 声明、开局期校验。**框架 v1.7(25 号)起字符串能力标签全链退役**(README ⑥)——现行口径:外部能力 = **会话组件桥接**(类型化 `requires`/`requiresOptional`,装配期响亮);v2.0 大厅侧另以开局门自查角色在场性承担(04 §4.3/§4.7)。08 §8.8 原文读作历史(施工 3.3 同)。 |
| **RoleAPI 冻结面** | 角色插件对外唯一出口:14 方法(`RoleApiSurfaceTest` 同源)+ 阵营五条经组件操作面(组件 id `faction`);桥接层 1:1 覆盖,未来加方法缺口一目了然(08 §8.10,自框架侧 16 §16.3 迁入)。 |
| **切换 B3 角色兜底(已废)** | v3.1 已删除的引擎级兜底(引擎 import 桥接做清场):结构性保证已由"单写者 + stop 对称"承担;契约与测试见 09 §9.7(清场单闸;框架侧 03 留有勘误注)。 |

---

*SHDF 具体玩法设计案 · 玩法线(v1.0 写定;v1.1~v1.14 对齐注与 v2.0 大厅设计变更注见 [README.md](README.md),大厅系词条已按 v2.0 改写);基于 HydroKarbs MiniGame Framework v1.14(见 [../](../README.md));框架机制术语见 [../CONTEXT.md](../CONTEXT.md)。*
