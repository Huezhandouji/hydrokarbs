# ADR-0009:框架基础设施瘦身与配置中立化(v1.3)

- 状态:已接受(2026-10-25)
- 关联:21-v1.3修正案-框架基础设施瘦身与配置中立化.md(变更指示全文);ADR-0007(API 白名单反面词表中 `PersistencePort`/`data/**` 的历史口径由本案改写);ADR-0008(v1.2 基线)
- 验收:无新增编号(收缩);★10~★12 归属迁移「宿主线(框架侧 v1.3 废止)」

## 背景

用户哲学(最高优先级裁决):**框架不得与任何一个小游戏的实现相关。** 三个残留缺口在 v1.2 基线中仍然存在:①框架持有整套持久化(L5 `data/**` + HikariCP/mysql 依赖 + `engine/port/PersistencePort` + `database:` 配置块),而 `player_stats`/`match_history`/排行榜是战绩域词汇——哪张表、哪列指标由宿主玩法决定(§10.1 的 `boss_kills` 列本就是玩法线示例列);②框架持有表现层组件与渲染器族(`component/display/**` + `render/**` + `display:` 配置块),而 BossBar 行内容是玩法语义(`DisplayPort` 早在 v3.1 已以同一理由删除);③发行配置携带 `lobby` 缺省(`initial-mode`/`fallback-mode`/`arenas` 示例),而 lobby 属 SHDF 玩法线,框架基线并无此模式。另:`PersistencePort` 自 v1 定义至今**零消费者**——引擎内核不调它,组件经 Spec 构造数据避开它,唯一规划中的调用方是 5.4 施工里它的自证实现。

## 决策

1. **持久化整体出框架(R1,D1)**:`data/**`、HikariCP/`mysql-connector-j` 依赖与 shade、`PersistencePort` 接口(连接口一起删)、`PersistencePortImpl`、`database:` 块、`engine.shutdown-flush-budget-seconds`、`sql/` 资源全部移出。判据与 A13/A9 同源:**不为零消费者的接缝保留公共契约**(Assumption-driven,不做 hypothetical seam)。归宿:宿主自建,经自己的组件/服务直连自己的 DB 栈;`StatsComponent`(采集+`StatsDelta` 出口)留框架,落库归宿主。10-数据库篇与 5.3/5.4 施工文档存档保全(加迁出注);**§10.5(Threads 与线程边界)例外保留为框架基线**——它是 `util/Threads` 与主线程单点回程纪律(★8)的设计权威。
2. **表现层出框架(R2,D2)**:`component/display/**`、`render/**`、`display:` 配置块移出;施工子阶段 4.3 撤销;06 §6.2 数据驱动设计资产**迁 Shdf 玩法线保全**(`Shdf具体玩法设计案/10-表现层组件.md`);P3 组件计数 L3 实数 21→20。
3. **发行配置中立化(R3,D3)**:`initial-mode`/`fallback-mode` 默认改空+注释引导;`arenas` 示例注释化;L0 缺省兜底改空串;空值语义 = 初始模式未注册 → WARN(不再 ERROR)+ 会话保留待后补切换;fallback 空/未注册 → 回退链跳过一级直接二级 dissolve;`config-version` 2→3;A3 表述改「回退去向 = 配置指向的任意已注册模式,框架不设出厂模式」。与 ★4(引擎零 mode id 字面量)同一纪律在发行面的收口。(**★D5 同日增补:`initial-mode`/`arenas` 键随启动建会话特性整体删除,见第 5 条。**)
4. **信任边界键保留与 flush 键废止(D4)**:`performance.component-budget-ms` 保留——v1.1 D9③ 信任边界观测键,观测对象是任意(含跨 jar 第三方)组件的单 tick 预算,非玩法参数;`shutdown-flush-budget-seconds` 废止——唯一服务对象 `StatsFlusher#drainAndShutdown` 随 L5 迁宿主线。
5. **启动建会话特性删除,会话创建权移交宿主(D5,2026-10-25 增补)**:删除「启动即自动创建会话」特性——`HydroKarbsAssembly` 的整段拓扑装配(topology 读取/rotation·arena 分支/建会话循环/启动期首切换)移出框架基线,框架 `onEnable` **零会话**(零会话起服为合法状态;`EngineTicker` 空转与 `onDisable` 空集解散循环本就幂等,无需改);配置键 `session.topology`/`session.arenas`/`session.initial-mode`/`session.dissolve-after-empty-seconds` 一并删除(空场组件时长改由宿主组装 `SessionSpec.sessionComponents` 时直接喂 `EmptyArenaComponent.Spec.after`);`session.fallback-mode`/`min-dwell-ticks`/`disconnect-grace-seconds` **保留**(内核节律,与谁建会话无关)。判据与 D3 同源且更进一步:`session.topology`/`arenas`/`initial-mode` 是框架替宿主预设的**部署形态与开局动作**——属玩法编排而非框架机制;`SessionService.create(SessionSpec)` 本就是会话唯一创建路径(§3.2 冻结签名),`HydrokarbsApi.createSession(...)`/`SessionRecipe.initialModeId`(v1.1-D14)已确立「调用方填初始模式」形态,D5 把宿主拉平到同一口径;启动期首切换的日志噪音/状态分叉与形态 B 的「拓扑装配延迟至首 tick/`ServerLoadEvent`」补丁随创建权移交整体消失。语义后果:拓扑 = 宿主调几次 `create`(§3.6 引擎零感知口径更纯粹,装配期的拓扑分支也不再持有);`SessionSpec.initialModeId` 字段保留(每会话首切换目标,宿主填;目标未注册走既有 A1 `MODE_NOT_FOUND` 拒绝、会话保留待机);`SessionPolicy` 的 topology/dissolveAfterEmptySeconds 字段失去填充来源(瘦身登记 21 §5 回填义务-7);`EmptyArenaComponent` 机制零改动(★31)。零新增验收编号(拓扑装配不在任何冻结面,既有断言零触碰零放宽)。
5. **验收纪律**:编号只增不改不重排;★10~★12 归属改标「宿主线(框架侧 v1.3 废止)」,命题随宿主线执行;★1~★9、★13~★55 断言与 ArchUnit/源码扫描**零放宽**;07 查战绩面随 R1 悬置(回填义务见 21 §5)。

