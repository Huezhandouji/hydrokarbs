# 阶段2 —— 会话层与模式切换(施工指导)

> HydroKarbs MiniGame Framework · v1.0(自 SHDFMatch 设计案 v4.1 剥离玩法重构;玩法实现见 Shdf具体玩法设计案/)
> 对应路线图:`16-路线图与验收.md`(v2 根目录)§16.1 的 **P1**(会话层,提前到桥接之前)。
> 本章只做施工序列化;设计内容以 `设计案v2/` 根设计文档(v1.0,自 v4.1 重构)为唯一权威,引用样式「§3.2」= 根目录 `03-会话与模式切换.md` 第 3.2 节。
> 版本基线(全文一致):Java 21 / PaperMC 1.21.11 / Gradle Kotlin DSL / `io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT`;`runTaskTimer` 初始 delay=1L;DB 与文件 IO 走虚拟线程;命令走 Brigadier(`LifecycleEvents.COMMANDS`);调度统一走全局调度(`GlobalRegionScheduler` 在普通 Paper 等价,未来 Folia 迁移成本 0,§00-A5)。角色系统接入是 SHDF 宿主扩展,随玩法线阶段3(Shdf 侧)引入,框架基线不含宿主插件坐标,见 Shdf具体玩法设计案/。
> ★v1.7 注(2026-10-27;修正案 25 号,能力标签机制全链退场):本文 2.3 行的「`CapabilityRegistry` 字符串标签机制件」交付项与下游接口段的 A2 能力校验/标签注册表述读作**历史口径**——机制件已删(25 号;2.3/2.6 两档头部有专项注);外部能力 = 会话组件桥接(`requires/requiresOptional`,★21/★60)。**原文为施工序列存档,不改写,按本注读**。

## 1. 阶段目标(含出口闸门)

把阶段1 的"能空跑的引擎"变成"能换模式运行的框架":会话容器、模式注册与配置装载、`switchMode` 两阶段原子切换、世界生命周期全部落地;大厅模式(2.5)迁玩法线(Shdf 侧)落地,框架侧先以两个测试模式建立闸门等价物(见 §1 出口闸门)——但**不碰角色线**(P2,SHDF 宿主扩展线,迁 Shdf 侧)。

**出口闸门(不通过不放行)**:lobby ↔ 空转模式(minilobby)原子往返 **100 次**——①成员零丢失(含断线宽限期成员);②世界句柄零泄漏;③引擎零改动接入两个测试模式(§16.1-P1 验收方式)。闸门的机械部分由本阶段离线测试冻结:★13 大厅只是模式 / ★14 切换原子性 / ★15 切换风暴被挡 / ★16 名册零搬运 / ★17 世界零泄漏 / ★18 交接类型安全(§16.2;玩法侧建立/复验点迁 Shdf 侧,归属口径见总览 §4);**2.6(v4 会话级组件层)另冻结 ★27~★33**(每会话一份/声明序装配与逆序回收/跨级只读/故障隔离不解散/空场策略可插拔/切换实例恒等;§16.1-P1.5、变更指示 §6.4);起服实测以人工循环 ≥10 次抽查闭环时序(时序类断言无法离线覆盖的部分)。

阶段内强约束(全部有测试守门,违者即架构回归):

- `engine/**` 零内容:源码不出现任何 mode id 字面量(**含 `lobby`**),`fallback` 模式是配置项不是常量(§3.2、§14.21,守门测试 1.4 建立的 `EngineHasNoModeStringsTest` 持续生效)。
- 切换只由 `EngineTicker` 驱动(事件派发栈内不发切换,§3.1、§16.4-三律①)。
- 机制在内核、策略可下放为组件(§2.8-决断一 v4 修订版):名册/连接追踪/会话状态机/切换执行/世界句柄仍是 `SessionService` 内建;**空场策略自 v4 起下放为会话级组件**(2.6,`EmptyArenaComponent`;rotation 不挂 = 永不解散)——归属判据见 D7 两问。
- `SessionMember` 与 `Participant` 字段集刻意隔离(§14.18)。

