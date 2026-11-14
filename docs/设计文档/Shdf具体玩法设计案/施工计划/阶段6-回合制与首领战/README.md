# 阶段6 —— 回合制与首领战(路线图 P6 + P7,§16.1)

> SHDF 玩法线;框架施工线见 ../../../施工计划/
> ★v1.1/v1.2 对齐注:本目录子阶段文档写定于 v1.0,旧包根(`com.shdf.match`)、旧主类(`SHDFMatchPlugin`)与单 jar 口径按 `../README.md` 头部对齐注重读(框架 `com.hydrokarbs`、宿主 `com.shdf.*`、API_VERSION=3)。
> 对应设计案:§5.1/§5.2(四模式矩阵与竞技三模式细则)、§6.1-#18~#22、§8.6(BOMB/BOSS_POLICY)、§9.6(角色接入矩阵)、§12.1/§12.2(包结构与工厂骨架)、§15.2(modes/*.yml 与地图样例)、§16.1-P6/P7;路线图:P6(第二模式)+ P7(第三模式)。
> 前置:阶段1~5 出口闸门全绿(施工计划总览 §1:框架线 `../../../施工计划/README.md` §1、本线 `../README.md` §1);术语以 `../../../CONTEXT.md`(v4.1)为准。
> 版本基线(全文一致):Java 21 / PaperMC 1.21.11(`io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT`)/ Gradle Kotlin DSL / `com.shadowHunterRolesPlugin:ShadowHunterRolesPlugin:1.0.0`(mavenLocal、compileOnly、softdepend)/ 命令走 Brigadier(`LifecycleEvents.COMMANDS`)/ DB 与文件 IO 走虚拟线程 / `runTaskTimer` 初始 delay=1L / 调度统一 `GlobalRegionScheduler`(Folia 迁移成本 0)。
> v4 影响:无。
> v4.1 影响:无。
> 引用口径:「§N.M」= 设计篇目第 N.M 节——框架篇目(00~03/06/07/10~15)在 `../../../NN-*.md`,玩法篇目(04/05/08/09)在 `../../NN-*.md`(如 §5.2 = `../../05-四模式矩阵.md` 第 5.2 节);「★N」= `../../../16-路线图与验收.md` §16.2 验收编号。

## 1. 阶段目标(含出口闸门)

把 §5.1 矩阵剩余的两个竞技模式按「Builder 拼组件表」方式落地,并**机械证明** P6/P7 的关键命题——新增模式不改引擎(§16.2-★5):

| 交付 | 内容 |
|---|---|
| bomb_defusal | 回合制(Prep↔Live 循环 + 半场换边)、炸弹目标状态机、长按读条交互、可选经济;2 人起服可打完整回合 |
| boss_raid | 非玩家 Boss 实体、血线阶段门(P1→P2→P3)、Resolution 30s 宽限、限命重生、可选仇恨;起服可打穿全链 |
| 零改动证明 | `engine/**` 与既有 `component/**` 的 PR diff 为空;新增文件全部在 `mode/bomb\|boss/**` + `resources/**` + SPI 一行(§13.2 同构判据) |

**出口闸门(不通过不放行)**:
1. 两个模式起服可玩(§16.1 P6/P7 的验收方式);
2. PR diff 检查:`engine/**` 与既有 `component/**` 路径为空(6.4 执行并留痕,§16.2-★5);
3. `BombDefusalDeclarationTest` / `BossDeclarationTest` 全绿;§16.2 架构测试(1~5、★13~★17、★22~★26)无回归;
4. 施工纪律:每子阶段必含文件清单、离线测试、DoD(总览 §3-3——框架线 `../../../施工计划/README.md` §3-3);不起服不编译不算完成。

## 2. 子阶段清单

| 编号 | 标题 | 核心产出 | 出口验收 |
|---|---|---|---|
| 6.1 | 炸弹攻防-回合制与目标 | `BombDefusalModes` 工厂(§12.2 逐行)、`bombPhases` 阶段表、RoundComponent 接线纪律(回合 WIN≠switchMode)、`BombObjective` 状态机、五个 EndCondition、`BombRules`、`BombConfig.from`、bomb_defusal.yml + SPI 行 | 判定器/状态机/规则全部离线单测(§16.2-9);假实例断言:回合结束只回跳阶段,零 `end()`/`switchMode`;起服日志出现「已加载模式: bomb_defusal」 |
| 6.2 | 炸弹攻防-交互与经济 | `PlantInteraction`(3s/7s 读条 + MOVE/DAMAGE/RELEASE/ITEM_SWITCH 打断 + kit 减半)、checkMap 缺失清单、半场换边、BOMB_POLICY 行为验证、RoleReward(+20 能量)、EconomyComponent 可选、`BombDefusalDeclarationTest` | 起服 2 人:安放→引爆/拆除→超时→换边→整场→回大厅战报(★18);DeclarationTest 冻结组件表;`engine/**`/`component/**` diff 为空 |
| 6.3 | 首领战模式 | `BossRaidModes`、`BossComponent`(start 生成/checkMap 前置/血线 jump)、`BossPhaseGate`(66%/33%)+WipeCounter(30s 宽限)、`AggroComponent`(可选)、BOSS_POLICY、限命重生、三条 EndCondition、ruin_yard 地图、boss_raid.yml、`BossDeclarationTest` | 起服打穿 Warmup→P1→P2→P3→Resolution→回大厅;BossDeclarationTest 绿;`engine/**` diff 为空(§16.1-P7) |
| 6.4 | 引擎零改动验收 | PR diff 检查纪律、CTF 演练预演(§13.1 步骤表 + §13.2 不需动清单)、四模式矩阵回归(§5.1 逐行)、组件复用率统计 | §16.2-★5 成立且留痕;架构测试全量回归绿;§5.1 差异表 12 行全部有验证载体 |

