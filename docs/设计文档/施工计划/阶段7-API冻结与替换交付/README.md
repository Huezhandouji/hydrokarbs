# 阶段7 — API 冻结与替换交付(路线图 P7.5 + P7.6 + P8)

> HydroKarbs MiniGame Framework · v1.3(现行;v1.2 于 2026-10-23 地图注册通道;v1.1 于 2026-10-23 框架独立插件化拆分与 API 面扩充;v1.0 于 2026-10-22 自 SHDFMatch 设计案 v4.1 剥离玩法重构;玩法实现见 Shdf具体玩法设计案/)
> ★v1.3 影响(总览 §11):★10~★12(数据库三验收)归属迁「宿主线(框架侧 v1.3 废止)」——本阶段出口闸门计数 **51→48**;7.1 的「查战绩」实现随 R1 悬置(落地不含 DB 依赖项,回填裁决 21 §6-Q1);发布 checklist 的「shadow jar 含 HikariCP+mysql-connector-j」项废止。
> **★用户排期决议(2026-10-25)**:本阶段执行顺序调整为 **7.1 → 7.2 → 7.5(G1)先行**——提前至阶段4 之前(阶段7 主体只依赖阶段2,已验收 2026-10-25;组件库经模式 Builder 消费、不进 API 面,后补为「只增」关系);**7.4(全量验收与发布)后置至阶段4 完成后**执行;阶段8(G2)仍按 A13 触发条件不设时间表。**本注为施工排期决议、非依赖变更**——各子阶段「前置与依赖」的设计依赖不变(总览 `../README.md` §1/§2 决议注);§2「建议顺序」保留为原设计顺序记录,实际执行按本决议。
> 对应设计案:`07-公共API.md`(§7.1/§7.2/§7.5)、`13-扩展步骤.md`(§13.4/§13.10)、`16-路线图与验收.md`(§16.1 P7.5+P7.6+P8、§16.2 全表)、`00-假设与体检.md`(§0.1 旧插件体检)。术语以根目录 `CONTEXT.md`(v1.1)为准。
> v4 影响:无;HydrokarbsApi 冻结面未变(会话级组件第一版不进公共面)。**★v1.1 影响:冻结面由新增子阶段 7.5 按"只增不改"扩充(v2:API_VERSION/成员/事件/能力/D14),并完成包根更名与拆分彩排(G1);7.1 已交付的 v1 面不动。**
> ★v1.7 注(2026-10-27;修正案 25 号,能力标签机制全链退场):上行 v1.1 影响中的「能力」增量项随冻结面收敛删除(19→17 法,API_VERSION=6;7.1/7.5 两档头部有专项注;阶段3 承接行的能力标签机制条款随 ★32 废止改判 ★60,标签机制框架侧落点 2.6 相应退场)——**原文为施工序列存档,不改写,按本注读**。

## 1. 阶段目标

把框架从"能玩的工程"变成"可被第三方消费的平台 + 可替换旧插件的成品":