## 2. 子阶段清单

| 编号 | 标题 | 核心产出 | 出口验收 |
|---|---|---|---|
| 2.1 | 会话基础件 | `Session`(状态机 RUNNING/ENDING/SWITCHING/DISSOLVING)、`SessionMember`/`SessionRoster`(原地位移)/`ConnectionTracker`(宽限 60s)/`SessionPolicy`(rotation 常驻、min-dwell 与宽限;★v4:空场部分由 2.6 删除)/`SessionSpec`/`SessionId`/`DissolveReason`/`LeaveReason`、`SessionEventBus` 七事件(§3.1;★v4:2.6 追加第八事件 `SessionComponentQuarantined`) | 名册原地位移、宽限期成员保留、宽限扫描离线单测绿(空场计时归 2.6,★31);七事件可订阅 |
| 2.2 | 模式注册与配置装载 | `MatchModeProvider` SPI + `META-INF/services/com.shdf.match.engine.mode.MatchModeProvider`、`MatchModeRegistry`(ServiceLoader+ServicesManager 双发现,§12.2)、`MatchModeConfigLoader`/`MatchModeConfig`、`MapDefinition`/`RegionSet`/`NamedRegion`/`SpawnPoint`/`InteractPoint`/`MapRegistry`、`YamlCodec`(虚拟线程解析+容错三铁律 §15.2)、`ConfigRepository`、默认资源释放 | 假工厂经双发现注册;坏模板(依赖校验失败)被注册闸拦截并 SEVERE;地图/模式 yml→record 解析容错单测绿;起服默认资源释放且二次启动不覆盖 |
| 2.3 | switchMode 两阶段切换 | `SessionService` 接口 + `SessionServiceImpl`(A1~A6 准备可异步 / B1~B5 执行单 tick 原子,§3.2)、`SwitchResult` 3 拒绝码、`Handoff`(键随主人、merge 外部覆盖)、`MatchModeSelection`、三级回退(新实例失败→fallback 模式→dissolve)、四类防护(§3.5)、`CapabilityRegistry` 字符串标签机制件、`SessionIngestListener`、Brigadier `/sm switch` | ★14/★15/★16/★18 单测绿;★6 close 幂等与 ★7 清场逆序在切换路径复测绿;起服插件 enable、切换被正确拒绝/接受的日志可见 |
| 2.4 | WorldPort 世界生命周期 | `WorldPortImpl`(模板复制走虚拟线程、世界加载主线程、引用计数、`ResetStrategy=REUSE_DIRTY\|TEMPLATE_RESTORE`,§3.7)、`WorldHandle`、副本目录名 = mapId+会话短码、准备超时 30s | ★17 `WorldRefCountTest`(100 次切换零泄漏)绿;同 (map,session) 重复 acquire 幂等返回同句柄 |
| 2.5 | 大厅模式与最小闭环(**迁玩法线**) | 子阶段整体迁 Shdf 侧:`Shdf具体玩法设计案/施工计划/2.5-大厅模式与最小闭环.md`(实现见 Shdf具体玩法设计案/) | 闸门等价物留框架侧:1.4 禁词表含 `lobby` + 2.3 同一入口切换测试先行建立(★13);★18 交接契约测试由 2.3 建立(归属口径见总览 §4) |
| 2.6 | 会话级组件层(★v4.0 新增) | `SessionComponent`(+`Specification`/`Factory`/`Snapshot`)/`SessionComponentRegistry`(§2.5.1/§3.8)、`engine/port/` 五类型(`SessionServices`/`SessionPort`/`SessionComponentLookupPort`/`MatchModeInstanceAccess`/`SessionView`)、`MatchModeInstancePort#session()`、`SessionSpec.sessionComponents` 有序表、L0 装配接线(rotation 不挂 / arena 挂,读 `session.dissolve-after-empty-seconds`;**★v1.3-D5:装配者改宿主——两键废止,spec 与会话组件表由宿主组装,框架 onEnable 零会话**)、`component/session/EmptyArenaComponent`、装配/回收/派发接线 + 会话级故障隔离(D9),删除 `SessionPolicy` 空场部分(§6.4) | ★27~★33(除 ★32 桥接部分——桥接侧迁 Shdf 侧)绿:每会话一份、声明序装配与逆序回收、跨级只读、隔离不解散、空场策略 rotation 不挂=永不触发、100 次切换实例恒等(§16.1-P1.5) |