## 替代方案(已否)

- **保留 `PersistencePort` 接口(只删 L5 实现)**:为零消费者的接缝保留公共契约 = hypothetical seam(与 A9「不提供运行期装饰入口」、A13「不做假想缝」同判据);且端口方法面(`stats/top/accumulate/flushNow`)是战绩域词汇,框架不认识内容;接口留守还会让「框架该不该带 DB」在每次扩面时重新开庭。否。
- **直接销毁表现层设计(不迁 Shdf)**:`DisplayModel` 数据驱动设计是已付的设计资产——旧插件「表现层写死玩法语义、换个模式整套报废」教训的反证(00 §0.1 体检行),销毁即丢;迁玩法线保全零框架负担(框架不再 import 它,设计文字住宿主侧)。否。
- **`data/**` 降为「可选模块」留在框架 jar**:可选项仍携带 HikariCP/驱动 shade 体积、`database:` 配置面与 `PersistencePort` 契约位,中立化不彻底;真有跨玩法通用持久化诉求时按 A13 以真实消费者重新立项(届时走「只增不改」新版本)。否。

## 后果

- 正:框架 jar 零 DB 依赖与 shade 体积;配置面零玩法缺省(「零玩法配置起框架」成为合法状态,纯框架自检/测试部署受益);框架与「任一小游戏的实现」在持久化/表现/发行配置三个面彻底解耦;基线与用户哲学一致,审查判据变简单(框架里不该再有战绩表、渲染器与 lobby 字样)。
- **D5 增补后果(2026-10-25)**:正——框架 onEnable 零会话,「会话怎么开」(建几个/开局进哪个模式/挂什么会话组件)全部成为宿主编排;启动期首切换噪音源消失,形态 B 加载时序补丁作废,§3.6「引擎零感知」在装配面也成立。负——宿主承担会话编排(建会话时机、`SessionSpec.initialModeId`、空场组件 `after` 自供,登记 21 §5 回填义务-7);存量部署的 `session.topology`/`arenas`/`initial-mode`/`dissolve-after-empty-seconds` 四键升级后失效(迁移口径见施工计划 §11.2);D3-① 的「启动装配 WARN 待机」路径随装配消失(待机语义由 create 既有 A1 拒绝机制承载)。
- 负:宿主自建持久化的施工负担转移到玩法线(Shdf 侧需新增对应子阶段与 ★10~★12 的执行);07 查战绩面悬置(签名类型 `PlayerStats`/`LeaderboardRow` 原住 L5,回填裁决登记 21 §6-Q1);★10~★12 框架侧废止执行(命题迁宿主线)。
- 未决移交:21 §6(Q1 查战绩缺席语义 / Q2 宿主持久化形状 / Q3 通用持久化是否回迁)。