1. **对外冻结面**:落成 `com.hydrokarbs.api` 包(★v1.1-D13 包根已更名;7.1 交付时为旧根,随 7.5 全库更名)——`HydrokarbsApi` 接口(§7.1)、`HydrokarbsApiImpl` 薄委托、`SessionView`/`MatchModeInfo`/`MapInfo` 不可变 record 快照,经 `ServicesManager`(`ServicePriority.Normal`)暴露,`HydrokarbsApiSurfaceTest` 反射冻结方法集(学角色系统 `RoleApiSurfaceTest` 的"只增不改"纪律,§7.2、§0.1)。
2. **第三方扩展通路**:第三方插件不改我们的 jar,经 `getServicesManager().register(MatchModeProvider.class, …)` 注册模式(§13.4);我们监听 `ServiceRegisterEvent` 收录(`build()` + `verifyDependencies`,坏模板跳过并 SEVERE),`ServiceUnregisterEvent` 摘除并让相关会话回退 fallback;A9 刻意限制(聚合根 build 后不可变、不能给已注册模式追加组件、要变体就注册新 id,§0.2-A9)写进 api javadoc;交付示例工程 `TheirPlugin`。
3. **旧插件替换**(7.3 迁玩法线:SHDF 迁移线,实现见 `Shdf具体玩法设计案/施工计划/7.3-旧插件迁移替换.md`;框架侧承接其前置 7.1/7.2 全绿):交付旧图配置 → `maps/*.yml`(+`modes/*.yml` 草稿)的迁移脚本与字段映射表;给出 `SHDFGamePlugin` 停用清单与回滚预案;新老并行跑一局对比一致;声明数据不迁移(旧插件零持久化,§0.1)。
4. **发布**:48 条验收(§16.2:40 + ★41~★46/★51~★55,再减 ★10~★12(★v1.3 归宿主线),截至本阶段;★47~★50 属阶段8,见总览 §1)逐条执行并留记录;5 个压测场景演练;发布前 checklist(shadow jar 产物核对(★v1.3:框架 jar **零 DB 依赖**,HikariCP/mysql shade 项废止)、build→runServer 闭环、plugin.yml/api-version、版本号策略)。

**出口闸门(不通过不放行)**——施工计划总览 §1 阶段7 行:

- 假插件(TheirPlugin)经 `MatchModeProvider` 注册并被大厅列出(★20);
- **48 条**验收(§16.2,截至本阶段;★v1.3:原 51 条减 ★10~★12——数据库三验收归属迁「宿主线(框架侧 v1.3 废止)」,由宿主线持久化执行,本阶段不再复验)全绿(★19 由 7.1 首建,★20 由 7.2 首建,★5/★13/★17/★21 在本阶段复验,★27~★33 由 2.6 首建,★41~★46/★51~★55 由 7.5 首建);
- 新老并行跑一局对比一致(P8,§16.1)→(7.3,随 Shdf 侧)。

## 2. 子阶段清单

| 编号 | 标题 | 核心产出 | 出口验收 |
|---|---|---|---|
| 7.1 | HydrokarbsApi 冻结面 | `api/HydrokarbsApi`(10 方法,§7.1)+ `HydrokarbsApiImpl`(薄委托 SessionService/Registry;★v1.3:原「/PersistencePort」委托项随 R1 悬置——查战绩两方法落地不含 DB 依赖项,回填裁决 21 §6-Q1)+ `api/view/` 三个不可变 record + ServicesManager 注册 + `HydrokarbsApiSurfaceTest` | ★19 全绿;`LayeringTest#apiDependsOnlyOnEngine` 不红;runServer 上 `load(HydrokarbsApi.class)` 非 null |
| 7.2 | 第三方注册与扩展 | `listener/ProviderServiceListener`(ServiceRegisterEvent/ServiceUnregisterEvent)+ `MatchModeRegistry` 运行期收录/摘除 + A9 与第三方约束 javadoc + 示例工程 `examples/TheirPlugin` + `ThirdPartyModeRegistrationTest` | ★20;假插件出现在 `modes()` 目录、被大厅投票池列出、可被 switchMode 选中;卸载后目录摘除且会话回退 |
| 7.3 | 旧插件迁移替换(玩法线:SHDF 迁移线,迁 Shdf 侧) | 实现与产出全份见 `Shdf具体玩法设计案/施工计划/7.3-旧插件迁移替换.md`(迁移脚本与 golden test、新配置草稿、迁移报告、SHDFGamePlugin 停用清单与回滚预案,原文逐字随迁) | 新老并行跑一局对比表逐项一致(P8,随 Shdf 侧);框架侧承接:前置 7.1/7.2 全绿 |
| 7.4 | 全量验收与发布 | 全量验收逐条执行记录表 + 5 压测场景记录 + 发布前 checklist(shadow jar 产物核对——★v1.3:框架 jar 零 DB 依赖,HikariCP/mysql shade 项废止、build→runServer 闭环、版本号策略)+ 回滚预案 | §16.2 全表(截至本阶段,★v1.3:★10~★12 归宿主线,48 条)逐条勾;产物核对通过;回滚预案演练一次 |
| **7.5** | **API 面扩充与拆分彩排(★v1.1 新增,G1;含 ★v1.2 地图注册交付物)** | 全库包根更名 `com.hydrokarbs`(D13)+ api 增量(EventSubscription/SessionEventView 族/SessionRecipe/SessionComponentProvider)+ `ApiSurfaceTest`(白名单与签名快照)+ D2/D3/D4/D14 实现(薄委托 + provider 干跑合并校验)+ 拒载样例 + 测试伴随插件 + 地图注册通道(`MapRegistry` 双输入 + `registerMap`/`unregisterMap` + `MapRegistrationTest`) | **形态 A 下 ★41~★46、★51~★55 全绿**;包根更名后全量测试零语义变化;G1 闸门达成(详见 `7.5-API面扩充与拆分彩排.md`) |

