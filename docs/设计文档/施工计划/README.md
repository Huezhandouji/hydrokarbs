# HydroKarbs MiniGame Framework 施工计划总览(框架线)

> HydroKarbs MiniGame Framework · v1.10(现行,2026-10-28 修正案 28 号:属性袋退场与 MapDefinition 三字段定案;v1.9 于 2026-10-28 修正案 27 号:切换与解散的世界清场,内核接缝 + L0 注入函数,WorldPort/公共面零改动;v1.8 于 2026-10-28 修正案 26 号:地图声明按路径直交 + MapDefinition 瘦身,方法面零变化;v1.7 于 2026-10-27 能力标签机制退场,25 号;v1.6 于 2026-10-27 显式注册收敛,24 号;v1.5 于 2026-10-27 REUSE_DIRTY 副本随会话解散销毁,23 号;v1.4 于 2026-10-27 API 面收敛与地图注册单通道,22 号;v1.3 于 2026-10-25 框架基础设施瘦身与配置中立化,21 号;v1.2 于 2026-10-23 地图注册通道;v1.1 于 2026-10-23 框架独立插件化拆分与 API 面扩充;v1.0 于 2026-10-22 自 SHDFMatch 设计案 v4.1 剥离玩法重构;玩法实现见 Shdf具体玩法设计案/)

> 本文件夹是框架设计案(v1.1)的**框架线施工序列化**:框架线共 8 个阶段、23 个子阶段,每个子阶段给出可执行的施工指导(步骤、文件、陷阱、验收、DoD)。
> **双线口径**:玩法实现子阶段(阶段3 全部、2.5、5.1/5.2、阶段6 全部、7.3)属**玩法线**,整体迁入 `../Shdf具体玩法设计案/施工计划/`(清单见其 `完成清单-玩法线.md`);本计划仅保留行级去向标注与框架侧承接点。
> **框架设计案是唯一权威**——施工计划只回答"按什么顺序做、以什么闸门放行、避哪些坑",不重复、不修改设计内容。所有结论标注出处(如「§3.2」= `../03-会话与模式切换.md` 第 3.2 节;「§16.2」= `../16-路线图与验收.md`)。
> ★v4.0(会话级组件层,2026-10-17):受影响子阶段、阶段 2 内插 **2.6** 的待办与交付顺序约束见本文 **§7**;变更全文见 `../17-v4变更指示书.md`(变更指示,历史存档,非设计章节)。
> ★v4.1(进程级服务层与 ServiceRegistry,2026-10-21):受影响子阶段、阶段 1 内插 **1.6** 的待办与交付顺序约束见本文 **§8**;变更全文见 `../18-v4.1修正案-进程级服务层与ServiceRegistry.md`(变更指示,历史存档,非设计章节)。
> ★v1.1(框架独立插件化拆分与 API 面扩充,2026-10-23):受影响子阶段、阶段 7 内插 **7.5**(G1)与新增**阶段8-独立插件化交付**(8.1,G2)的待办与交付顺序约束见本文 **§9**;变更全文见 `../19-v1.1修正案-框架独立插件化拆分与API面扩充.md`(变更指示,历史存档),决策记录见 `../docs/adr/0007-standalone-framework-plugin-split.md`。
> ★v1.2(地图注册通道,2026-10-23):受影响子阶段与交付顺序约束见本文 **§10**;变更全文见 `../20-v1.2修正案-地图注册通道.md`(变更指示,现行,非历史存档),决策记录见 `../docs/adr/0008-map-registration.md`;新增验收为 §16.2 的 **★54~★55**(首建于 7.5)。
> ★v1.3(框架基础设施瘦身与配置中立化,2026-10-25):受影响子阶段(4.3 撤销、5.3/5.4 迁宿主线)、阶段表与验收归属的处理见本文 **§11**;变更全文见 `../21-v1.3修正案-框架基础设施瘦身与配置中立化.md`(变更指示,现行,非历史存档),决策记录见 `../docs/adr/0009-framework-infrastructure-slimming.md`;**零新增验收编号**,★10~★12 归属改标「宿主线(框架侧 v1.3 废止)」。
> ★v1.4(API 面收敛与地图注册单通道,2026-10-27):①两参 `createSession(String, String)` 重载删除(`SessionRecipe` 重载为公共 API 建会话唯一形态,API_VERSION 3→4、冻结面 16→15 方法);②文件式地图装载整链退场(地图仅经 `registerMap` 注册)——受影响子阶段与交付顺序约束见本文 **§12**;变更全文见 `../22-v1.4修正案-API面收敛与地图注册单通道.md`(变更指示,历史存档,非现行),决策记录见 `../docs/adr/0010-api-surface-consolidation.md`;新增验收为 §16.2 的 **★56~★57**(首建于 7.1/7.5)。
> ★v1.5(REUSE_DIRTY 副本随会话解散销毁,2026-10-27):世界副本的**复用窗口 = 会话生命周期**——会话解散经 `WorldPort` 新增第三方法 `discardSession(SessionId)` 销毁该会话名下全部副本(三类覆盖:切换离开保留的目录/在用句柄/迟到 in-flight;★58)——受影响子阶段与交付顺序约束见本文 **§13**;变更全文见 `../23-v1.5修正案-REUSE_DIRTY副本随会话解散销毁.md`(变更指示,现行,非历史存档),决策记录见 `../docs/adr/0011-reuse-dirty-copy-dispose-on-dissolve.md`。
> ★v1.6(显式注册收敛,2026-10-27):模式与会话组件的注册从 ServicesManager 提供方通道(`ServiceRegisterEvent`/`ServiceUnregisterEvent` 动态收录/摘除)改为 `HydrokarbsApi` **显式注册四法**(`registerMode(Plugin, MatchModeProvider)` 响亮闸 / `unregisterMode(Plugin, modeId)` 只摘自己的 id + 摘除回退编排:fallback-mode force 切换或 FALLBACK_DISSOLVE / `registerSessionComponents(Plugin, specs)` 响亮抛干跑校验 / `unregisterSessionComponents(Plugin)` 幂等;API_VERSION 4→5,冻结面 15→19 方法)——`SessionComponentProvider` SPI 与 `ProviderServiceListener` 删除,`MatchModeRegistry#discover()` 只剩 ServiceLoader 本 jar 半边,注册时序反转 = 第三方必须 `depend` 框架、`load(HydrokarbsApi)` 后显式登记(「先启补收录」退役),disable 兜底四族(ProviderUnloadListener;★59)——受影响子阶段与交付顺序约束见本文 **§14**;变更全文见 `../24-v1.6修正案-API面显式注册模式与会话组件.md`(变更指示,现行,非历史存档),决策记录见 `../docs/adr/0012-explicit-api-registration.md`。
> ★v1.7(能力标签机制全链退场,2026-10-27;用户裁决:字符串契约不严谨):字符串能力标签平行层整体退役——`CapabilityRegistry`、切换 A2 能力校验、`HydrokarbsApi#registerCapability`/`capabilities` 两法(API_VERSION 5→6,冻结面 19→17 方法)、`MatchModeMeta.requiredCapabilities`(Builder 声明语言变更)、`MatchModeInfo` 快照分量、`SessionComponent.Specification#capabilities`、`ServiceSpec.capabilities` 与 seal/shutdown 两端注册、`ProviderUnloadListener` 能力块(兜底四族收敛三族)、`capabilityClaims` 簿记;**外部能力新形态 = 会话组件桥接**:提供方(或宿主自持防腐层)经 `registerSessionComponents`/`SessionRecipe` 贡献类型化桥接组件,消费方组件 `requires`/`requiresOptional` 声明依赖,装配期响亮、运行期 quarantine;软依赖由 `requiresOptional` 独自承载(机制已在框架内,非新增;★21/★60);**语义位移**:能力校验从「每次切换重查(进程级)」改为「创建时绑定 + 运行期降级/quarantine」——provider 摘除只影响后续会话,存量会话由桥接组件自身降级承接(D14 既有口径);冻结测试合法更新(FROZEN 17 行、BASELINE 重录、API_VERSION=6);**★32 废止、新增 ★60**(§4)——受影响子阶段与交付顺序约束见本文 **§15**;变更全文见 `../25 号修正案`(v1.7 能力标签机制退场;正文落盘前以本横幅与 §15 为准)。
> ★v1.8(地图声明按路径直交 + MapDefinition 瘦身,2026-10-28;修正案 26 号,**方法面零变化**):「模板寄居框架数据目录 `maps/templates/`」约定退场——`MapDefinition` 自带 `worldDirectoryPath`(构造期规范化为绝对路径入库,缺目录名 id 无从派生响亮抛),`MapRegistry` 删 `templatesRoot` 构造依赖、注册校验直验声明路径存在且为目录(缺失/非目录响亮抛带完整绝对路径,14.39 读作「注册前路径已就位」),`HydrokarbsAssembly` 撤模板根派生;`MapDefinition` 瘦身为两分量(id/displayName/tags/bounds/spawns/regions/interactPoints/metadata/resetStrategy 全删;`map/` 包 RegionSet/NamedRegion/SpawnPoint/InteractPoint 出册,Bounds 从未落码);tags 与副本处置策略走 `MetaAttributes` 约定键 `map.tags`/`map.reset-strategy`(缺省 REUSE_DIRTY 语义不变;三口径——专用写口/`set` 仅组件断言不放开、注册期冻结与注册方脱钩、声明级生命周期);id = 声明目录名路径派生,目录查重/副本命名/`SessionRecipe` 钉图键统一;`api/view/MapInfo` 收缩为 `(id, tags, resetStrategy)`;`AttributeBag` 增 `declaration`/`copyOf`;`WorldPortImpl`/`PlaceholderWorldPort` 以声明路径为基准(行为语义零改动);API_VERSION 6→7、FROZEN 17 行不动、BASELINE 随声明形状合法重录;**新增 ★61~★64**(§4)——受影响子阶段与交付顺序约束见本文 **§16**;变更全文见 `../26 号修正案`(正文落盘前以本横幅与 §16 为准)。
> ★v1.9(切换与解散的世界清场,2026-10-28;修正案 27 号,**内核接缝 + L0 注入函数,非破坏性**):消除「世界内有玩家 → `Bukkit.unloadWorld` 返回 false → SEVERE + 保留目录」路径(23 号销毁闭环的伴生收口)——**切换** = 同世界复用(判据 = `org.bukkit.World` 引用相等,首切空值分支跳过)新步 B3.5 清场先行于 B4 组件摆放(同图重开「原地起步 → 出生点起步」为冻结语义变更,依赖跨局位置保留的组件自读 Handoff 自行传送)、异图切换 B5 finally 殿后(`previousWorld` 非 null 守卫内、完成事件之后、release 之前);**解散** = `dissolveNow` finally 内「先名册后世界」(摘句柄后、`discardSession` 前);**遗留人员 = `World#getPlayers()` 世界内全员,不以会话名册为界**;平台语义收敛 `SessionServiceImpl.Builder` 两 L0 注入函数(`worldClearer`/`hubWorldClearer`,默认 no-op,落点 `HydrokarbsAssembly`,`hubSpawn()` 与 `hubRelocator` 同源单一来源),best-effort WARN 不阻塞;`WorldPort`/`HydrokarbsApi` 17 法/`API_VERSION=7` 零改动、★19/★44 零重录;**新增 ★65**(§4,首建于 2.3,持续验证 = `ModeSwitchAtomicityTest` + `FakeWorldPort` sticky/时序录增补)——受影响子阶段与交付顺序约束见本文 **§17**;变更全文见 `../27 号修正案`(现行,非历史存档),决策记录见 `../docs/adr/0015-world-clear-on-switch-and-dissolve.md`。