执行顺序即编号顺序;2.1→2.2 可微并行(互不 import),2.3 依赖 2.1+2.2,2.4 依赖 2.3 的接缝(A6/释放点),2.5(迁玩法线,Shdf 侧)依赖全部;2.6 依赖 2.1+2.3(会话内核与切换),与 2.2/2.4/2.5 无接口耦合,可后置与其余子阶段并行。**交付顺序约束(v4):2.6 必须先于阶段 4/5 的模式开发完成(玩法线部分迁 Shdf 侧)**——两级挂载机制要在模式落地前定死,否则会出现"模式顺手写会话级需求"的回流(变更指示 §6.4;路线图口径:P1.5 排在 P1 之后、P2 之前,§16.1)。

## 3. 与其他阶段的接口

**上游(阶段1 已提供,本阶段直接消费,不得重造)**:

- 1.4 的架构冻结测试族(`LayeringTest`/`AggregatePurityTest`/`EnginePurityTest`/`EngineHasNoModeStringsTest`/`MatchModeInstanceServicesMembersTest`)与本阶段全部新增代码持续对抗——本阶段不改宽任何断言(**★v4 例外:2.6 按变更指示 §6.4/§7 的 v4 修订项——1.4 增补注预留条款落到 `LayeringTest`:`component/session/**` 归 L3、mode 允许 import 它、`MatchModeInstancePort#session()` 允许;属判定补充,非放宽既有断言**)。
- 1.3 的 `MatchModeInstance` 容器:`awakeAll`/`startAll`(名册位移点)/`tick`(只派发)/`close`(幂等、逆序 stop、登记资源回收)/`requestEnd`(置 ENDING,§12.2)。2.3 的 B 阶段只是调用方。
- 1.4 建立的聚合根 `MatchMode`(仅 `verifyDependencies`/`createInstance` 两方法,§2.3)与 `MatchModeComponent.Specification`(`checkMap` 装配期钩子,§2.4)——2.2 注册闸与 2.3 阶段 A 的全部落点。
- 1.5(P0.5)的三迁组件:`PhaseComponent`/`PhaseSpec`、`EndGateComponent`/`EndCondition`/`GateVerdict`/`MatchOutcome`(component/endgate)、`RuleBookComponent`/`Rule`——2.5 大厅组件表(迁 Shdf 侧)的现成件。
- `util/Threads`(主线程单点回程,1.1,§14.1)与 `EngineTicker`(P0 全局单任务,`runTaskTimer` delay=1L;`tickAll` 已调用 `runPendingSwitch`/`tickCurrentInstance`/`idleTimers` 三个会话钩子,§12.2;**★v4:2.6 扩为四步**——新增 ③ `tickSessionComponents()` 会话组件派发、④ 内核 sweep,空场 `idleTimers` 移出内核,§3.8)。
- 阶段1 的 Gradle 工程与 `plugin.yml` 骨架(§12.2;本阶段 2.3 做一处修订:移除 `commands:` 段改全 Brigadier,见 2.3 步骤 12 与待裁决)。

**下游(本阶段向后续阶段承诺)**:

- 对阶段3(SHDF 宿主扩展线,迁 Shdf 侧):`CapabilityRegistry`(2.3)是字符串标签注册机制本体——宿主桥接侧的能力标签注册与软依赖降级(`CapabilitySoftDependTest`,★21,3.3 迁 Shdf 侧;★32 标签机制框架侧由 2.6 建立)只消费它;`SessionServiceImpl` 的 A2 能力校验点(§3.2-A2)已按"标签集合包含性"实现,引擎零字面量;`SessionIngestListener`(2.3)是 join/quit/kick 路由唯一入口,宿主扩展不另设监听。角色系统接入的实现见 Shdf具体玩法设计案/。
- 对阶段4(组件线):`MatchModeConfig` 只读配置视图(2.2)是各组件 Spec 从 `modes/*.yml` 取参的唯一通道;2.5(迁 Shdf 侧)的大厅工厂为阶段4 留有**两行回接口**——`display`(★v1.3:`DisplayComponent` 已迁 Shdf 侧 10,回接在宿主工程内完成)与 `guard`(阶段4 `RegionGuardComponent.Spec`),回接零引擎改动。
- 对阶段5(框架半段 5.3/5.4 持久化——**★v1.3:整体迁宿主线**,总览 §11;玩法半段 5.1/5.2 迁 Shdf 侧):`switchMode` 端到端路径(2.3)与 `Handoff` 交接机制是玩法线首模式接入的统一承接面——★18 的交接契约测试框架侧常驻(`HandoffContractTest`),真键复验(战报交接)属玩法线 5.2(迁 Shdf 侧);~~5.3/5.4 只依赖阶段1 的 `PersistencePort`~~(★v1.3:该接口已删)。首模式与端到端链路的实现见 Shdf具体玩法设计案/。
- 对阶段6/7:★14 在 6.x 带玩法复测(阶段6 迁 Shdf 侧);2.2 的 `MatchModeRegistry` 双发现与 2.3 的 `SessionService` 方法集是 7.1 `HydrokarbsApi` 冻结面与 7.2 第三方注册(`ThirdPartyModeRegistrationTest`,★20)的直接素材——**2.2/2.3 的公开类型签名一经合入即按冻结对待**,改名要过设计评审。

## 4. 风险与缓解

| 风险 | 缓解 |
|---|---|
| 切换 B 阶段在事件派发栈内被执行(重入,§14.17) | 统一挂起-驱动模型:`switchMode` 只做拒绝判定+阶段 A 准备+挂起执行计划;B 唯一入口是 ticker 的 `runPendingSwitch()`(§12.2、§16.4-三律①);`SwitchStormGuardTest` 与调用栈断言冻结 |
| 异步回程碰 Bukkit API(§14.1) | 世界模板复制/配置解析在虚拟线程,一切世界加载与玩家操作经 `Threads` 回主线程;2.4 的 `WorldLoader` 把"主线程装卸"收成一个注名接口,code review 只看一处 |
| 世界句柄泄漏(§14.19) | 释放点收敛在 `SessionServiceImpl`(B5 完成后 try/finally 释放旧句柄;dissolve finally 兜底);★17 以 100 次循环单测冻结 |
| 大厅特判反弹(§14.21) | fallback 是 `session.fallback-mode` 配置项;`EngineHasNoModeStringsTest` 禁词表覆盖全部本阶段 mode id(`lobby`/`minilobby`);★13 断言 lobby 与其他模式切换路径逐字相同(**★v1.3/2.5 迁移注 2026-10-25**:框架禁词表现仅为 `lobby` 一词——`hydrokarbs` 工程 `EngineHasNoModeStringsTest.java:33` `FORBIDDEN_MODE_IDS = {"lobby"}`(阶段2-施工报告实测);`minilobby` 属玩法线 id,随 2.5 迁 Shdf 侧、由该侧清单覆盖。本行「全部本阶段 mode id」按框架侧现口径读) |
| vote-pool/rotation 指向尚未注册的模式(阶段2 时具体玩法模式未注册,随玩法线引入) | 框架侧行为:倒计时到点若目标未注册,走 `MODE_NOT_FOUND` 拒绝、旧实例继续(§3.2-A1),不炸;2.5(迁 Shdf 侧)交付的 `lobby.yml` 默认池暂为 `minilobby@lobby_mini`(文件内注释标明 §4.4 终态;§4.4 属 `04-大厅模式.md`,迁 Shdf 侧) |
| 起服实测无法自动跑 100 次人工切换 | 100 次的机械保证由离线循环测试(`WorldRefCountTest`+`LobbyMinilobbyRoundTripTest`)承担;起服实测 ≥10 次人工循环核对时序与日志,DoD 分列这两项,不互相冒充 |
| 设计案未定位/未展开的少量类型(`MatchModeConfig`/`JoinResult`/`SessionEventBus`/`WorldHandle`/`SessionState`/`MapSelectionStrategy`) | 各子阶段文档按归属就近安置并逐处标注"§12.1 未列,建议位置";汇总见下方「待裁决问题」,由统筹裁决后回填 |