建议顺序:7.1 → 7.2(依赖 7.1 的 api 包)→ 7.3(玩法线,Shdf 侧,可与 7.2 并行:迁移脚本不碰 api)→ 7.4(全部就绪后收口)→ **7.5(★v1.1;依赖 7.1/7.2,在 7.4 之后收口 G1——验收记录并入发布口径)**。

## 3. 与其他阶段的接口

**上游已提供(本阶段只消费、不重建)**:

| 来自 | 已提供什么 | 本阶段消费点 |
|---|---|---|
| 阶段1 | `LayeringTest`/`AggregatePurityTest`/`EnginePurityTest`/`MatchModeInstanceServicesMembersTest` 等架构冻结测试(§16.2-★1~4/★22~26) | 7.4 复跑全绿 |
| 阶段2 | `SessionService#switchMode` 两阶段切换与三级回退、`SwitchResult`(3 拒绝码)、`MatchModeSelection`、`Handoff`(§3.2~§3.5);`WorldRefCountTest` | 7.1 控制方法直接委托;7.2 摘除时回退调用 |
| 阶段3(玩法线,整阶段迁 Shdf 侧) | 角色系统接入是 SHDF 宿主扩展,见 `Shdf具体玩法设计案/`(§8.8 现 Shdf 侧 08 篇,子阶段 3.3 随迁);框架侧承接:能力标签机制条款于 2.6 建立,`CapabilitySoftDependTest` 为框架条款(★21) | 7.4 复验 ★21(SHDF 扩展线归属口径:软依赖降级) |
| 阶段4 | 23 个共享组件(§6.1) | TheirPlugin 示例模式复用其公开 Spec |
| 阶段5(框架半段:5.3/5.4) | ~~`PersistencePort.stats/top`、`StatsFlusher`(★10/★11)~~(★v1.3:5.3/5.4 整体迁宿主线,此上游供给废止——7.1 战绩方法随 R1 悬置(落地不含 DB 依赖项)、★10/★11 复验改归宿主线;首模式玩法半段(5.1/5.2)迁 Shdf 侧,实现见 `Shdf具体玩法设计案/施工计划/`) | 7.1 悬置注;7.4 归属迁移注 |
| 阶段6(玩法线,整阶段迁 Shdf 侧) | 各玩法模式实现见 `Shdf具体玩法设计案/施工计划/`;框架承接:"`engine/**` diff 为空"基线(★5) | 7.4 复验口径见 7.4 表第 5 行 |

**下游承诺(本阶段交付后对外生效)**:

| 交付物 | 承诺 |
|---|---|
| 7.1 api 包 | `HydrokarbsApi` 方法集**只增不改**(§7.2 冻结纪律);值对象不可变快照,不泄漏引擎活实例 |
| 7.2 注册面 | 第三方模式注册/摘除生命周期(收录→目录→可切换→卸载回退)与 `TheirPlugin` 可编译可运行的参照实现 |
| 7.3 迁移工具(迁 Shdf 侧) | 交付随玩法线(见 `Shdf具体玩法设计案/施工计划/7.3-旧插件迁移替换.md`);对外承诺不变:服主按停用清单操作可回滚 |
| 7.4 发布 | 40 条验收记录(§16.2 至 7.4 止:★1~★40;★41~★55 由 7.5/8.1 首建后随发布复验) + 压测记录 + 发布 checklist,作为 v(§12.1 版本号)发布的放行依据 |

## 4. 风险与缓解

| 风险 | 缓解 |
|---|---|
| api 泄漏引擎活实例(可变 List/Session/Instance 引用) | 值对象全部 record + `List.copyOf` 深拷贝;SurfaceTest 之外 7.1 DoD 增加"不返回任何 `com.hydrokarbs.engine.*` 活类型"检查(§7.2 表"值对象"行;包根随 7.5 更名) |
| `PlayerStats`/`LeaderboardRow` 住 `data/model`(§12.1)与 `apiDependsOnlyOnEngine`(§16.2)字面冲突;`MapDefinition` 住 `com.hydrokarbs.map` 同理 | 7.1 按"api/view 定义冻结副本、Impl 处转换"落笔;若统筹裁决为放行包名,改 ArchUnit 放行清单须单独 PR 说明(施工计划 §3 纪律 4);已列入待裁决 |
| 第三方插件加载顺序不利(比我们晚启/早停) | `ServiceRegisterEvent` 动态收录(§13.4);TheirPlugin 用 `softdepend: [HydroKarbs]` 双保险;晚于我们的注册同样被事件捕获 |
| 迁移脚本换算错(tick→秒、yaw 缺省、区域角点无序化)(随 7.3 迁 Shdf 侧) | golden test 以旧仓真实 `config.yml`/`maps.yml` 为 fixture(只读);新老并行一局对比兜底;脚本输出"人工确认清单"不猜键位 |
| 40 条验收执行量大(7.4 执行 §16.2 至该子阶段止的 ★1~★40) | 分级:自动测试一条命令跑齐;手动项(5/8/10/11/12;★21 框架口径随自动批复归、宿主侧实测随 Shdf 侧,与 7.4 执行记录表一致)给逐步操作;先自动后手动,红了回退对应子阶段修,不带病发布 |
| 新老对比口径(结构不同:旧 5 Phase 状态机 vs 新组件装配)(随 7.3 迁 Shdf 侧) | 对比表按**行为项**逐条给旧值/新值/一致性,结构差异如实注明为"语义等价说明"(7.3) |
| 主仓 `engine/` 在 7.2 有改动,与 ★5"新增模式不改引擎"口径混淆 | ★5 检验对象是"新增一个模式"的动作(TheirPlugin 全部在 `examples/`);框架收录能力本身的 engine 改动在 7.4 记录表中注明口径 |

## 5. 待裁决问题

> 施工前置复核(2026-10-25)发现的待裁决项;裁决前按登记口径执行,在 7.1 施工落笔时定案,不由施工文档拍死。

1. **api 快照 record `SessionView` 与引擎既有接口 `engine/port/SessionView` 撞名**(2026-10-25 登记):7.1 拟交付的 `api/view/SessionView`(不可变快照 record,§7.1)与 2.6 已交付并在用的 `engine/port/SessionView`(实例侧只读会话视图,★29 `SessionComponentAccessTest` 在用)同名。**倾向:api 侧改名 `SessionInfo`**(与 `MatchModeInfo`/`MapInfo` 命名对齐;引擎侧接口不动,★29 零波及)。**7.1 施工落笔时裁决**:若改名,`HydrokarbsApi` 方法签名、`HydrokarbsApiSurfaceTest` 冻结清单与 7.1 文档全部按 `SessionInfo` 记——冻结面尚未首建,无既有消费者破坏面(7.1 文档已加同款待裁决注,不逐处重复)。