## 0. 使用方法

| 读者 | 用法 |
|---|---|
| 施工者(开工某框架线子阶段) | 读 `阶段N/README.md` → 读目标子阶段文档 → 通读 `../14-陷阱清单.md` 中该文档引用的条目 → 动工 |
| 施工者(每日子阶段收尾) | 逐条勾掉子阶段文档末尾的 DoD 清单;DoD 未全绿不得进入下一子阶段 |
| 评审者 | 以「§16.2 验收 → 阶段映射表」(本文 §4)核对各阶段出口闸门是否真实通过 |
| 玩法线施工者(宿主扩展接入/大厅/各玩法模式/迁移) | 见 `../Shdf具体玩法设计案/施工计划/`(本计划仅保留行级去向标注与框架侧承接点) |

## 1. 阶段总览(双线:8 阶段(阶段8 条件触发) ↔ 路线图 P0~P8.5,§16.1)

> **双线拆分**(★v1.3 更新):框架线 = 阶段1 全部、阶段2(除 2.5)、阶段4(除 4.3)、阶段7(除 7.3)、阶段8 全部,共 **20 个子阶段**(v1.2 时为 23——5.3/5.4 迁宿主线、4.3 撤销,§11);玩法线 = 阶段3 全部、2.5、5.1/5.2、阶段6 全部、7.3,共 13 个子阶段,迁 `../Shdf具体玩法设计案/施工计划/`(★v1.3 起 5.3/5.4 的持久化实现亦归宿主线,由 Shdf 侧计划承接新增子阶段)。下表「线」列标注归属;玩法线行仅留去向与框架侧承接点。

> **★用户排期决议(2026-10-25,排期叠加、非依赖变更)**:施工执行顺序调整为——阶段7 的 **7.1 → 7.2 → 7.5(G1)提前至阶段4 之前**执行、**7.4(全量验收与发布)后置至阶段4 完成后**执行;阶段8(G2)仍按 A13 触发条件(首个真实跨 jar 消费者)不设时间表。依据:阶段7 的主体(7.1 冻结面 / 7.2 注册 / 7.5 彩排)只依赖阶段2(已验收 2026-10-25,见 `完成清单.md`);组件库(阶段4)经模式 Builder 消费、不进 API 面,后补为「只增」关系。**§2 依赖图与各子阶段文档的「前置与依赖」小节保留为设计依赖记录,本决议不改变设计依赖关系**。