## 待裁决问题

> 编写中发现的与设计案疑似矛盾或空白;本阶段已按"最小自洽实现"施工并在对应子阶段文档中标注,以下提请统筹裁决后回填设计案。

1. **切换完成事件的命名**:§3.1 七事件表用 `ModeSwitchStarted`/`ModeSwitchCompleted`,而 §3.2-B5 与 §0.5.3 原提到 `SessionModeSwitched(from, to)`——**已裁决(★v4.1 勘误)**:统一为 `ModeSwitchStarted` / `ModeSwitchCompleted`(完成事件携带 from/to,03 §3.2-B5 注);本阶段按七事件表实现为 `ModeSwitchCompleted(from, to)`,与裁决一致。
2. **大厅 underflow 规则的触发事件**:§4.2(`04-大厅模式.md`,迁 Shdf 侧)规则监听 `MatchModeInstanceEvent.MemberLeft`,但 §12.2 引擎 Structural 族的 permits 里没有该类型(只有 `ParticipantLeft` 等六种)——本阶段按 `ParticipantLeft` 实现,请裁决设计侧口径。
3. **plugin.yml 的 `commands:` 段**:§12.2 骨架含 `commands:` 段,而版本基线要求命令走 Brigadier(`LifecycleEvents.COMMANDS`);同名声明并存会注册冲突(papermc-plugin-dev 技能反模式表)——本阶段已按"删除 `commands:` 段、全 Brigadier(含别名 `sm`)"施工,请裁决回填 §12.2。
4. **§12.1 包结构未列出的类型已就近安置**:`MatchModeConfig`(engine/mode/)、`JoinResult`/`SessionState`/`MapSelectionStrategy`(engine/session/)、`SessionEventBus`(engine/event/,随 `SessionEvent`)、`WorldHandle`/`ResetStrategy`(engine/port/,随 `WorldPort`)——请统筹确认或指定位置。
5. ~~`JoinResult` 形状未定义~~ **已裁决(v3.2)**:设计案 §3.2.1 已定义 `JoinResult`(Joined/Rejected)与 `JoinAdmission`/`JoinDecision` 端口——JoinPolicy 下放组件后,join 的入场裁决经端口查询;本阶段按 §3.2.1 原样实现。
6. **`Handoff.of(Key<T>, T)` 便捷工厂**:§4.2 判定器代码使用了该形态,§3.3 的 Handoff API 面未列出——已按最小补充实现。
7. **ConnectionTracker 宽限期满的事件语义**:§3.1 未细化"宽限到期移除"是否伴随事件——按「断线即发 `MemberLeft`+成员标记 DISCONNECTED 保留;宽限期满静默移除;重连发 `MemberReconnected`」实现。
8. **阶段2 交付的 `modes/lobby.yml` 取值**(2.5 迁玩法线后随 Shdf 侧 2.5 承接):`vote-pool`/`rotation` 暂指向 `minilobby@lobby_mini`(§4.4 终态的具体玩法模式在玩法线阶段5/6 才注册;§4.4 属 `04-大厅模式.md`,迁 Shdf 侧)——分阶段交付裁剪,文件注释已标明终态。