## 3. 与其他阶段的接口

### 上游已提供(本阶段只消费,零改动)

| 来自 | 本阶段消费什么 |
|---|---|
| 阶段1 | `PhaseComponent`(**Timed/Await 两型**阶段表 + `jump` + PhaseChanged——§6.1-#4 的「Objective 阶段表」由 AWAIT 的 entry 谓词覆盖,`../../../CONTEXT.md` 为权威口径,见 1.5 与 6.1 待裁决)、`EndGateComponent`(EndCondition 按 priority 求值 → `svc().self().end()`)、`RuleBookComponent`(Rule 纯函数求值)、`MatchModeInstancePort.end/switchTo`(§3.4、§6.1-#4/#5/#6) |
| 阶段2 | `SessionService#switchMode` 两阶段算法、`Specification#checkMap` 管线(阶段 A3:任何一条非空 → `REJECTED_BY_TARGET_MODE`,detail 带缺失清单,§3.2)、`WorldPort`(REUSE_DIRTY 同图复用)、fallback 三级回退 |
| 阶段3 | `RoleComponent` 唯一读口(`requestRole/applyPending/areHostile/roleIdOf`)、RolePolicy 三策执行面(§8.6)、`RoleKillEvent` 合成(LOWEST 监听器已建,本阶段不新增监听) |
| 阶段4 | L3 组件族(§6.1 中住 `component/**` 的 21 行):RoundComponent(回合循环/重置/比分,内部消费 #4/#5,§6.1-#18)、InteractionComponent(Channel 读条/四类打断,§6.3)、RespawnComponent(NONE/限次,§6.1-#3)、JoinControlComponent(★v3.2 入场控制)、ObjectiveComponent、TeamComponent、ScoreComponent、EconomyComponent、DisplayComponent、StatsComponent 等 |
| 阶段5 | `TeamDeathmatchModes` 工厂先例(声明序/测试形态)、StatsComponent→落库链路(write-behind)、大厅 vote-pool → 模式的端到端基线、`StatsComponent.LAST_SUMMARY` 战报交接(§3.3) |

### 下游承诺(交给阶段7)

| 承诺 | 交给谁 |
|---|---|
| bomb/boss 两个真实消费者,作为 `HydrokarbsApi` 冻结面的行为基线 | 7.1(API 冻结) |
| SPI 注册两行 + 两个工厂类,作为第三方 `MatchModeProvider` 注册的对照样板 | 7.2 |
| `Bomb/BossDeclarationTest`:模式冻结测试的可仿写模板 | 7.2 / 7.4 |
| 「引擎零改动」成立记录(6.4 留痕):阶段7 任何 API 变更不得打破 ★5 | 7.4(总验) |

## 4. 风险与缓解

| 风险 | 影响 | 缓解 |
|---|---|---|
| R1 回合级裁决与 EndGate 契约冲突:§12.2 把五条判定全列在 endgate,而 §3.4 骨架对任何裁决无条件 `end()`,与 §5.2「回合 WIN 不产 EndResult」冲突 | 回合制无法成立;每回合被切回大厅 | 6.1 先核对阶段1 交付的 EndGateComponent 契约;默认按 §5.2 行为注记实现(EndGate 只挂 RoundLimit,回合级四条事件驱动,详见 6.1 待裁决 Q1),裁决后回填 |
| R2 §5.2 骨架的 `EntityDamaged` 事件在实例总线上无生产者(§12.1 监听器清单无通用伤害→总线桥) | Boss 血线推进/仇恨无输入 | 默认 BossComponent.update() 轮询实体血量(零新监听器,零 diff 违规);详见 6.3 待裁决 Q4 |
| R3 读条交互的高频事件(PlayerMoveEvent MONITOR)拖性能 | 卡顿 | 通道结算与打断是 InteractionComponent 机制(阶段4 已验,§15.3-①);6.2 只声明 InteractionPoint 数据,回调内先做廉价判断(§15.1) |
| R4 半场换边与角色重应用的时序竞态 | 角色错池/重复应用 | 换边、重传送、`RoleComponent.applyPending` 在同一次 PhaseChanged 事件栈内完成,无跨 tick 中间态;离线测试冻结 |
| R5 对局中被拒/转观战的中途进入体验差(★v3.2 入场控制) | 玩家流失 | REJECT 的 reason 给明确提示与重试时机;想要"进来看"把 JoinControlComponent.Spec 配为 SPECTATE_WHEN_RUNNING(组件数据可调,零代码)——观战安置走 SPECTATOR 队与 VisibilityComponent;断线宽限由会话层 ConnectionTracker 保证(§3.1) |
| R6 checkMap 拒绝过严导致地图难配 | 地图装载失败排查难 | 缺失清单逐项进 `REJECTED_BY_TARGET_MODE` detail(§3.2-A3、§15.2 铁律 3);6.2/6.3 各有 detail 内容断言测试 |
| R7 起服实测成本高 | 进度拖延 | 离线单测先行全绿(判定器/状态机/规则),起服只跑端到端路径;`gradlew build` → `runServer` 闭环(papermc-plugin-dev 技能流程) |
| R8 Boss 实体清场遗漏 | 实体残留进下一局 | Boss 实体在 `stop()` 与 `start()` 严格对称移除(§16.2-6/7:close 幂等、清场逆序);CleanupComponent 兜底 |