| 阶段 | 线 | 内容 | 对应路线图 | 出口闸门(不通过不放行) |
|---|---|---|---|---|
| **1** `阶段1-引擎内核与架构地基/` | 框架线 | Gradle 工程与构建链路;聚合根 `MatchMode`/组件基类 `MatchModeComponent`/`MatchModeInstanceServices`;实例容器与事件总线与 `EngineTicker`;架构冻结测试;三迁组件(Phase/EndGate/RuleBook);进程级服务装配(`ServiceRegistry`) | P0 + P0.5 + P0.6 | 假组件离线跑完 awake→start→update→stop 全生命周期;`LayeringTest`/`AggregatePurityTest`/`EnginePurityTest` 全绿;三组件离线单测通过;1.6 DoD(★34~★40)全绿 |
| **2** `阶段2-会话层与模式切换/`(2.5 迁玩法线) | 框架线 | 会话基础件;模式注册与地图/配置装载;`switchMode` 两阶段切换与三级回退;join 入场裁决(`JoinAdmission` 端口,★v3.2 §3.2.1);`WorldPort` 世界生命周期;大厅模式落地 →(2.5 迁 Shdf 侧;**框架闸门等价物留框架侧**:1.4 禁词表含 `lobby` + 2.3 同一入口切换测试) | P1 + P1.5(2.6) | lobby ↔ 空转模式 原子往返 100 次:成员零丢失、世界零泄漏、引擎零改动接入两个测试模式(框架侧先以两个测试模式建立;2.5 大厅于 Shdf 侧落地后联测) |
| **3** | 玩法线(SHDF 扩展线,整阶段迁 `../Shdf具体玩法设计案/施工计划/`) | 宿主角色系统桥接与包装组件(实现见 Shdf 侧施工计划) | P2 | 随 Shdf 侧;框架承接:★3/★23 桥接泛化条款于 1.4 预留,★32 标签机制于 2.6 建立 |
| **4** `阶段4-共享组件齐活/` | 框架线 | 队伍/计分族;目标/区域/交互族;投票/统计族;生命周期辅助组件(含入场控制)(★v1.3:原「表现层」半段删除——4.3 撤销,DisplayComponent 迁 Shdf 侧 10,§11) | P3 | §6.1 中住 `component/**` 的 **20 行**(★v1.3:21→20,#12 迁出)组件单测全覆盖 awake/start/update/stop 幂等性与登记资源回收;#20/#21/#22(住 `mode/**` 的胜负变体/实体/仇恨类组件)随玩法线迁 Shdf 侧,挂账至其 5.1 / 6.x 的 DoD |
| **5**(玩法半段:5.1/5.2) | 玩法线,迁 `../Shdf具体玩法设计案/施工计划/` | 首模式工厂;端到端验收(实现见 Shdf 侧施工计划) | P4 | 随 Shdf 侧(从会话进入任意已注册模式→计分→回退/切换);框架承接:★18 交接复验口径 |
| **5**(框架半段:5.3/5.4)`阶段5-首模式与持久化/` | 框架线(★v1.3:**整体迁宿主线**——持久化出框架,5.3/5.4 撤销框架侧交付,由宿主自建 DB 栈承接;文档存档加迁出注,§11) | (历史)数据库基建(HikariCP/虚拟线程/迁移);仓储与 write-behind 落库 | P5(★v1.3:归宿主线) | 落库可查、停服不丢、库不可用降级内存、lobby 零落库行(★v1.3:随 ★10~★12 归宿主线执行) |
| **6** | 玩法线,整阶段迁 `../Shdf具体玩法设计案/施工计划/` | 各玩法模式的实现与扩展(实现见 Shdf 侧施工计划) | P6 + P7 | 随 Shdf 侧;**框架承接:『`engine/**` 与既有 `component/**` 的 PR diff 为空』为框架验收(★5),由每次玩法模式接入执行** |
| **7** `阶段7-API冻结与替换交付/`(7.3 迁玩法线) | 框架线 | `HydrokarbsApi` 冻结面(★v1.3:查战绩面悬置,7.1 落地不含 DB 依赖项);第三方注册扩展;API 面扩充与拆分彩排(★v1.1 新增子阶段 7.5:G1,含 ★v1.2 地图注册交付物);全量验收与发布;旧插件迁移替换 →(7.3,SHDF 迁移线,迁 Shdf 侧)(**★用户排期决议 2026-10-25:执行顺序 7.1 → 7.2 → 7.5(G1)先行、提前至阶段4 之前;7.4 后置至阶段4 完成后——排期决议不改变依赖,见本节首注**) | P7.5 + P7.6 + P8 | 假插件经 `MatchModeProvider` 注册并被大厅列出;**48 条**验收(§16.2 至本阶段止:40 + ★41~★46/★51~★55,再减 ★10~★12(★v1.3 归宿主线),归属口径见 §4/§11)全绿;新旧并行实测对比 →(7.3,随 Shdf 侧) |
| **8** `阶段8-独立插件化交付/`(★v1.1 新增) | 框架线 | 物理拆分与双形态交付(G2;**触发条件:7.5 全绿且首个真实跨 jar 消费者出现,A13**) | P8.5 | 形态 B 下 ★47~★50 全绿;★50 双形态等价(同一验收集 ★1~★55,随验收追加同步扩,在形态 A/B 下结论一致) |

## 2. 阶段依赖与并行性

```
阶段1 ──▶ 阶段2 ──▶ 阶段3 ──▶ 阶段5 ──▶ 阶段6 ──▶ 阶段7 ──▶ 阶段8(v1.1 新增,条件触发)
              │        (3 与 4 可并行:角色组件与通用组件互不依赖)
              └──────▶ 阶段4 ──┘
                       (阶段5 的 5.1/5.2 依赖阶段4 的组件;
                        5.3/5.4 持久化只依赖阶段1 的端口,可与 3/4 并行)
```

- **硬串行**:1 → 2(引擎契约先于会话装配);5.1/5.2 依赖 4(模式要挂通用组件);6、7 依赖 5;8 依赖 7(且受 A13 触发条件约束——无跨 jar 消费者则不启动,§9)。
- **可并行**:阶段3(角色线)与阶段4(组件线)互不依赖;(★v1.3:原「阶段5 持久化子阶段(5.3/5.4)只消费阶段1 定义的 `PersistencePort` 接口,可提前并行」废止——`PersistencePort` 已删、5.3/5.4 迁宿主线,§11。)上图保留为 v1.2 前交付顺序记录。
- 每个子阶段文档的「前置与依赖」小节给出精确到子阶段的依赖声明。

> **双线注**:阶段3、5.1/5.2、阶段6、7.3 的施工已迁 Shdf 侧(§1);上图保留为原交付顺序记录,框架侧承接点见 §1 与 §4。

> **★用户排期决议(2026-10-25,排期叠加)**:执行排期 = 1 → 2 → **7.1 → 7.2 → 7.5(G1)** → 4(阶段4,组件线)→ 玩法线(5.1/5.2、阶段6,Shdf 侧)→ **7.4** → 8(A13 条件触发)——阶段7 的 7.1/7.2/7.5 提前至阶段4 之前、7.4 后置至阶段4 完成后。**上图与上列「硬串行/可并行」为设计依赖记录,本条是排期决议、非依赖变更**(依据:7 的主体只依赖阶段2——已绿;阶段4 组件经模式 Builder 消费、不进 API 面,后补为「只增」关系);阶段8 仍按 A13 触发条件不设时间表(§9.3)。

## 3. 贯穿性纪律(所有阶段共同遵守)

1. **术语 v1.1**:以根目录 `CONTEXT.md` 为准;禁用其「历史术语」表中的旧名(如裸 `Mode` 接口、`RoleLoadoutComponent`、`HandoffKeys`、`enum Capability`、引擎侧结局枚举、旧包根 `com.shdf.match`——D13 更名,宿主工程另立 `com.shdf.*`)。
2. **版本基线**:Java 21 / PaperMC 1.21.11 / Gradle Kotlin DSL / `paper-api 1.21.11-R0.1-SNAPSHOT`;宿主扩展依赖(角色系统插件等)属 **SHDF 扩展线**,随阶段3(Shdf 侧)引入,框架基线不含宿主插件坐标(见 Shdf具体玩法设计案/)。
3. **每子阶段必含**:新增/修改文件清单、离线可跑的测试、DoD 清单。**不起服不编译不算完成**(§papermc-plugin-dev 技能流程:build → runServer 实测闭环)。
4. **测试即架构**:§16.2 的架构验收测试在本阶段建立后**永久生效**,任何后续阶段不得为通过功能而放宽断言;发现测试本身写错,修测试须在 PR 中单独说明理由。
5. **陷阱前置**:动工前通读 `../14-陷阱清单.md` 中该子阶段引用的条目;39 条陷阱按"会炸得多惨"排序,高编号(v3 组件纯度类、v4 跨级纪律类)在阶段 1~4 尤其致命。
6. **引擎内容盲**:`engine/**` 不出现任何 mode id 字面量(含 `lobby`)与 `Phase/Rule/Role/Team/Score/Objective` 等内容词汇;组件不出现任何具体玩法内容字样(具体玩法禁词随玩法线清单)——`EnginePurityTest` 与 `EngineHasNoModeStringsTest` 守门(§16.2-★24/4)。

## 4. §16.2 验收(65 条,含 ★34~★40、★41~★55、★56~★58、★59、★60、★61~★64 与 ★65;★v1.3:★10~★12 归属迁宿主线;★v1.4:+★56~★57;★v1.5:+★58;★v1.6:+★59;★v1.7:+★60,★32 废止;★v1.8:+★61~★64,方法面零变化;★v1.9:+★65,世界清场)→ 阶段映射表

> ★ 编号与归属口径同 `../16-路线图与验收.md` §16.2(框架/玩法两线**同一套编号**,不拆分不重排);「首次建立于/持续验证于」列中标注「迁 Shdf 侧」者为玩法线执行点,行内注给出框架侧承接点;闸门等价物留框架侧的条目(★13)由框架测试先行建立。计数口径:60 条 = 框架 53 + SHDF 扩展线 3(★3/★21/★23)+ 两线联合 1(★32,**★v1.7 废止行不计有效命题**,25 号 §5)+ 宿主线 3(★10/★11/★12,v1.3 迁出,21 §11);★41~★53 为 v1.1 新增、★54~★55 为 v1.2 新增、★56~★57 为 v1.4 新增(22 号)、★58 为 v1.5 新增(23 号)、★59 为 v1.6 新增(24 号)、★60 为 v1.7 新增(25 号),全部归属框架线(★41~★46/★51~★57 首建于 7.5,★47~★50 首建于 8.1,★58 首建于 2.4,★59 首建于 7.2/7.5,★60 首建于 7.5);**★v1.7(25 号):★32 就地废止**(能力标签扩展随标签机制全链退场;编号不删不重排,废止行不计有效命题,命题由 ★60 承接);**★v1.8(26 号):+★61~★64 归框架线**(地图声明按路径直交/id 路径派生统一/MapDefinition 瘦身与约定键/副本按声明路径复制与命名;首建于 7.5/2.4,权威文本 = 26 号 §5,落盘后复核);**★v1.9(27 号):+★65 归框架线**(世界清场;首建于 2.3,权威文本 = 27 号 §5);v1.8 时点口径为「64 条」,v1.7 时点口径为「60 条」,v1.6 时点口径为「59 条」,v1.5 时点口径为「58 条」,v1.4 时点口径为「57 条」,v1.3 时点口径为「55 条」,v1.2 前口径为「框架 51」(含 ★10~★12,当时归属框架)——**现行计数口径:65 条 = 框架 58 + SHDF 扩展线 3 + 联合 1(★32 废止)+ 宿主线 3**。

| 验收 | 归属 | 命题 | 首次建立于 | 持续验证于 |
|---|---|---|---|---|
| 1 | 框架 | engine 不认识模式 | 1.4 | 全阶段 |
| 2 | 框架 | 模式互不依赖 | 1.4 | 全阶段 |
| 3 | SHDF 扩展线 | 只有宿主桥接引用宿主插件(泛化条款:宿主桥接仅被其包装组件 import) | 1.4(规则预留,框架基线空转) | Shdf 侧阶段3 起实际生效(迁 Shdf 侧) |
| 4 | 框架 | 引擎/组件无 mode id 字面量 | 1.4 | 全阶段 |
| 5 | 框架(玩法线接入时执行) | 新增模式不改 engine | 玩法线模式接入 diff 检查(原 6.4,迁 Shdf 侧;规则于 1.4 预留) | 玩法线每次接入(Shdf 侧)与 7.4 |
| 6 | 框架 | close 幂等 | 1.3 | 2.3(切换路径复测) |
| 7 | 框架 | 清场逆序且异常不中断 | 1.3 | 2.3 |
| 8 | 框架 | 主线程边界单点回程 | 1.1(`Threads`) | 全阶段 |
| 9 | 框架 | 规则可离线测 | 1.5 | 5.1/6.x(玩法规则复验,迁 Shdf 侧;框架侧假规则单测常驻) |
| 10 | **宿主线(框架侧 v1.3 废止)** | 库不可用对局照跑 | 5.3(★v1.3:迁宿主线,由宿主自建持久化承接) | 7.4(★v1.3:归属迁移注) |
| 11 | **宿主线(框架侧 v1.3 废止)** | 战绩 30s 窗口不丢 | 5.4(★v1.3:迁宿主线) | 7.4(★v1.3:归属迁移注) |
| 12 | **宿主线(框架侧 v1.3 废止)** | 表结构可演进 | 5.3(迁移演练;★v1.3:迁宿主线) | — |
| 13 | 框架 | 大厅只是模式 | 2.5(迁 Shdf 侧;**闸门等价物留框架侧**:1.4 禁词表含 `lobby` + 2.3 同一入口切换测试先行建立) | 7.2(第三方目录) |
| 14 | 框架(玩法线复测) | 切换原子性 | 2.3 | 6.x(带玩法复测,迁 Shdf 侧;框架断言不变) |
| 15 | 框架 | 切换风暴被挡 | 2.3 | — |
| 16 | 框架 | 名册零搬运 | 2.1/2.3 | — |
| 17 | 框架 | 世界零泄漏 | 2.4 | 7.4 |
| 18 | 框架(战报交接复验属玩法线) | 交接类型安全 | 2.3/2.5(2.5 迁 Shdf 侧;框架侧 2.3 建立契约测试) | 5.2(战报交接,迁 Shdf 侧;框架侧 `HandoffContractTest` 常驻) |
| 19 | 框架 | 公共 API 冻结 | 7.1 | — |
| 20 | 框架 | 跨插件模式可注册 | 7.2 | — |
| 21 | SHDF 扩展线(机制框架) | 软依赖降级 | 3.3(迁 Shdf 侧;框架承接:标签机制条款于 2.6) | 7.4 |
| 22 | 框架 | 聚合根零行为 | 1.4 | 全阶段 |
| 23 | SHDF 扩展线 | 宿主扩展接入单点 | 3.4/3.5(迁 Shdf 侧;泛化条款于 1.4 预留) | 全阶段(ArchUnit) |
| 24 | 框架 | 引擎零状态机+零内容词汇 | 1.4 | 全阶段 |
| 25 | 框架 | svc 成员封闭 | 1.4 | 全阶段 |
| 26 | 框架 | 状态主人唯一 | 1.4(测试)/ 4.x(各组件执行) | 全阶段 |
| 27 | 框架 | 会话级组件每会话一份(★v4) | 2.6 | — |
| 28 | 框架 | 会话级生命周期与回收(★v4) | 2.6 | — |
| 29 | 框架 | 跨级只读访问(★v4) | 2.6 | — |
| 30 | 框架 | 会话组件故障隔离不解散(★v4) | 2.6 | — |
| 31 | 框架(玩法线复测) | 空场策略可插拔(★v4) | 2.6 | 6.x(带玩法复测,迁 Shdf 侧) |
| 32 | 框架机制+扩展线接线 | 能力标签扩展(★v4)(**★v1.7 废止,25 号 §5:本条随能力标签机制全链退场废止**——外部能力 = 会话组件桥接,命题由 ★60 承接;编号不删不重排,本行保留存档、不计有效命题) | 2.6(会话侧)/ 3.3(桥接侧,迁 Shdf 侧) | 全阶段(★v1.7 起:检验随机制退役废止,25 号) |
| 33 | 框架 | 会话组件不参与切换装配(★v4) | 2.6 | — |
| 34 | 框架 | 注册查重响亮失败(★v4.1) | 1.6 | 全阶段 |
| 35 | 框架 | 装配期依赖校验(★v4.1) | 1.6 | 全阶段 |
| 36 | 框架 | 拓扑序 + 声明序稳定(★v4.1) | 1.6 | 全阶段 |
| 37 | 框架 | 失败即回滚(★v4.1) | 1.6 | 全阶段 |
| 38 | 框架 | 关停逆序幂等(★v4.1) | 1.6 | 全阶段 |
| 39 | 框架 | ServiceEnv 语义(★v4.1) | 1.6 | 全阶段 |
| 40 | 框架 | 服务层分层冻结(★v4.1;★v1.1 增款:服务不得 import `api/**`,14.37) | 1.6(增款随 7.5 落地) | 全阶段 |
| 41 | 框架 | 跨 jar 模式注册(★v1.1;形态 A 以测试插件模拟) | 7.5 | 8.1(真双 jar) |
| 42 | 框架 | 成员操作端到端(joinSession/leaveSession hub→arena,与 /sm 同权)(★v1.1) | 7.5 | 全阶段 |
| 43 | 框架 | 事件订阅(快照不可变/句柄回收/订阅者隔离)(★v1.1) | 7.5 | 全阶段 |
| 44 | 框架 | API 编译面封闭(ApiSurfaceTest 白名单与签名快照)(★v1.1) | 7.5 | 全阶段 |
| 45 | 框架 | 二进制兼容(样例插件仅依赖白名单编译,框架次要版本升级零改动)(★v1.1) | 7.5 | 每次框架发版 |
| 46 | 框架 | 版本协商(低版本样例拒载且框架照常)(★v1.1) | 7.5 | — |
| 47 | 框架 | 卸载顺序(模式全摘/会话回退/标签摘除/无残留句柄)(★v1.1) | 8.1 | 全阶段 |
| 48 | 框架 | 第三方组件隔离跨 jar 复验 + 预算告警(★v1.1) | 8.1 | 全阶段 |
| 49 | 框架 | 内部面零泄漏(白名单反面不出现在可达公开签名)(★v1.1) | 8.1 | 全阶段 |
| 50 | 框架 | 双形态等价(★1~★55 随验收追加同步扩,在形态 A/B 下结论一致)(★v1.1) | 8.1 | 每次发版 |
| 51 | 框架 | 会话组件贡献·全局(provider 注册/坏拒收/disable 摘除)(★v1.1) | 7.5 | 全阶段 |
| 52 | 框架 | 会话组件贡献·单次(recipe.extra 进表/随 dissolve 灭/响亮拒)(★v1.1) | 7.5 | — |
| 53 | 框架 | 贡献组件隔离(仅自身 quarantine、会话不解散;预算告警)(★v1.1) | 7.5 | 全阶段 |
| 54 | 框架 | 地图注册端到端(注册→列出→可开局→卸载摘除/正用拒绝)(★v1.2) | 7.5 | 全阶段 |
| 55 | 框架 | 地图坏声明响亮拒绝(模板缺失/重复 id 点名;不经 reload)(★v1.2;**★v1.8 点注(26 号,权威文本 = 26 号 §5)**:「模板缺失」「模板目录缺失」为模板根时点口径——读作声明自带 `worldDirectoryPath` 缺失或非目录,响亮抛带完整绝对路径;重复 id = 路径派生 id 撞名,同一路径再注册必撞、同名异父同撞) | 7.5 | 全阶段 |
| 56 | 框架 | 建会话公共面唯一形态(★v1.4):两参 `createSession(String, String)` 不复存在,`createSession(SessionRecipe)` 重载为唯一形态;API_VERSION 3→4、冻结面 16→15 方法 | 7.1(★v1.4 收敛冻结面) | 全阶段复验(`HydrokarbsApiSurfaceTest`/`ApiSurfaceTest`) |
| 57 | 框架 | 地图仅注册通道(★v1.4):配置装载零地图条目(`YamlCodec.readMap`/`MapDefinition.from`/`MapRegistry.replace` 与文件半边、无参构造退场),地图仅经 `registerMap` 进目录,目录顺序 = 注册序;注册期校验与正用摘除被拒守卫(20-D3/D4)不变 | 7.5(★v1.4 收敛) | 全阶段复验(`MapRegistryTest`/`MapRegistrationTest`) |
| 58 | 框架 | 副本复用窗口 = 会话生命周期(★v1.5):REUSE_DIRTY 副本目录随其所属会话解散而销毁(`WorldPort#discardSession` 三类覆盖:切换离开保留的目录/在用句柄/迟到 in-flight);切换离开时目录保留(复用语义不变)、TEMPLATE_RESTORE 归零删副本原样;删除仍唯一走 `deleteCopyDir` 入口 + 路径形态断言;幂等 | 2.4(★v1.5 收敛) | 全阶段复验(`WorldRefCountTest` ⑧ 断言族) |
| 59 | 框架 | 显式注册收敛(★v1.6):模式与会话组件改经 `HydrokarbsApi` 显式注册四法——`registerMode(Plugin, MatchModeProvider)`(注册闸 build→verifyDependencies→id 查重全响亮,返回 `MatchModeInfo`)、`unregisterMode(Plugin, modeId)`(只摘自己的 id,越权响亮拒/不存在幂等 no-op;摘除回退编排:fallback-mode force 切换或 FALLBACK_DISSOLVE)、`registerSessionComponents(Plugin, specs)`(干跑校验响亮上抛、幂等重收录)、`unregisterSessionComponents(Plugin)`(幂等);ServicesManager 提供方通道退场(`SessionComponentProvider` SPI 与 `ProviderServiceListener` 删除,`MatchModeRegistry#discover()` 只剩 ServiceLoader 本 jar 半边),注册时序反转 = depend 框架 + `load(HydrokarbsApi)` 后显式登记;disable 兜底四族(ProviderUnloadListener);API_VERSION 4→5、冻结面 15→19 方法 | 7.5(★v1.6 收敛) | 全阶段复验(`HydrokarbsApiSurfaceTest`/`ApiSurfaceTest` 快照随 v1.6 合法更新;`ThirdPartyModeRegistrationTest`/`SessionComponentContributionTest` 显式通道复验) |
| 60 | 框架 | 能力标签机制全链退场(★v1.7):字符串能力标签平行层整体删除(`HydrokarbsApi` 能力两法——API_VERSION 5→6、冻结面 19→17;`CapabilityRegistry`;切换 A2 校验;`MatchModeMeta.requiredCapabilities`;`MatchModeInfo` 快照分量;`SessionComponent.Specification#capabilities`;`ServiceSpec.capabilities` 与 seal/shutdown 两端注册;`ProviderUnloadListener` 能力块——兜底四族收敛三族;`capabilityClaims`)——外部能力 = **会话组件桥接**:提供方经 `registerSessionComponents`/`SessionRecipe` 贡献类型化桥接会话组件,消费方组件 `requires`/`requiresOptional` 类型化声明,装配期响亮、运行期 quarantine(语义位移 = 创建时绑定 + 运行期降级,provider 摘除只影响后续会话;软依赖 = `requiresOptional`,★21 承载);★32 同批废止(§16.2) | 7.5(★v1.7 收敛;`HydrokarbsApiSurfaceTest` FROZEN 17 行、`ApiSurfaceTest` BASELINE 重录、API_VERSION=6;`CapabilitySoftDependTest` 标签断言族随机制退役) | 全阶段复验(桥接组件经 `SessionComponentContributionTest` requires/requiresOptional 断言族复验) |
| 61 | 框架 | 地图声明按路径直交(★v1.8):「模板寄居 `maps/templates/`」约定退场——`MapDefinition` 自带 `worldDirectoryPath`(构造期规范化绝对路径,缺目录名响亮抛);注册期校验直验声明路径存在且为目录(缺失/非目录响亮抛带完整绝对路径);`MapRegistry` 无 `templatesRoot`、`HydrokarbsAssembly` 撤模板根派生;`WorldPortImpl`/`PlaceholderWorldPort` 以声明路径为基准(行为语义零改动) | 7.5(★v1.8 增量;`MapRegistryTest` 路径直验/规范化/响亮抛断言;`ApiSurfaceTest` BASELINE 随声明形状重录) | 全阶段复验 |
| 62 | 框架 | 地图 id 路径派生统一(★v1.8):id = 声明目录名——目录查重/`WorldPort` 副本命名与 deleteCopyDir 断言/`SessionRecipe` 钉图键三处同一来源;同一路径再注册必撞查重闸,同名异父目录同撞 | 7.5(★v1.8 增量;`MapRegistrationTest` 钉图/查重端到端;`MapRegistryTest` 同名异父断言) | 全阶段复验 |
| 63 | 框架 | MapDefinition 瘦身 + 声明词料约定键(★v1.8):两分量 `(worldDirectoryPath, metaAttributes)`,原九分量全删;`map/` 包 Bounds 族出册;tags/处置策略 = `map.tags`/`map.reset-strategy`(`MetaAttributes` 门面三口径——专用写口/`set` 断言不放开、注册期冻结脱钩、声明级生命周期;缺省 REUSE_DIRTY 不变);`MapInfo` 收缩 `(id, tags, resetStrategy)` | 7.5(★v1.8 增量;`MapRegistryTest` 冻结脱钩/缺省语义;`ApiSurfaceTest` BASELINE 快照合法重录) | 全阶段复验 |
| 64 | 框架 | 副本按声明路径复制与命名(★v1.8):`WorldPortImpl` 复制源恒为声明自带 `worldDirectoryPath`(缺失 → future 异常完成 + 清 in-flight、计数未入表,不降级);`PlaceholderWorldPort` 世界查找基准 = 声明路径派生 id;副本目录名 = `mapId-<会话短码>`(`map.id() + "-" + 会话短码`,新基准);`deleteCopyDir` 唯一删除口的路径形态断言按新基准(仅限副本根下 `<mapId>-<短码>` 形态,绝不递归删任意路径);`WorldHandle#reset` 值 = 约定键缺省语义(REUSE_DIRTY);**复用窗口 = 会话生命周期(★58)与 TEMPLATE_RESTORE 行为原样** | 2.4(★v1.8 增量;`WorldRefCountTest` ⑧ 组断言族按 ★v1.8 新基准——复制源/命名/删除断言;`SwitchHarness`;离线零 Bukkit)(权威文本 = 26 号 §5) | 全阶段复验 |
| 65 | 框架 | 世界清场(★v1.9):切换地图时旧世界内全部遗留人员(`World#getPlayers()`,不以会话名册为界)传往对应地图出生点——同世界复用(判据 = `org.bukkit.World` 引用相等、首切空值分支)走 B3.5 先行于 B4 组件摆放(恰一次、from == to、finally 门闩不重复)、异图走 B5 finally 殿后(完成事件之后 release 之前、from/to 互指代理世界);解散时传往主世界出生点(与 `hubRelocator` 同源)——先名册后世界、恰一次、先于 `discardSession`;heldWorld 为 null 跳过无害;清场注入抛异常 → 切换仍成功、release 照发、WARN;`WorldPort` 接口与公共面零改动 | 2.3(★v1.9 清场断言族:sticky 同句柄 + seamLog 时序录;`ModeSwitchAtomicityTest` 五用例)(权威文本 = 27 号 §5) | 全阶段复验(`ModeSwitchAtomicityTest` ★65 断言族;平台正确性归 runServer 实测,同 ★17 分工) |

## 5. 子阶段文档模板(各阶段统一)

每个子阶段文件(`<N.M>-<标题>.md`)固定七段:**目标 / 前置与依赖 / 施工步骤(有序,含文件路径与类名)/ 关键实现要点 / 陷阱与纪律(引 §14 条目)/ 测试与验收(引 §16.2 编号)/ 完成定义(DoD 复选清单)**。每份 150~300 行,信息密度优先。各阶段 `README.md` 另含:子阶段清单表、与其他阶段的接口(上游输入/下游承诺)、风险。

## 6. 参照物与外部事实来源

| 来源 | 用途 |
|---|---|
| `设计案v2/` 根设计文档(v1.2,现行;v1.0 于 2026-10-22 自 SHDFMatch v4.1 剥离玩法重构) | 框架线唯一权威设计(17/18/19 为变更指示历史存档、20 为现行变更指示,均非设计章节;玩法线设计见 `../Shdf具体玩法设计案/`) |
| `../CONTEXT.md` | 术语表(v4.1 沿用) |
| `docs/adr/` | 重大决策记录(大厅即模式/聚合根零行为;历史存档) |
| 宿主角色系统插件源码 | 角色系统参照物:`RoleAPI` 冻结面 14 方法、阵营操作面、组件纪律(**SHDF 扩展线参照**;对照实现随 Shdf 侧阶段3,插件坐标见 Shdf具体玩法设计案/) |
| 被替换的旧宿主插件源码 | **SHDF 迁移线参照**(7.3 迁 Shdf 侧,迁移对照来源随 Shdf 侧) |
| `C:\Users\ROG\.zcode\skills\papermc-plugin-dev\`(技能) | Paper 1.21.11 API 事实与构建/实测流程 |

## 7. v4.0 影响清单与交付顺序(会话级组件层)

> ★v4.0:组件从"单一挂载级"扩为**两级挂载**——实例级 `MatchModeComponent`(随实例 `close()` 全灭)+ 会话级 `SessionComponent`(**每会话一份**、跨模式切换存活、随 `dissolve` 全灭);两级互相只读查询,干预只走端口(`end()`/`switchTo()` ↔ `requestSwitch()`/`requestDissolve()`)。
> 变更全文见 `../17-v4变更指示书.md`(变更指示,非设计章节),决策记录见 `../docs/adr/0004`,新增验收为 §16.2 的 **★27~★33**。
> 下表按指示书 §6.4 的口径逐条标注受 v4 影响的子阶段(其中 **2.2 行为落盘期补充**——该键的消费者标注随 D11 变更,仅注释层、无新增配置键);其余子阶段的"v4 影响:无/或注"行由各阶段 `README.md` 给出(指示书 §6.4 末行)。

### 7.1 受影响子阶段

| 子阶段 | v4 影响 | 处置(落点) |
|---|---|---|
| 1.2 聚合根与组件契约 | 组件契约**由一份变两份**:`SessionComponent` 照抄 `MatchModeComponent` 纪律(注入四条件、`Specification` 形状);`MatchModeInstancePort` 新增一行 `SessionView session()`;`MatchModeInstanceServices` 三成员**不变**(★25 口径不变) | 已交付类型不改;追加面落在 **2.6** |
| 1.3 实例容器-事件总线与节拍器 | `ComponentRegistry` 是 `SessionComponentRegistry` 的**形状参照**(会话版:装配期依赖校验 + 声明序派发 + 逆序 `stop` + 故障隔离);`EngineTicker.tickAll` 由三步扩为**四步**(★顺序是规格的一部分:新增 ③ 会话组件派发);会话事件族新增 `SessionComponentQuarantined`(D9) | 已交付容器与驱动器不改;接缝落在 **2.6** |
| 1.4 架构冻结测试 | **测试预留**:`LayeringTest` 判定补充条款——`component/session/**` 归 L3(mode 允许 import 它);`MatchModeInstanceServices` 3 成员不变;`MatchModeInstancePort#session()` 允许 | 本子阶段只写"v4 增补注"**预留条款**(不修既绿测试、不放宽断言);修订实现在 **2.6** |
| 2.1 会话基础件 | `SessionPolicy` 的**空场部分改造为组件**:空场判定与 `idleTimers` 空场计时**删除**,下放 `component/session/EmptyArenaComponent`(rotation **不挂 = 永不解散**;arena 挂,时长读 `session.dissolve-after-empty-seconds`);连接追踪与宽限 sweep **留内核**(**★v1.3-D5:该键已删,时长改由宿主组装 `SessionSpec.sessionComponents` 时自供;`SessionPolicy` 的 topology/dissolveAfterEmptySeconds 字段失去填充来源**) | 2.1 已交付件由 **2.6** 改造(第一波迁移,D11) |
| 2.2 模式注册与配置装载 | 默认 `config.yml` 键表:`session.dissolve-after-empty-seconds` 的**消费者**由 `2.1 SessionPolicy` 改为 **L0 装配**(喂 `EmptyArenaComponent.Spec`;键保留,§15.2、D11)(**★v1.3-D5:键废止——消费者改宿主 spec 组装**) | 2.2 文档键表与 `config.yml` 键注释同步改(仅注释与消费者标注;无新增配置键) |
| 2.3 switchMode 两阶段切换 | 切换阶段 **A2 能力集口径注**:可用能力集 = 进程级标签 ∪ **本会话**会话组件标签(`MatchModeMeta.requiredCapabilities` 的机制不变,只扩可用集来源;缺任一 → `REJECTED_BY_TARGET_MODE` + detail);`CapabilityRegistry` 增会话组件注册的标签;`SessionService` 公开方法签名不变 | 2.3 已交付的 A2 校验点由 **2.6** 增注 |
| 2.5 大厅模式与最小闭环 | **不涉**——会话级组件按会话装配、与模式无关;模式不得注册/增删会话级组件(D4) | 无 |
| 7.1 HydrokarbsApi 冻结面 | **API 冻结面结论:不变**——会话级组件第一版**不进** `HydrokarbsApi` 冻结面;`SessionService` 方法签名不变(装配/派发/回收接线扩展不改变公共面) | 7.1 冻结清单与 `HydrokarbsApiSurfaceTest`(★19)不变;第三方查询会话组件列为未决(17 §11-Q3) |

### 7.2 待办

- **阶段 2 内插 `2.6-会话级组件层.md`**(编号排在 2.5 之后):对应设计案 `17`-D1~D11、02 §2.5.1、03 §3.8;交付物 = `engine/component/SessionComponent`(+`SessionComponentRegistry`)、`engine/port/` 5 类型(`SessionServices`/`SessionPort`/`SessionComponentLookupPort`/`MatchModeInstanceAccess`/`SessionView`)、`component/session/EmptyArenaComponent`、L0 装配接线(rotation/arena 分支;**★v1.3-D5:该分支废止,装配者改宿主**——见 2.6 注)、`SessionSpec` 扩展;验收 = ★27~★33(★32 的桥接部分除外);前置 = 1.2(组件契约纪律)、2.1/2.3(会话内核与切换)。

### 7.3 交付顺序约束

- **硬约束**:v4 机制(**2.6**)必须先于**阶段 4/5 的模式开发**完成——否则会出现"**模式顺手写会话级需求**"的**回流**:本该住会话级组件的行为被就地写进模式或实例级组件,2.6 之后再拆即破坏性返工。
- 落点:2.6 属阶段 2,天然先于阶段 3/4/5(§2 依赖图);阶段 4 与阶段 5.1/5.2 开工前先核对 2.6 已绿。

## 8. v4.1 影响清单与交付顺序(进程级服务层与 ServiceRegistry)

> ★v4.1:新增**进程级服务层**——服务(Service)是与组件并列的另一类对象,不挂进会话/实例、不进组件表,经 `ServiceRegistry` **两阶段装配**(`install` 声明 → `seal` 依赖校验 + 拓扑序创建 + 启动,失败回滚不留半启动态);经**订阅会话事件、只读快照、`SessionService` 受控命令**三条通道与会话对话;**组件体系与服务互不可见**(修正案 §1 一页摘要)。
> 变更全文见 `../18-v4.1修正案-进程级服务层与ServiceRegistry.md`(变更指示,非设计章节),决策记录见 `../docs/adr/0006-process-service-registry.md`(修正案原文编号 0005,因冲突顺延为 0006),新增验收为 §16.2 的 **★34~★40**(阶段映射见本文 §4)。
> 下表按修正案 §8 的口径逐条标注受 v4.1 影响的子阶段;其余子阶段的"v4.1 影响:无/或注"行由各阶段 `README.md` 给出(修正案 §8:其余阶段 4/5/6 为"v4.1 影响:无")。

### 8.1 受影响子阶段

| 子阶段 | v4.1 影响 | 处置(落点) |
|---|---|---|
| 1.4 架构冻结测试 | **测试预留**:`LayeringTest` 增补条款预留(★40 两款:`..service..` 不 dependOn `..mode../..component../..bridge.role..`;`..component../..mode..` 不 dependOn `..engine.service../..service..`);`EnginePurityTest` 禁词表确认不变(`engine/service/` 无内容词) | 本子阶段只写"v4.1 增补注"**预留条款**(不修既绿测试、不放宽断言);实现落在 **1.6** |
| 2.1 会话基础件 | **SessionEventBus 服务订阅者注**:服务是总线既有定位"给跨切面逻辑用"的第一类正式订阅者;订阅面即既有 publish/subscribe(零改动),订阅句柄在服务 `stop` 中注销 | 2.1 已交付总线零改动;本子阶段文档加一行注,订阅落地在 **1.6** |
| 3.3 Capability 软依赖机制 | **CapabilityRegistry 注册者注**:注册者扩为三类——L1 / 第三方插件 / **服务(seal 期注册,随 `shutdown` 摘除)**;机制、字符串标签口径、A2 校验均不变 | 3.3 已交付机制不改;本子阶段文档加一行注,能力标签接线在 **1.6**;**该子阶段文档迁 Shdf 侧,注册者注同步移交**(§1) |
| 7.1 HydrokarbsApi 冻结面 | **API 冻结面结论:不变**——服务与 `ServiceRegistry` **不进** `HydrokarbsApi` 冻结面(D9);`SessionService` 公开方法签名不变 | 7.1 冻结清单与 `HydrokarbsApiSurfaceTest`(★19)不变;第三方消费/提供服务列为**未决**(18 §12-Q9,仲裁去向 07 §7.4,第一版明确不进) |

### 8.2 待办

- **阶段 1 内插 `1.6-进程级服务装配.md`**(编号排在 1.5 之后):对应设计案 `18`-§3 契约骨架与 §8 行 2(七段模板口径);交付物 = `engine/service/`(`ServiceRegistry`/`ServiceSpec`/`ServiceEnv`/`ServiceLifecycle`/`ServiceDependencyException`)、`engine/port/ConfigView`、`service/` 参照服务(`GlobalLedger`/`GlobalBoard`)、测试 `ServiceRegistryDupTest`/`ServiceRegistryDepTest`/`ServiceRegistryOrderTest`/`ServiceRegistryRollbackTest`/`ServiceRegistryShutdownTest`/`ServiceEnvTest`;施工步骤 = 契约 → 注册表(查重/校验/拓扑/回滚)→ 能力标签接线 → 参照服务 → 测试;陷阱 = 14.31~14.34;验收 = ★34~★40(首次建立于 1.6,持续验证于全阶段,映射见 §4);前置 = 1.2(契约纪律)、1.3(容器与事件总线形状)。

### 8.3 交付顺序约束

- **顺序**:1.6 前置 = 1.2(契约纪律)/ 1.3(容器与事件总线形状);**不阻塞阶段 2–4**——1.6 是阶段 1 的内插子阶段(排在 1.5 之后),§2 依赖图主干(1 → 2 →(3 ∥ 4)→ 5 → 6 → 7)不变。
- **回流防线**:阶段 5 起凡出现**跨会话聚合**诉求(全服账本/系列赛、全局看板、进程级配对队列),先核对 **1.6** 已绿——防"**进程级需求被顺手写进内核/模式**"的**回流**(与 §7.3 同款理由,见 `../17-v4变更指示书.md` §7.3)。

## 9. v1.1 影响清单与交付顺序(框架独立插件化拆分与 API 面扩充)

> ★v1.1:框架(HydroKarbs)拆为独立插件、玩法(宿主)成为消费插件(`depend: [HydroKarbs]`);`HydrokarbsApi` 扩充为 v2(API_VERSION = 2:成员操作/会话事件订阅/能力标签注册/会话组件贡献 SPI(D14));API 编译面白名单冻结(`ApiSurfaceTest`);信任边界三层(D9);交付走 **G1(单 jar 彩排)→ G2(双 jar 物理拆分)** 两道闸门;包根更名 `com.shdf.match` → `com.hydrokarbs`(D13)。
> 变更全文见 `../19-v1.1修正案-框架独立插件化拆分与API面扩充.md`(变更指示,历史存档;现行变更指示见 §10/20),决策记录见 `../docs/adr/0007-standalone-framework-plugin-split.md`,新增验收为 §16.2 的 **★41~★53**(阶段映射见本文 §4)。

### 9.1 受影响子阶段

| 子阶段 | v1.1 影响 | 处置(落点) |
|---|---|---|
| 1.1 工程骨架与构建链路 | **包根更名**(D13:group/包目录/plugin.yml main/META-INF 路径,与 API_VERSION 1→2 同批一次付清);G1 期执行 | 落在 **7.5** 全库一次性执行(1.1 已交付工程按更名 PR 处理,ArchUnit/冻结测试包名模式同步) |
| 1.4 架构冻结测试 | ArchUnit/冻结测试的**包名模式**随 D13 更名;★40 增款(服务不得 import `api/**`,14.37) | 包名模式随 **7.5** 同批;增款条款在 7.5 建立 |
| 7.1 HydrokarbsApi 冻结面 | **冻结面扩充**(只增不改):API_VERSION + D2/D3/D4/D14 增量;`HydrokarbsApiSurfaceTest` 方法集快照更新 | 扩充实现与快照更新落在 **7.5**(7.1 已交付的 v1 面不动) |
| 7.2 第三方注册与扩展 | `MatchModeProvider#requiredApiVersion()` 默认方法;示例工程 TheirPlugin 补 API_VERSION 检查首步 | 落在 **7.5** |
| 其余阶段(2/4/5/6) | 引擎内部机制零改动——**v1.1 影响:无** | 各阶段 README 原注不变 |

### 9.2 待办

- **阶段 7 内插 `7.5-API面扩充与拆分彩排.md`**(G1;编号排在 7.4 之后):全库包根更名落地(D13)→ api 增量类型(EventSubscription/SessionEventView 族/SessionRecipe/SessionComponentProvider)→ `ApiSurfaceTest` 白名单与签名快照 → D2/D3/D4/D14 实现(薄委托 + provider 干跑合并校验)→ API_VERSION 与拒载样例 → 测试伴随插件(第三方目录测试插件升级为真实消费样例,承担 ★41~★46、★51~★53)。前置:7.1(API 冻结面)。
- **新增 `阶段8-独立插件化交付/`(8.1)**(G2):Gradle 双工程/复合构建 → 玩法插件 depend 接线与资源迁移 → `PluginDisableEvent` 摘除路径(D8)→ 预算观测(D9③)→ ★47~★50。前置:7.5 全绿 + **触发条件:首个真实跨 jar 消费者**(A13)。

### 9.3 交付顺序约束

- **G1 先于 G2,且 G2 只认触发条件**:G1(7.5)是 7.4 之后的收口扩充;G2(8.1)不设时间表——**首个真实跨 jar 消费者(伴随插件或第二宿主)立项前不启动**(A13:过早物理拆分会把 API 面瑕疵固化成二进制契约)。
- **回流防线**:G1 期间任何"宿主代码绕过白名单直调内部面"的写法按彩排纪律驳回——形态 A 下仅 **L0 装配/监听/命令** 三处豁免可触内部面;G2 之后豁免清零,宿主与第三方同权(19 §8)。
- 服务不经 `HydrokarbsApi`(D10):7.5 实现 D2 成员操作时同步落 ★40 增款 ArchUnit(14.37)。

## 10. v1.2 影响清单与交付顺序(地图注册通道)

> ★v1.2:宿主/伴随插件经 `HydrokarbsApi` 显式注册/摘除地图声明(`registerMap(Plugin, MapDefinition)` / `unregisterMap(Plugin, String)`;API_VERSION 2→3)——文件装载之外的第二输入通道,补齐形态 B 玩法图的运行期发现缺口;**地图交成品不交工厂**(`MapDefinition` 纯静态值);`WorldPort` 与切换 A3/A6 零改动。
> 变更全文见 `../20-v1.2修正案-地图注册通道.md`(变更指示,现行,非历史存档),决策记录见 `../docs/adr/0008-map-registration.md`,新增验收为 §16.2 的 **★54~★55**(阶段映射见本文 §4)。

### 10.1 受影响子阶段

| 子阶段 | v1.2 影响 | 处置(落点) |
|---|---|---|
| 7.1 HydrokarbsApi 冻结面 | 冻结面 v3 增量(两方法,只增不改;API_VERSION=3) | 扩充实现与快照更新落在 **7.5**(7.1 已交付的 v2 面不动) |
| 7.5 API 面扩充与拆分彩排 | 交付物增:`MapRegistry` 第二输入(汇入同一私有 `register()`)、注册期校验(查重/tags/模板目录)、卸载守卫(正用拒绝)、`MapRegistrationTest`(★54~★55)、测试伴随插件带一张注册式地图作消费样例 | 并入 **7.5** 施工步骤与 DoD(与 api 增量/包根更名同批交付) |
| 2.2 模式注册与配置装载 | `MapRegistry` 行注:双输入口径(文件装载 + 显式注册) | 文档加一行注(仅口径注,无新增配置键) |
| 其余阶段 | **v1.2 影响:无**——引擎内部机制、切换、世界生命周期、持久化零改动 | 各阶段 README 原注不变 |

### 10.2 交付顺序约束

- 地图注册随 **7.5**(G1)交付——它是 API 面扩充的一部分,不单独设子阶段;★54~★55 首建于 7.5、全阶段持续验证。
- 模板落盘时序纪律(解包 → 构造 → 注册)写入测试伴随插件的消费样例与 14.39 条目;`/sm reload maps` 不触碰注册式地图的口径随 2.2 行注与 15 §15.2 注落盘。

## 11. v1.3 影响清单与交付顺序(框架基础设施瘦身与配置中立化)

> ★v1.3:持久化(R1)与表现层(R2)整体出框架、发行配置中立化(R3)——框架基线移除 `data/**`、`engine/port/PersistencePort`(连接口一起删,零消费者的假想接缝,判据与 A13/A9 同源)、`bridge/persistence/`、`component/display/**`、`render/**`、config `database:`/`display:` 块与 `engine.shutdown-flush-budget-seconds`;`initial-mode`/`fallback-mode` 默认改空(空值语义见 21-D3);`config-version` 2→3;`performance.component-budget-ms` 保留(信任边界观测)。**同日 D5 增补:启动建会话特性删除、会话创建权移交宿主**——`HydroKarbsAssembly` 整段拓扑装配废止,配置键 `session.topology`/`session.arenas`/`session.initial-mode`/`session.dissolve-after-empty-seconds` 删除(`session.fallback-mode`/`min-dwell-ticks`/`disconnect-grace-seconds` 三节律键保留);拓扑 = 宿主调几次 `SessionService.create(SessionSpec)`(冻结签名不动);框架 onEnable 零会话,启动期不再发生任何切换。
> 变更全文见 `../21-v1.3修正案-框架基础设施瘦身与配置中立化.md`(变更指示,现行,非历史存档),决策记录见 `../docs/adr/0009-framework-infrastructure-slimming.md`;**零新增验收编号**,★10~★12 归属改标「宿主线(框架侧 v1.3 废止)」(§4);架构断言族零触碰。

### 11.1 受影响子阶段

| 子阶段 | v1.3 影响 | 处置(落点) |
|---|---|---|
| 4.3 表现层 DisplayComponent | **撤销**——`component/display/**` 与 `render/**` 出框架,设计资产迁 `../Shdf具体玩法设计案/10-表现层组件.md`(保全不销毁);`display:` 配置块删除 | 文档头部加「v1.3 撤销」注(文件保留存档);阶段4 出口闸门与 §6.1 计数 21→20;宿主侧 display 组件落地随 Shdf 侧施工 |
| 5.3 数据库基建 / 5.4 仓储与 write-behind 落库 | **整体迁宿主线**——`data/**`/`PersistencePort`/`database:` 块出框架;★10~★12 命题随宿主线持久化执行 | 两文档头部加「v1.3 迁宿主线」撤销注(文件保留存档,作为宿主建库参照);阶段5 框架半段从本计划移除,由 Shdf 侧计划承接新增对应子阶段(回填义务,21 §5) |
| 1.1 工程骨架与构建链路 | build 依赖删 HikariCP/`mysql-connector-j` 两行与 shade 义务;`sql/` 资源目录不建 | 施工时按 12 §12.1 的 ★v1.3 删除注执行(文档已注) |
| 2.1/2.2 会话基础件与配置装载 | L0 缺省兜底改空串(不再有 `"lobby"`);空初始模式 → WARN + 会话待机;空 fallback → 跳级 dissolve;`config-version` 3(**★v1.3-D5:`session.topology`/`arenas`/`initial-mode`/`dissolve-after-empty-seconds` 四键删除——默认资源不落这些键;空初始模式待机路径随 D5 消失,由宿主 create 参数承载**) | 按 15 §15.2 v1.3 版与 21-D3/D5 落地;空值语义的施工口径见 21 §5 回填义务-6;宿主建会话编排见 21 §5 回填义务-7 |
| **(★v1.3-D5)2.3/2.6 L0 装配与阶段8 加载时序** | **启动建会话特性删除**——2.3 步骤 13「按 topology 建会话」与 2.6 步骤 12「L0 装配接线按 topology 构建 sessionComponents」废止:框架 onEnable 零会话,读节律三键构造 `SessionPolicy`、装配 `CapabilityRegistry`/`WorldPort` 保留;建会话(`create(SessionSpec)`,填 initialModeId/地图池/会话组件表)是宿主编排动作;阶段8 的「拓扑装配延迟至首 tick/`ServerLoadEvent`」补丁随之作废 | 2.3/2.6 文档加 ★v1.3-D5 注(已注);宿主侧建会话编排登记 21 §5 回填义务-7(Shdf 侧施工承接) |
| 7.1 HydrokarbsApi 冻结面 | 「查战绩」面(`stats`/`top`)随 R1 悬置——7.1 落地不含 DB 依赖项;API_VERSION 维持 3、方法集不变(`createSession`/`SessionRecipe.initialModeId` 与 D5 同向:调用方填初始模式,冻结面零改动) | 7.1/7.2 文档施工时按 07 §7.1 ★v1.3 注执行;回填裁决登记 21 §6-Q1 |
| 7.4 全量验收与发布 | 验收计数 51→48(★10~★12 归宿主线);★10/★11/★12 条目与 10-数据库引用加归属迁移注;发布 checklist 的「shadow jar 含 HikariCP+mysql-connector-j」项废止;**★v1.3-D5:压测场景 S1 的 `session.topology: arena` 配置方式废止——多会话压测改为宿主经 `create` 建 N 个会话**(7.4 已注) | 7.4 文档加 ★v1.3 注;宿主线执行 ★10~★12 时结果记录于宿主线完成清单 |
| 其余阶段(2 余项/4 余项/8 余项) | 引擎内部机制零改动——**v1.3 影响:无**(4.3/5.3/5.4 与上列 D5 行除外) | 各阶段 README 原注不变 |

### 11.2 交付顺序约束

- **框架线子阶段计数 23→20**(4.3、5.3、5.4 出列);依赖图主干 1 → 2 →(3 ∥ 4)→ 7 → 8 不变——阶段5 框架半段消失后,5.1/5.2(玩法线)直接依赖阶段4,阶段6/7 不再依赖框架侧持久化。
- **宿主线回填义务**(21 §5):宿主自建持久化时在 Shdf 侧施工计划新增子阶段并执行 ★10~★12;框架侧任何人不得以「验收需要」为由把 DB 栈加回框架(违者按 §3-4「测试即架构」纪律驳回)。
- **配置迁移**:存量部署 `config-version` 2→3——旧 `database:`/`display:` 块被忽略并提示归属变更;`fallback-mode` 原值若为 `lobby`,升级时必须显式确认目标模式 id(空值 = 直接 dissolve 的新语义);**★v1.3-D5:`session.topology`/`arenas`/`initial-mode`/`dissolve-after-empty-seconds` 四键废止**(升级后不再生效)——原 rotation/arena 部署与开局模式的编排改由宿主在编排点调 `SessionService.create(SessionSpec)` 表达(填 `initialModeId`/地图池/会话组件表,空场时长直接喂 `EmptyArenaComponent.Spec.after`),宿主线迁移指引登记 21 §5 回填义务-7。

## 12. v1.4 影响清单与交付顺序(API 面收敛与地图注册单通道)

> ★v1.4(2026-10-27):①**API 面收敛**——两参 `createSession(String, String)` 重载删除,`SessionRecipe` 重载为公共 API 建会话唯一形态;API_VERSION 3→4,冻结面 16→15 方法(★19 冻结清单与 ★44 白名单快照随裁决合法重录,07 §7.2 One-Version Rule:同一能力只保留一份口径)。②**地图注册单通道**——「配置文件声明地图」特性退场:文件式装载整链删除(`ConfigRepository.loadMaps`、`YamlCodec.readMap`、`MapDefinition.from`、`MapRegistry.replace` 与文件半边、无参构造),`MapRegistry` 只剩显式注册/摘除通道(目录顺序 = 注册序);世界模板目录约定 `maps/templates/<mapId>/`、注册期校验与「正用摘除被拒」守卫(20-D3/D4)不变。(**★v1.8 点注(26 号,权威文本 = 26 号 §5)**:「世界模板目录约定…不变」半句为 v1.4 时点口径——模板根约定随 26 号退场;「注册期校验」在新形状 = 声明路径存在且为目录 + 同路径查重(★61/★62 续守),「正用摘除被拒」(20-D4)不变)
> 变更全文见 `../22-v1.4修正案-API面收敛与地图注册单通道.md`(变更指示,现行,非历史存档),决策记录见 `../docs/adr/0010-api-surface-consolidation.md`(Supersedes ADR-0008 的文件式通道部分);新增验收 **★56~★57**(§4,首建于 7.1/7.5);施工已随 v1.4 窗口完成(git 实况为准),本节留档影响面。

### 12.1 受影响子阶段

| 子阶段 | v1.4 影响 | 处置(落点) |
|---|---|---|
| 7.1 HydrokarbsApi 冻结面 | 冻结面 16→15 方法:两参 `createSession` 行删除(recipe 唯一形态,迁移口径 = `SessionRecipe.of(modeId, mapId)`);API_VERSION 3→4 | 施工已完成(冻结测试三件套同步重录);7.1 文档头部加 ★v1.4 注 |
| 7.5 API 面扩充与拆分彩排 | 文件式地图相关测试随单通道收敛(`MapRegistrationTest` 删文件通道三用例;`MapRegistryTest` 重写为注册/摘除语义);新增 testkit `MapTestKit` 造数 fixture | 施工已完成;7.5 文档头部加 ★v1.4 注 |
| 2.2 模式注册与配置装载 | 配置装载退地图:`loadAll` 只解析 `config.yml` + `modes/*.yml`,零地图条目(★57) | 代码已落(2.2 文档为施工序列存档,按 22 §4 指示读);装载行为由 `ConfigRepositoryTest` 复验 |
| 其余阶段(1/2 余项/4/8) | 引擎内部机制零改动——**v1.4 影响:无**(上列 7.1/7.5/2.2 除外) | 各阶段 README 原注不变 |

### 12.2 交付顺序约束

- **子阶段计数与依赖图不变**:v1.4 是对已验收面(7.1/7.5 已于 2026-10-25 验收)的收缩性收敛,不新增子阶段、不改依赖;★56~★57 由 v1.4 窗口补建。
- **存量部署迁移**:①两参 `createSession` 调用方改 `SessionRecipe.of(modeId, mapId)`;②原 `maps/*.yml` 声明不再被框架装载——改由宿主/玩法插件代码构造 `MapDefinition` 后 `registerMap`(迁移口径 22-D3;服主侧「零代码加图」诉求登记 22 §6-Q2,不预置);③消费方 `onEnable` 版本检查(14.36)在 v4 下照常拒载不崩服。

## 13. v1.5 影响清单与交付顺序(REUSE_DIRTY 副本随会话解散销毁)

> ★v1.5(2026-10-27):世界副本的**复用窗口 = 会话生命周期**——REUSE_DIRTY 归零保留的唯一理由「同会话复载」随会话死亡失效,保留目录从缓存退化为孤儿残留并随会话代际线性累积;本版把窗口关闭点定为**会话解散**:`WorldPort` 增第三方法 `discardSession(SessionId)`,解散经它销毁该会话名下全部世界副本,三类全覆盖:①计数表该会话**存活条目**(摘条目 → 卸载不存档 + 删目录,策略无关);②**曾发放已保留目录**(曾切换离开而保留在磁盘者,端口「会话曾发放 mapId 集」簿记,按精确名 `<mapId>-<短码>` 删除——不做后缀猜测,短码可含 '-');③**迟到 in-flight**(会话短码记入「已死短码集」,其后归零处置无论策略一律删)。切换路径(B5 换图对 REUSE_DIRTY 保留目录)与 TEMPLATE_RESTORE(归零删副本)行为零改动;删除仍唯一走 `deleteCopyDir` 入口 + 路径形态断言;卸载/删除失败 best-effort 保留 + WARN/SEVERE;公共面零改动(API_VERSION 维持 4)。
> 变更全文见 `../23-v1.5修正案-REUSE_DIRTY副本随会话解散销毁.md`(变更指示,现行,非历史存档),决策记录见 `../docs/adr/0011-reuse-dirty-copy-dispose-on-dissolve.md`;新增验收 **★58**(§4,首建于 2.4);施工已随 v1.5 窗口完成(git 实况为准),本节留档影响面。

### 13.1 受影响子阶段

| 子阶段 | v1.5 影响 | 处置(落点) |
|---|---|---|
| 2.4 WorldPort 世界生命周期 | 接口两法 → 三法(增 `discardSession`);disposeAfterZero 删除判据 = TEMPLATE_RESTORE **或** 会话短码已死;新增「会话曾发放 mapId 集」与「已死短码集」两簿记 | 施工已完成(`WorldRefCountTest` 扩展 ⑧ 断言族:同会话复用保留至解散销毁/双图全覆盖/TEMPLATE_RESTORE 结论一致/迟到 in-flight 删/幂等无害);本档头部加 ★v1.5 注 |
| 2.3 会话切换 | `dissolveNow` finally 的释放接缝改发 `discardSession`(「先摘再释放」双方保险纪律保留);B5/迟到 onWorldReady 释放零改动 | 施工已完成(`ModeSwitchAtomicityTest` 断言解散发起 discardSession);2.3 文档为存档,按 23 §4 指示读 |
| 其余阶段(1/4/7/8) | 引擎内部机制零改动——**v1.5 影响:无**(上列 2.3/2.4 除外) | 各阶段 README 原注不变 |

### 13.2 交付顺序约束

- **子阶段计数与依赖图不变**:v1.5 是对 2.3/2.4 已验收面的行为收口(复用窗口的自然闭合),不新增子阶段、不改依赖;★58 由 v1.5 窗口补建。
- **行为对照**:会话存活期内的切换离开 = REUSE_DIRTY 目录保留(复用语义不变);会话解散 = 名下全部副本销毁(三类);关停(SHUTDOWN 解散循环)同口径清理——解散循环先于 `worldIo.shutdown()`,已提交删除任务放行完成。

## 14. v1.6 影响清单与交付顺序(显式注册收敛)

> ★v1.6(2026-10-27):模式与会话组件的注册从「ServicesManager 提供方通道(ServiceRegisterEvent/ServiceUnregisterEvent 事件动态收录/摘除)」改为 `HydrokarbsApi` **公共面显式注册四法**——`HydrokarbsApi` 升 v5(API_VERSION 4→5),冻结面 15→19 方法(+`registerMode(Plugin, MatchModeProvider)`/`unregisterMode(Plugin, modeId)`/`registerSessionComponents(Plugin, specs)`/`unregisterSessionComponents(Plugin)`);注册期双口径合一**响亮**(显式调用方可捕获,原事件回调「不上抛/拒收录」口径随通道退场);注册时序反转 = 第三方必须 `depend: [HydroKarbs]`、`load(HydrokarbsApi)` 后在 onEnable 显式登记(原「先启补收录」场景退役);disable 兜底扩**四族**(能力标签/模式/地图/会话组件贡献,`ProviderUnloadListener` 四职责块);冻结测试合法更新(FROZEN 19 行、BASELINE 重录、API_VERSION=5)。
> 变更全文见 `../24 号修正案`(v1.6 显式注册收敛;正文落盘前以本横幅与本节为准);新增验收 **★59**(§4,首建于 7.5);施工已随 v1.6 窗口完成(git 实况为准),本节留档影响面。

### 14.1 受影响子阶段

| 子阶段 | v1.6 影响 | 处置(落点) |
|---|---|---|
| 7.2 第三方注册与扩展 | **收录面退役**——原施工序列(ProviderServiceListener 的 `ServiceRegisterEvent` 收录 / `ServiceUnregisterEvent` 摘除 + 「运行期 SEVERE + 忽略后来者」双口径 + 摘除回退在监听器)整体退场;第三方注册/摘除改 api 显式四法(响亮闸;归属簿记与回退编排迁 `HydrokarbsApiImpl`),兜底四族落 `ProviderUnloadListener` | 施工已完成;本档头部加 ★v1.6 注(原文为施工序列存档,不改写) |
| 7.5 API 面扩充与拆分彩排 | **显式注册面**——`HydrokarbsApi` 冻结面 15→19 法(API_VERSION 4→5);`ApiSurfaceTest`(BASELINE 重录)/`HydrokarbsApiSurfaceTest`(FROZEN 19 行)/版本常量随 v1.6 合法更新;D14 通道一收录改 `registerSessionComponents` 显式注册(响亮上抛,原事件通道口径退役) | 施工已完成;本档头部加 ★v1.6 注 |
| 示例与测试迁移(`examples/TheirPlugin` + api 测试) | TheirPlugin 的 onEnable 顺序改显式注册四法(depend + load(HydrokarbsApi) 版本检查(REQUIRED = 5)→ `registerMode` → `registerSessionComponents` → 解包模板 → `registerMap` → 订阅/命令)、onDisable 显式反注册(守卫测试同步);`ThirdPartyModeRegistrationTest`/`SessionComponentContributionTest` 改显式注册通道复验(★20/★41/★51 命题不变) | 施工已完成(git 实况为准) |
| 其余阶段(1/2/4/8) | 引擎内部机制零改动——**v1.6 影响:无**(上列 7.2/7.5/示例迁移除外) | 各阶段 README 原注不变 |

### 14.2 交付顺序约束

- **子阶段计数与依赖图不变**:v1.6 是对已验收面(7.1/7.2/7.5)的注册面收敛,不新增子阶段、不改依赖;★59 由 v1.6 窗口补建。
- **存量消费方迁移**:①原经 `getServicesManager().register(MatchModeProvider.class, …)` 的第三方插件 → `depend: [HydroKarbs]` + `onEnable` 里 `load(HydrokarbsApi)` 版本检查(REQUIRED = 5)→ `api.registerMode(this, factory)`;②原 `SessionComponentProvider` 实现类 → 拆为 `SessionComponent.Spec` 清单,`onEnable` 调 `api.registerSessionComponents(this, specs)`、`onDisable` 调 `api.unregisterSessionComponents(this)`;③能力标签无独立反注册法,随 disable 由兜底四族摘除(D8);④显式反注册仍是纪律(兜底不是免死金牌,14.38)。

## 15. v1.7 影响清单与交付顺序(能力标签机制退场)

> ★v1.7(2026-10-27;用户裁决:字符串契约不严谨):字符串能力标签平行层整体退役——`CapabilityRegistry`、切换 A2 能力校验、`HydrokarbsApi#registerCapability`/`capabilities` 两法(API_VERSION 5→6,冻结面 19→17 方法)、`MatchModeMeta.requiredCapabilities`(Builder 声明语言变更)、`MatchModeInfo` 快照分量、`SessionComponent.Specification#capabilities`、`ServiceSpec.capabilities` 与 seal/shutdown 两端注册、`ProviderUnloadListener` 能力块(兜底四族收敛三族)、`capabilityClaims` 簿记;**外部能力新形态 = 会话组件桥接**:提供方(或宿主自持防腐层)经 `registerSessionComponents`/`SessionRecipe` 贡献类型化桥接组件,消费方组件 `requires`/`requiresOptional` 声明依赖,装配期响亮、运行期 quarantine;软依赖由 `requiresOptional` 独自承载(机制已在框架内,非新增;★21/★60);**语义位移**:能力校验从「每次切换重查(进程级)」改为「创建时绑定 + 运行期降级/quarantine」——provider 摘除只影响后续会话,存量会话由桥接组件自身降级承接(D14 既有口径);冻结测试合法更新(FROZEN 17 行、BASELINE 重录、API_VERSION=6)。
> 变更全文见 `../25 号修正案`(v1.7 能力标签机制退场;正文落盘前以本横幅与本节为准);新增验收 **★60**(§4,首建于 7.5)、**★32 同批废止**(编号不删不重排,废止行不计有效命题);施工已随 v1.7 窗口完成(git 实况为准),本节留档影响面。

### 15.1 受影响子阶段

| 子阶段 | v1.7 影响 | 处置(落点) |
|---|---|---|
| 1.6 进程级服务装配 | `ServiceSpec` 的 `capabilities` 声明与 `ServiceRegistry` 构造注入(`CapabilityRegistry`)/seal 注册/shutdown 摘除两端标签接线退场;能力标签随 seal 注册/随 shutdown 摘除的测试项废止 | 施工已完成;本档头部加 ★v1.7 注(原文为施工序列存档,不改写) |
| 2.3 switchMode 两阶段切换 | 切换 **A2 能力校验退役**(`CapabilityRegistry`/`requiredCapabilities` 全链删除)——外部能力装配闸改由组件 `requires`/`requiresOptional` 承载,硬依赖缺失在装配期响亮(干跑合并校验/`createSession` 拒绝);A2 行保留为历史口径行,步骤表不重排 | 施工已完成;本档头部加 ★v1.7 注(原文为施工序列存档,不改写) |
| 2.6 会话级组件层 | `SessionComponent.Specification#capabilities` 方法与 Snapshot `capabilities` 分量删除(原「会话组件标签进 A2 可用集」交付项随机制废止);**★32 框架半边随 ★60 改判**(废止) | 施工已完成;本档头部加 ★v1.7 注(原文为施工序列存档,不改写) |
| 7.1 HydrokarbsApi 冻结面 / 7.5 API 面扩充与拆分彩排 | **冻结面 19→17 与快照重录**——`registerCapability`/`capabilities` 两法随冻结面收敛删除(API_VERSION 5→6);`HydrokarbsApiSurfaceTest` FROZEN 17 行、`ApiSurfaceTest` BASELINE 重录(`SessionRecipe`/view record 的能力分量行同步删除)、`CapabilityRegistryTest` 删除;示例与测试样例 REQUIRED_API_VERSION 升 6 | 施工已完成;两档头部加 ★v1.7 注(原文为施工序列存档,不改写) |
| 7.2 第三方注册与扩展 | **收录面**:D4 能力标签注册法随两法删除(API_VERSION 5→6、冻结面 19→17);disable 兜底四族收敛**三族**(能力职责块移除);消费样例 REQUIRED_API_VERSION 升 6 | 施工已完成;本档头部加 ★v1.7 注(原文为施工序列存档,不改写) |
| 其余阶段(1 余项/2 余项/4/7 余项/8) | 引擎内部机制零改动——**v1.7 影响:无**(上列 1.6/2.3/2.6/7.1/7.5/7.2 除外;14.38 兜底三族随注,零新增陷阱编号) | 各阶段 README 原注不变 |

### 15.2 交付顺序约束

- **子阶段计数与依赖图不变**:v1.7 是对已验收面(1.6/2.3/2.6/7.1/7.2/7.5)的机制退场收敛,不新增子阶段、不改依赖;★60 由 v1.7 窗口补建、★32 同批废止(编号不删不重排)。
- **存量消费方迁移**:①原调 `registerCapability(Plugin, tag)` 的提供方 → 贡献**类型化桥接会话组件**(`onEnable` 调 `api.registerSessionComponents(this, specs)` 或经 `SessionRecipe` 单次携带),消费方组件在 Spec 上 `requires(...)`/`requiresOptional(...)` 声明依赖;②消费方 `onEnable` 版本检查 REQUIRED = 6(14.36);③**存量会话语义**:provider 摘除只影响后续会话,存量会话由桥接组件自身降级承接(D14;运行期 quarantine/降级既有机制,★30/★53);④显式反注册仍是纪律(兜底三族:模式/地图/会话组件贡献,14.38)。

## 16. v1.8 影响清单与交付顺序(地图声明按路径直交 + MapDefinition 瘦身)

> ★v1.8(2026-10-28;修正案 26 号,**方法面零变化**):「模板寄居框架数据目录 `maps/templates/`」约定退场——`MapDefinition` 自带 `worldDirectoryPath`(构造期规范化为绝对路径入库,缺目录名响亮抛),`MapRegistry` 删 `templatesRoot` 构造依赖、注册校验直验声明路径存在且为目录(缺失/非目录响亮抛带完整绝对路径),`HydrokarbsAssembly` 撤模板根派生;`MapDefinition` 瘦身为 `(worldDirectoryPath, metaAttributes)` 两分量(原九分量全删——tags/处置策略走 `MetaAttributes` 约定键 `map.tags`/`map.reset-strategy`,缺省 REUSE_DIRTY 语义不变;id = 声明目录名路径派生);`map/` 包 Bounds 族出册;`MapInfo` 收缩 `(id, tags, resetStrategy)`;`AttributeBag` 增 `declaration`/`copyOf`;`WorldPortImpl`/`PlaceholderWorldPort` 以声明路径为基准(复制/计数/处置管线零改动)。
> 变更全文见 `../26 号修正案`(正文落盘前以本横幅与本节为准);新增验收 **★61~★64**(§4,首建于 7.5/2.4;权威文本 = 26 号 §5)、**★54~★55/★57 就地注**(解包模板/模板目录缺失为模板根时点口径,命题不变;★57 尾部「模板目录约定…不变」半句为 v1.4 时点口径);施工已随 v1.8 窗口完成(git 实况为准),本节留档影响面。

### 16.1 受影响子阶段

| 子阶段 | v1.8 影响 | 处置(落点) |
|---|---|---|
| 2.4 地图注册 | **注册校验口径改写**:`MapRegistry` 删 `templatesRoot` 构造依赖——校验直验声明自带 `worldDirectoryPath` 存在且为目录(缺失/非目录响亮抛带完整绝对路径);id 查重 = 路径派生(同一路径再注册必撞、同名异父同撞);`MapTestKit` 按新基准改写(落盘即声明目录本体);★61~★64 断言族落本子阶段 | 施工已完成;本档头部加 ★v1.8 注(原文为施工序列存档,不改写) |
| 7.2 第三方注册与扩展 | **收录面**:地图声明构造读作新形状——`registerMap` 载荷 = 两分量 `MapDefinition`(词料经 `MetaAttributes` 约定键,构造期冻结);消费样例 REQUIRED_API_VERSION 升 7(14.36) | 施工已完成;本档头部加 ★v1.8 注(原文为施工序列存档,不改写) |
| 12(设计篇 §12) | **map/ 包行注按现行实签重录**:`MapDefinition` 两分量/增 `MetaAttributes.java`/`MapRegistry` 无模板根/Bounds 族出册;view/ 行注 MapInfo 收缩;`AttributeBag` 行注增两口;`bridge/world/` 行注复制源 = 声明自带路径;resources `maps/templates/` 与形态 B 树两处模板行加退场注 | 正文已就地修订(设计正文,非施工存档;12-代码骨架.md 版本行 v1.8) |
| 其余阶段(1/2 余项/4/5/7 余项/8) | 引擎与切换管线零改动——**v1.8 影响:无**(复制/计数/处置行为语义零改动;`SessionRecipe`/`MapInfo` 形状侧变更经 BASELINE 重录承载) | 各阶段 README 原注不变 |

### 16.2 交付顺序约束

- **子阶段计数与依赖图不变**:v1.8 是对已验收面(2.4/7.2)的声明形状收敛,不新增子阶段、不改依赖;★61~★64 由 v1.8 窗口补建(首建于 7.5/2.4,编号只增不重排)。
- **存量消费方迁移**:①原以 `MapDefinition` Builder/多分量构造的调用方 → 改两参构造 `new MapDefinition(世界目录路径, metaAttributes.frozen())`(词料经 `MetaAttributes#set` 写约定键;id 无需显式声明——从路径派生,目录名即 id);②原约定「解包到 `maps/templates/<mapId>/`」的落盘步 → 落盘位置由声明直指(如插件数据目录 `maps/<id>/`),**注册前路径已就位**(14.39 新口径);③消费方 `onEnable` 版本检查 REQUIRED = 7(14.36);④显式反注册纪律不变(兜底三族,14.38)。

## 17. v1.9 影响清单与交付顺序(切换与解散的世界清场)

> ★v1.9(2026-10-28;修正案 27 号,**内核接缝 + L0 注入函数,非破坏性**):消除「世界内有玩家 → `Bukkit.unloadWorld` 返回 false → SEVERE + 保留目录」路径(23 号销毁闭环的伴生收口)——**切换** = 同世界复用(判据 = `org.bukkit.World` 引用相等,首切空值分支跳过)新步 B3.5 清场先行于 B4 组件摆放、异图切换 B5 finally 殿后(既有 `previousWorld` 非 null 守卫内、完成事件之后、release 之前、`!sameWorldReuse` 门闩);**解散** = `dissolveNow` finally 内「先名册后世界」(摘句柄后、`worlds.discardSession` 前,heldWorld 非 null 才清);**遗留人员 = `World#getPlayers()` 世界内全员,不以会话名册为界**;平台语义收敛 `SessionServiceImpl.Builder` 两 L0 注入函数(`worldClearer`/`hubWorldClearer`,默认 no-op,落点 `HydrokarbsAssembly`,`hubSpawn()` 与 `hubRelocator` 同源单一来源),best-effort WARN 不阻塞;`WorldPort`/`HydrokarbsApi` 17 法/`API_VERSION=7` 零改动、★19/★44 零重录。
> 变更全文见 `../27 号修正案`(现行,非历史存档),决策记录见 `../docs/adr/0015-world-clear-on-switch-and-dissolve.md`;新增验收 **★65**(§4,首建于 2.3);施工已随 v1.9 窗口完成(git 实况为准),本节留档影响面。

### 17.1 受影响子阶段

| 子阶段 | v1.9 影响 | 处置(落点) |
|---|---|---|
| 2.3 switchMode 两阶段切换 | **执行段 B 插 B3.5**(B3 合并后、B4 造件前:同世界复用清场先行,判据 = `org.bukkit.World` 引用相等 + 首切空值分支)+ **B5 finally 殿后清场**(既有 `previousWorld` 非 null 守卫内、release 前、`!sameWorldReuse` 门闩;时序 = 完成事件之后 release 之前);`dissolveNow` finally 增清场口(摘句柄后、`discardSession` 前) | 施工已完成(`ModeSwitchAtomicityTest` 扩 ★65 清场断言族:异图显式 mapId/B3.5 先行/失败不阻塞/解散/null 句柄);2.3 文档为施工序列存档,按 27 §4 指示读 |
| 2.3 测试基座(SwitchHarness/FakeWorldPort) | **离线断言前提增补**:`FakeWorldPort` 增 `sticky()` 同 (map, session) 幂等同句柄工厂(镜像真端口表命中;`immediate()` 原样不动)+ `seamLog` 接缝时序录(release/discardSession 按调用序追加);`SwitchHarness` 增 `worldClears`/`hubWorldClears`/`seamLog` 记录面与 sticky/抛异常两装配口 | 施工已完成(缺该增补则同世界判据离线恒 false、release 无序可断——27 号 §6/§7 登记);本档头部加 ★v1.9 注 |
| 1.6 平台函数注入(L0 装配) | `HydrokarbsAssembly` 增 `hubSpawn()`(主世界出生点单一来源,`hubRelocator` 改用,语义零变化)与 `worldClearer()`/`hubWorldClearer()` 两私有工厂;assemble 接线两行 | 施工已完成;本档头部加 ★v1.9 注 |
| 其余阶段(1 余项/2.4/2.6/4/5/7/8) | 引擎机制与世界端口零改动——**v1.9 影响:无**(`WorldPort` 三法/引用计数/复用窗口/`HydrokarbsApi` 冻结面与 `API_VERSION=7` 一寸不动;★17/★58 冻结断言零触碰——`WorldRefCountTest` 自建 Builder,默认 no-op 清场) | 各阶段 README 原注不变 |

### 17.2 交付顺序约束

- **子阶段计数与依赖图不变**:v1.9 是对 2.3 已验收面的接缝收口(23 号销毁闭环的伴生),不新增子阶段、不改依赖;★65 由 v1.9 窗口补建(首建于 2.3,编号只增不重排)。
- **行为对照**:切换 = 同世界复用 B3.5 先行(组件其后摆位,终态正确)/异图 B5 殿后(每人至多一次传送);解散 = 先名册后世界(两级覆盖互为兜底);清场失败 WARN 不阻塞,「卸载失败保留目录」(23-D2)仍为最后防线;同图重开「原地起步 → 出生点起步」为经 27 号修正案裁决的冻结语义变更(跨局位置保留改组件自读 Handoff 自理);**runServer 运行期观察项**:`World#getPlayers` 枚举与 teleport 的平台正确性、同 tick 双传送耗时(2.5 闭环,同 ★17 分工)。

---

*施工计划版本:v1.0(2026-10-16,由统筹规划生成;各阶段内容经并行编写 + 对抗式评审修订;★v1.3 增补 §11;★v1.4 增补 §12;★v1.5 增补 §13;★v1.6 增补 §14;★v1.7 增补 §15;★v1.8 增补 §16;★v1.9 增补 §17)*
