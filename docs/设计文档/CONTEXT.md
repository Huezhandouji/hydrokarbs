# CONTEXT.md — 领域术语表(HydroKarbs MiniGame Framework)

> HydroKarbs MiniGame Framework · v1.15(现行,2026-11-11 修正案 33 号/ADR-0021:会话控制句柄 SessionHandle 落地;v1.14 于 2026-11-06 修正案 32 号/ADR-0020:API 版本协商机制退场;v1.13 于 2026-10-30 修正案 31 号/ADR-0019:会话模式实例不变式与缺省切换管线退场;v1.12 于 2026-10-29 修正案 30 号/ADR-0018:JoinDecision 二值化与 SPECTATE 退场;v1.11 于 2026-10-28 修正案 29 号/ADR-0017:属性袋纯容器回归与声明词料三件退场;v1.10 于 2026-10-28 属性袋退场与 MapDefinition 三字段定案,28 号;v1.9 于 2026-10-28 切换与解散的世界清场,27 号;v1.8 于 2026-10-28 地图声明按路径直交 + MapDefinition 瘦身,26 号;v1.7 于 2026-10-28 能力标签机制退场,25 号;v1.6 于 2026-10-27 显式注册收敛,24 号;v1.5 于 2026-10-27 REUSE_DIRTY 副本随会话解散销毁,23 号;v1.4 于 2026-10-27 API 面收敛与地图注册通道收敛;v1.3 于 2026-10-25 框架基础设施瘦身与配置中立化;v1.2 于 2026-10-23 地图注册通道;v1.1 于 2026-10-23 框架独立插件化拆分与 API 面扩充;v1.0 于 2026-10-22 自 SHDFMatch 设计案 v4.1 剥离玩法重构;玩法实现见 Shdf具体玩法设计案/)
> 本文件只放术语与定义,不放实现细节。源自上游 v4.1 设计案(同源复制后按 v1.0 剥离玩法重构,上游代号见上方版本行与本表「代号」节;v1.1 修正案见 19/ADR-0007;v1.3 修正案见 21/ADR-0009;v1.4 修正案见 22;v1.5 修正案见 23;v1.6 变更见 24 号修正案;v1.7 变更见 25 号修正案;v1.8 变更见 26 号修正案;v1.9 变更见 27 号修正案;v1.10 变更见 28 号修正案;v1.11 变更见 29 号修正案;v1.12 变更见 30 号修正案(JoinDecision 二值化与 SPECTATE 退场,ADR-0018);v1.13 变更见 31 号修正案(会话模式实例不变式与缺省切换管线退场,ADR-0019);v1.14 变更见 32 号修正案(API 版本协商机制退场,ADR-0020);v1.15 变更见 33 号修正案(会话控制句柄 SessionHandle 落地,ADR-0021)——正文落盘前以施工计划 README 横幅为准;属性袋纯容器回归与声明词料三件退场见 docs/adr/0017 与 29 号修正案);本文件夹的 README 有最终解释权。SHDF 扩展线术语见节题框注。

## 代号

| 术语 | 定义 |
|---|---|
| **HydroKarbs MiniGame Framework(简称 **HKF**;v1.0 / v1.1 / v1.2 / v1.3 / v1.4 / v1.5 / v1.6 / v1.7 / v1.8 / v1.9 / v1.10 / v1.11 / v1.12 / v1.13 / v1.14 / v1.15)** | 本术语表所属框架的代号:自 SHDFMatch 设计案 v4.1 剥离全部具体玩法重构而成的纯框架(v1.0);v1.1(2026-10-23,框架独立插件化拆分与 API 面扩充,见 19 与 docs/adr/0007);v1.2(2026-10-23,地图注册通道,见 20 与 docs/adr/0008);v1.3(2026-10-25,框架基础设施瘦身与配置中立化,见 21 与 docs/adr/0009:持久化与表现层出框架、发行配置零玩法缺省、启动建会话特性删除与会话创建权移交宿主);v1.4(2026-10-27,API 面与地图注册通道收敛,见 22:两参 createSession 重载删除、建会话公共面唯一形态、配置文件声明地图特性删除与地图注册通道唯一);v1.5(2026-10-27,世界副本复用窗口 = 会话生命周期,见 23:REUSE_DIRTY 副本目录在其所属会话解散后全部销毁,窗口关闭即回收);v1.6(2026-10-27,显式注册收敛,见 24 号修正案:模式/会话组件改经 `HydrokarbsApi` 显式注册四法,ServicesManager 提供方通道退场,`SessionComponentProvider` SPI 删除,API_VERSION 4→5);v1.7(2026-10-28,能力标签机制退场,见 25 号修正案:字符串能力标签机制全链删除(注册表/A2 校验/能力两法/各声明分量),外部能力 = 会话组件桥接(requires/requiresOptional 类型化软依赖),API_VERSION 5→6);v1.8(2026-10-28,地图声明按路径直交 + MapDefinition 瘦身,见 26 号修正案:「模板寄居 `maps/templates/`」约定退场、`MapDefinition` 收为 `(worldDirectoryPath, metaAttributes)` 两分量、tags/处置策略走约定键、id = 声明目录名路径派生、`MapInfo` 收缩 `(id, tags, resetStrategy)`、API_VERSION 6→7 方法面零变化;该两分量形状随后由 28 号修正案/ADR-0016 定案为三字段 `(worldDirectoryPath, resetStrategy, Object metadata)`(不透明词料 + 按引用冻结、标签经单方法约定提取),该口径又由 29 号修正案/ADR-0017 取代为 `(worldDirectoryPath, resetStrategy, AttributeBag metadata)` + `MapDefinition.TAGS`/`tags()`);v1.11(2026-10-28,属性袋纯容器回归与声明词料三件退场,见 29 号修正案/ADR-0017:`AttributeBag` 以纯容器形态回归、28 号的不透明词料与约定提取面全删、`MapDefinition.TAGS` + `tags()` 唯一标签读口);v1.12(2026-10-29,JoinDecision 二值化与 SPECTATE 退场,见 30 号修正案/ADR-0018:`JoinDecision.Kind` 收缩为 `ADMIT`/`REJECT` 二值,`SPECTATE` 全库退场(观战安置非引擎词汇、归组件层),引擎行为零变化、方法面零变化、API_VERSION 维持 7);v1.12(2026-10-29,JoinDecision 二值化与 SPECTATE 退场,见 30 号修正案/ADR-0018:`JoinDecision.Kind` 收缩为 `ADMIT`/`REJECT` 二值,`SPECTATE` 全库退场(观战安置非引擎词汇、归组件层),引擎行为零变化、方法面零变化、API_VERSION 维持 7);当前版本 **v1.15(2026-11-11,会话控制句柄 SessionHandle 落地,见 33 号修正案/ADR-0021——`HydrokarbsApi` 冻结面 17→14 方法:`switchMode(sessionId,…)`/`joinSession`/`leaveSession` 三法迁句柄(见「会话控制句柄」条),`sessionOf`/`session`/`sessions`/`createSession` 改产句柄,「id 不存在」自 future 内 `Rejected` 前移为解析点 `Optional.empty()` 同步早失败;引擎与 `SessionService` 公开面零改动)**;v1.14(2026-11-06,API 版本协商机制退场,见 32 号修正案/ADR-0020——`HydrokarbsApi.API_VERSION` 常量与 `MatchModeProvider#requiredApiVersion()` 声明删除、消费方首行收敛为 api 在场检查;方法面零变化、版本数字轴 1~8 终结);v1.13(2026-10-30,会话模式实例不变式与缺省切换管线退场,见 31 号修正案/ADR-0019——出厂空模式 `EmptyMatchModeFactory`(id=`limbo`)经 SPI 注册兜底,`session.fallback-mode` 默认 `"limbo"`;缺省去向管线退场:`MatchModeMeta` 三分量、`MatchModeInfo` 八分量、无显式去向一律回落回退(单通道);回退链单点收敛 `fallbackSwitch`(无效回退/被拒/异常 → SEVERE + 即时解散);方法面零变化、API_VERSION 7→8);行文简称 **HKF**(HydroKarbs MiniGame Framework 的缩写;插件名 `HydroKarbs`、类名 `HydroKarbsPlugin`/`HydroKarbsAssembly` 与包根 `com.hydrokarbs` 是标识符,不是简称,不随本条变化)。装配类名相应为 HydroKarbsAssembly / HydroKarbsPlugin,框架 Java 包根为 `com.hydrokarbs.*`(D13,旧根 `com.shdf.match` 废止)。具体玩法(大厅/四模式)与角色系统接入(SHDF 宿主扩展)的实现见 Shdf具体玩法设计案/;SHDFMatch 为历史代号,存档于 17/18 与 docs/adr/,不改。 |

## 交付形态(v1.1)

| 术语 | 定义 |
|---|---|
| **框架插件(Framework Plugin)** | 独立插件 `HydroKarbs`:L2 引擎 + api/ + L1 通用桥(config/world)+ L3 通用组件 + 通用 listener/command。一个 Bukkit 插件、一个主类 `com.hydrokarbs.HydroKarbsPlugin`(机制上仍恰有一个主类,12 §12.1 不变);形态 B 产物边界见 12 §12.1 追加块。(**★v1.3**:原「L1 通用桥含 persistence / L5 数据 / render」已出框架——`bridge/persistence`、`data/**`、`render/**`、`component/display/**` 不在框架基线,21-D1/D2;持久化归宿主线自建,表现层见 Shdf具体玩法设计案/10)。 |
| **玩法插件(Gameplay Plugin / 宿主插件)** | 消费框架的插件(如 SHDF):`MatchModeProvider` 模式工厂、私有组件、宿主桥接(如 `bridge/role`)、玩法命令与监听;`plugin.yml: depend: [HydroKarbs]`;独立顶级包根(宿主 = `com.shdf.*`,旧根 `com.shdf.match` 不复用,D13)。 |
| **伴随插件(Companion Plugin)** | 消费框架的第三方插件(13 §13.9 案②的既有形态):只消费 `HydrokarbsApi` 公共面——完整权限面见 07 §7.5(查询+控制+成员+事件+会话定制;★v1.7:原「能力」项随能力标签机制退场,25 号);v1.1 起经扩充后的 `HydrokarbsApi` 拥有完整执行权(19-§1 术语表)。 |
| **API 编译面(API Surface)** | 玩法/伴随插件 compileOnly 依赖框架 jar 时**允许 import 的白名单包集**(19-D6;清单落 07 §7.2 与 7.5 施工文档);由 `ApiSurfaceTest` 双断言冻结(★44:HydrokarbsApi 签名零非白名单类型 + 白名单公开签名快照只增不改),与 `HydrokarbsApiSurfaceTest`(★19)互补。永不导出:`SessionService(Impl)`、`EngineTicker`、`WorldPort`、`ServiceRegistry` 族、`ComponentRegistry` 族、`bridge/**`、各 Impl。(**★v1.3**:原反面词表中的 `PersistencePort` 与 `data/**` 为历史口径——持久化已整体出框架基线,该包不存在,21-D1。) |
| **API_VERSION(★v1.14 历史口径,已退场——新代码禁用)** | 原 ★v1.1-D7 版本协商机制的 API 面版本常量:**★v1.14/32 号(ADR-0020)已整体退场**——运行期版本对照是自我比较(三方同源,真正的 `NoSuchMethodError` 崩点不经过版本数字),兼容性陈述改由版本链文档 + 冻结测试(★19/★44)+ maven 坐标承载;消费方 `onEnable` 首行收敛为 api 在场检查。历史沿革(数字轴已终结):v1.0 = 1,v1.1 = 2,v1.2 = 3,v1.4 = 4,v1.6 = 5,v1.7 = 6,v1.8 = 7,v1.13 = 8,只增不减;玩法/伴随插件 `onEnable` 首行检查,低于要求 → SEVERE + `setEnabled(false)`(拒载不崩服);破坏性变更必须升版本并至多一个次要版本双轨过渡(One-Version Rule;★v1.4/★v1.6/★v1.7/★v1.13 四次破坏性收敛均经用户裁决未设双轨窗口)。(**★v1.3**:v1.3 不改任何 API 方法,API_VERSION **维持 3**;07 查战绩面随 R1 悬置,回填裁决见 21 §6-Q1。)(**★v1.4(22 号)**:两参 `createSession(String,String)` 重载删除(破坏性收敛,未设双轨窗口),API_VERSION 3 → **4**;建会话公共面仅剩 `createSession(SessionRecipe)` 唯一形态;★19/★44 冻结测试与版本常量随本裁决合法更新。)(**★v1.6(24 号)**:显式注册收敛——`SessionComponentProvider` SPI 删除与 ServicesManager 提供方通道退场为破坏性变更,未设双轨窗口,API_VERSION 4 → **5**;冻结面 15 → 19 方法,★19/★44 冻结测试(FROZEN 19 行、BASELINE 重录)随本裁决合法更新。)(**★v1.7(25 号)**:能力标签机制退场——能力标签两法 `registerCapability`/`capabilities` 删除为破坏性变更,未设双轨窗口,API_VERSION 5 → **6**;冻结面 19 → 17 方法,★19/★44 冻结测试(FROZEN 17 行、BASELINE 重录)随本裁决合法更新;外部能力 = 会话组件桥接(★21/★60)。)(**★v1.8(26 号)**:地图声明按路径直交 + MapDefinition 瘦身——声明形状侧破坏、方法面零变化,API_VERSION 6 → **7**;FROZEN 17 行不动、BASELINE 随 MapInfo/MapDefinition/AttributeBag 形状合法重录。)(**★v1.10/28 号(ADR-0016,2026-10-28)**:属性袋退场与 `MapDefinition` 三字段定案——**API_VERSION 维持 7**(方法面零变化,破坏性在声明形状侧);★19 FROZEN 17 行不动、★44 BASELINE 随声明形状合法重录(背书 = ADR-0016);该形状在同一 v7 窗口内二次变更,版本轴不区分。)(**★v1.11/29 号(ADR-0017,2026-10-28)**:声明词料容器化——`MapDefinition` 第三分量由 `Object` 改为 `AttributeBag` 纯容器,`API_VERSION` 继续维持 **7**(方法面零变化);★19 FROZEN 17 行不动、★44 BASELINE 随容器类型/成员合法重录;同一 v7 窗口内第三次声明形状变更,版本轴不区分。) |
| **双形态交付(G1/G2)** | 形态 A(单 jar,现状:框架+宿主同 jar;宿主代码按白名单纪律写,仅 L0 装配/监听/命令三处豁免)与形态 B(双 jar 物理拆分);同一套源码与同一验收集,形态 B 是超集交付、不是分叉。两道闸门:**G1** = API 面扩充并在形态 A 下彩排全绿(★41~★46/★51~★55,含 ★v1.2 地图注册);**G2** = G1 全绿**且**首个真实跨 jar 消费者出现(A13)后执行物理拆分(★47~★50;★50 断言双形态等价)(19-D11)。 |
| **SessionRecipe(会话配方)** | ★v1.1-D14 通道二:`createSession(SessionRecipe)` 携带 `extraComponents`(单次贡献会话组件);与已收录贡献合并后统一 `verifyDependencies`,查重/缺依赖 → `createSession` 响亮失败。`engine.session.SessionSpec` 保持内部(recipe→spec 由框架转换,外部不见)。(**★v1.3-D5**:「L0 拓扑表」废止——`SessionSpec` 由宿主构造传入 `create`,合并序 = 宿主组装的 spec.sessionComponents → provider → recipe;合并机制与冻结面签名不变,`initialModeId` 仍由调用方填。)(**★v1.4(22 号)**:两参重载删除——本配方成为公共 API 建会话的**唯一形态**;两参旧载见「历史术语」表。)(**★v1.6(24 号)**:合并序中段的「provider 贡献」现指经 `registerSessionComponents` 显式注册收录的贡献(注册序)——原 `SessionComponentProvider` SPI 收录口径见「历史术语」表。) |
| **SessionComponentProvider(会话组件贡献者)** | ★v1.1-D14 通道一:插件(宿主/伴随)经 ServicesManager 注册的全局默认贡献;框架 `ServiceRegisterEvent` 收录时**干跑合并校验**(查重/依赖),坏 provider 拒收录(SEVERE 点名)、会话照常;收录后所有后续会话装配合并其贡献;provider 插件 disable 后自后续装配摘除(已装配会话不受影响)。合并序 = 宿主组装的 spec.sessionComponents → 各 provider → recipe.extra;模式仍无权贡献(v4-D4 不变),无运行期增删。(**★v1.6(24 号):本词条整体退场**——SPI 接口随 ServicesManager 提供方通道退场删除;全局贡献改经 `HydrokarbsApi#registerSessionComponents(Plugin, specs)` 显式注册:干跑合并校验失败**响亮上抛**给调用方(注册闸只有一份,原「SEVERE 拒收录、会话照常」口径随通道退场)、同一提供方重复注册 = 幂等重收录、摘除 = `unregisterSessionComponents`;定义细节移「历史术语」表。) |

## 会话层

| 术语 | 定义 |
|---|---|
| **Session(会话)** | 跨模式常驻的玩家容器。★v4:职责 = **内核机制 + 会话级组件**——名册、连接追踪、状态机、切换、世界机制仍为内核机制并保持权威,策略与行为可下放为会话级组件。进程内概念,不落库。 |
| **SessionMember(会话成员)** | 会话级成员身份:uuid、连接态、加入时间。刻意不含 team/role/state——那些是实例概念。 |
| **SessionRoster(名册)** | 一个会话全体 SessionMember 的登记簿。模式切换时名册**原地位移**,不搬数据。 |
| **SessionService** | 框架内负责会话生命周期与模式切换的内核服务。`switchMode` 是它的一等方法。 |
| **Dissolve(解散)** | 会话的终结:成员传回主城、释放世界(★v1.5:该会话名下全部世界副本一并销毁——复用窗口随会话解散关闭,23 号)、从注册表摘除。与"实例结束"是两回事。(**★v1.9(27 号)**:名册传送之后的 finally 内、销毁名下副本之前有**世界清场口**——`hubWorldClearer` 把地图世界内遗留人员传往主世界出生点,「先名册后世界」,见「世界清场」词条) |
| **拓扑(Topology)** | 服务器部署会话的方式:rotation(单会话轮换)或 arena(多竞技场并行)。引擎不感知。(**★v1.3-D5**:不再是框架配置概念——`session.topology` 键删除、`HydroKarbsAssembly` 拓扑装配废止,**拓扑 = 宿主调几次 `SessionService.create(SessionSpec)` 的次数与参数**(会话创建权移交宿主);引擎零感知口径不变,零会话起服为合法状态。) |
| **SessionComponent(会话级组件)** | ★v4:与实例级 MatchModeComponent 并列的第二挂载级,会话级行为单元基类。**每会话一份**、跨模式切换存活,随 dissolve 全灭;会话的策略与行为可下放于此(机制仍在内核)。 |
| **SessionServices(svc)** | ★v4:会话组件的服务集 record,两成员封闭:(self, components)。构造期一次性注入,纪律与实例级 MatchModeInstanceServices 一致。 |
| **SessionPort(self)** | ★v4:会话自面,会话级端口的落点:id/members/tick/events/currentInstance/later + **requestSwitch(target, payload)** / **requestDissolve(reason)**(会话级出局口,镜像实例级的 switchTo()/end();★ADR-0016/0017:旧属性袋机制退场,`attrs()` 删除)。 |
| **MatchModeInstanceAccess(实例访问面)** | ★v4:会话侧的**只读**实例访问面(无写口):实例信息、参赛者、实例组件的只读查询;无实例或切换间隙为空,**不得缓存实例组件引用越过切换**。 |
| **SessionView(会话视图)** | ★v4:实例侧的**只读**会话视图(经 MatchModeInstancePort 的 session() 取得):会话信息与**会话组件的只读查询**——模式接入会话级能力的通道。 |

## 模式声明层(v3 重构核心;v3.1 加 Match 前缀)

| 术语 | 定义 |
|---|---|
| **MatchMode(模式聚合根)** | 模式的**纯数据聚合**:id/名称/描述/图标/mapTags/MatchModeMeta + 唯一有序组件描述符表。★ **零行为**——仅有的两个方法是装配期的 `verifyDependencies()` 与 `createInstance()`。Builder 产出,build 后不可变。 |
| **MatchModeMeta** | 模式元数据 record:**`(recordsHistory, minParticipants, maxParticipants)` 三分量**(★v1.13/31 号:缺省去向分量 `defaultTransition` 与嵌套枚举 `DefaultTransition` 随缺省去向管线退场删除——无显式去向的结束一律回落 `session.fallback-mode` 单通道)。全是数据;v3.1 起无预设常量,各模式工厂逐字段自建。★v3.2:joinPolicy 字段已删(下放为 JoinControlComponent,§3.2.1)。(★v1.7:原首分量 `requiredCapabilities` 随能力标签机制退场删除,25 号;现入「历史术语」表。) |
| **EmptyMatchMode(出厂空模式)** | ★v1.13/31 号:框架**唯一**的出厂模式(`EmptyMatchModeFactory`,id = `limbo`,保留 id)——零组件、`mapTags` 空 = 通配(仍受会话地图池过滤)、meta `(false, 0, MAX_VALUE)`(force 只豁免人数下限、上限永不豁免);经本 jar SPI 清单注册,不在任何提供方 modeClaims(第三方 `unregisterMode` 越权响亮拒,天然防摘);唯一职责 = **回退落点**,保证「会话任何时刻有一个模式实例」。非玩法模式:零配置面(`modes/limbo.yml` 为注释-only 文件,仅消除缺失告警)。 |
| **Capability(能力标签)**(**★v1.7 历史口径**) | 外部插件能力标签(字符串):MatchModeMeta.requiredCapabilities 声明,开局期校验;所需标签缺席时,声明它的模式被拒、其余模式照常;引擎不认识任何具体标签(v3.1);机制为框架口径,具体标签与注册方为 SHDF 扩展线词汇。**★v1.7(25 号):本词条随能力标签机制全链退场为历史口径**——外部能力新形态 = **会话组件桥接**:提供方(或宿主自持防腐层)经 `registerSessionComponents`/`SessionRecipe` 贡献类型化桥接会话组件,消费方组件 `requires`/`requiresOptional` 类型化声明依赖(装配期响亮、运行期 quarantine;软依赖 = `requiresOptional`,★21/★60);字符串标签与 enum Capability 一并加入「历史术语」废止名单,新代码禁用。 |
| **MatchModeComponent.Specification(装配描述符)** | 声明"这个组件怎么造":工厂、requires/requiresOptional 依赖类型、providedType(供给面)、checkMap(地图适配)。freeze() 产出不可变快照,同一 Spec 跨模式共享安全。 |
| **ComponentEntry** | 描述符表条目:(工厂, 描述符类型, 提供类型, 必需依赖, 可选依赖) 的快照。不持有 Spec 对象。 |
| **MatchModeProvider** | SPI 工厂接口 `{ id(); MatchMode build(cfg) }`(**★v1.14/32 号:原第三法 `default int requiredApiVersion()`(★v1.1-D7 版本协商声明)已随版本协商机制退场删除,SPI 面收敛两法**)。聚合根是 final 类;build 后不可追加组件(A9)。(**★v1.6(24 号)**:不再是跨插件的 ServicesManager 注册面——降格为 `HydrokarbsApi#registerMode(Plugin, factory)` 的**载荷**:第三方 `depend` 框架、`load(HydrokarbsApi)` 后在 onEnable 以它显式注册模式(注册闸 build→verifyDependencies→id 查重全响亮);本 jar 出厂模式仍经 ServiceLoader(`META-INF/services`)发现。) |
| **Lobby(大厅)** | 示例模式(SHDF 提供):等人、投票、倒计时;用 Builder 声明的普通聚合根,零特权、可整体替换。(SHDF 扩展线示例,实现见 Shdf具体玩法设计案/04-大厅模式) |

## 实例运行层

| 术语 | 定义 |
|---|---|
| **MatchModeInstance(模式实例)** | 一次模式运行(俗称"一局")。**容器**:按描述符表造组件、按声明序派发、提供端口、回收登记资源。不持有玩法状态(「状态唯一」)。 |
| **MatchModeComponent(模式组件)** | 行为单元基类:id + svc(构造期注入),生命周期 awake/start/update/onEvent/onHandoff/stop。所有玩法状态住在组件里,组件是状态唯一主人并提供强类型读口。 |
| **MatchModeInstanceServices(svc)** | 组件的服务集 record,三成员封闭:(self, components, modeInfo)。照抄角色系统 ComponentServicesPort 纪律;不为任何领域增设转发成员。 |
| **MatchModeInstancePort(self)** | 实例自面,框架「基础端口」的落点:participants/tick/handoff/map/cfg/events/later + **end(EndResult)** / **switchTo(target, payload)** + ★v4 新增 **session()**(所属会话的只读视图 **SessionView**;★ADR-0016/0017:旧属性袋机制退场,`attrs()` 删除)。 |
| **Participant(参赛者)** | 成员在某次实例内的视图:uuid、生命周期标记(★ADR-0016/0017:实例 attrs 随旧属性袋机制退场删除)。team/role 归组件所有,经组件读口查询。 |
| **状态唯一** | 纪律:玩法状态只住在一个组件里,容器与其他组件不得复制持有;跨组件读写走状态主人的读口。 |
| **JoinControlComponent(入场控制组件)** | ★v3.2:决定会话成员是否成为当前实例的参赛者(`ADMIT`)或被拒(`REJECT`)。实现引擎端口 `JoinAdmission`,在「切换 B4 初始摆放」与「运行中 join」两个触发点被 SessionService 查询(组件声明序第一个实现者);**不挂 = 默认全接纳**。★v1.12/30 号(ADR-0018):`JoinDecision.Kind` 已收缩为二值,**观战安置非引擎词汇、归组件层**(组件在 `ADMIT` 之后用自己的词汇自理)。原 MatchModeMeta.joinPolicy 枚举的下放(§3.2.1)。 |

## 会话级组件(v4)

| 术语 | 定义 |
|---|---|
| **会话级组件(SessionComponent)** | ★v4 新增的挂载级(两级挂载,仅两级):会话级行为单元基类,生命周期随 Session(装配期创建 → dissolve 全灭),由**会话装配**声明(不按模式)。 |
| **每会话一份** | 会话级组件在会话生命周期内是单例:同一会话内一切查询恒返回同一实例;**跨会话不共享**(arena 下 N 个会话各持一份);共享的只能是不可变声明(Spec/Snapshot),有状态实例永不跨会话共享。 |
| **跨级只读** | 实例组件与会话组件按接口**互相只读**查询(实例侧经 SessionView,会话侧经 MatchModeInstanceAccess);**写走端口**——不得写入对方,干预只能走端口(实例级 end()/switchTo(),会话级 requestSwitch()/requestDissolve());跨级引用不得跨切换缓存。 |
| **判据两问** | 会话行为的归属判据:问一——它坏了会话会失守底线(名册错/世界漏/切换不原子)吗?是 → 内核机制;问二——服务器主想要另一种做法吗?是 → 会话级组件(策略)。 |
| **EmptyArenaComponent(空场解散策略)** | ★v4 第一波下放的会话策略:空场持续到设定时长即请求解散(经 requestDissolve);成员回归即重置计时。装配按拓扑:**rotation 不挂 = 永不解散;不挂 = 默认**(与大厅零配置哲学一致)。(**★v1.3-D5**:「装配按拓扑」的框架侧分支废止——挂不挂、`after` 给多少,由**宿主组装 `SessionSpec.sessionComponents` 时声明**(原 `session.dissolve-after-empty-seconds` 键删除);「不挂 = 永不解散」口径不变,★31 机制零改动。) |

## 进程级服务(v4.1)

| 术语 | 定义 |
|---|---|
| **Service(进程级服务)** | ★v4.1 新增的第三类对象,与组件并列:进程生命周期(onEnable 装配 → onDisable 关停),不随任何 Session/Instance 生灭。与组件三条硬边界:①不进组件描述符表——svc().components() 查不到它;②无五钩子派发、不占 tickAll 任何一步(自行经 ServiceLifecycle#start 订阅事件驱动);③与组件/模式双向不得 import(见「服务与组件互不可见」)。归属判据(决断一三问版第四行):**状态必须跨会话聚合,或本就是进程级机制/外部世界翻译** → 进程级服务(全服账本、全局看板、配对队列、经济适配)。 |
| **ServiceRegistry(两阶段装配容器)** | ★v4.1:服务的统一装配容器。阶段一 `install` 仅声明(重复 id/重复类型响亮抛,点名先到者;seal/shutdown 后拒);阶段二 `seal` 依赖校验 → 拓扑排序 → 按序 factory 创建 + start(★v1.7:原尾步「注册能力标签」随标签机制退场删除,25 号);任一工厂/start 抛 → **回滚已启动服务(逆序 stop)再抛**,绝不停在中间态;`shutdown` 逆序 safeStop、幂等(★v1.7:原「摘除能力标签」随机制退场删除,25 号)。**消费面五方法封顶**(install/seal/get/require/shutdown)——上帝注册表是陷阱,扩需求 = 新独立机制。 |
| **ServiceSpec(声明式描述符)** | ★v4.1:声明「这个服务怎么造」:`id/type/factory/requires/requiresOptional`(★v1.7:原 `capabilities` 字段随能力标签机制退场删除,25 号),Builder 构建,构建期校验 id 非空。与组件 Specification 的两处刻意差异:**无 freeze()**——服务 Spec 由 L0 现场构建、只被一个注册表消费,无共享串改问题;**工厂收 ServiceEnv 而非 svc**——服务不持服务集 record,依赖在创建期经 env.dep() 注入(拓扑序保证就绪)。 |
| **ServiceEnv(工厂取物口)** | ★v4.1:服务工厂的取物口,零内容词汇:dep(必需依赖,拓扑序保证就绪;取不到 = 声明与取用不符,响亮抛)/ depOpt(可选依赖,缺席 empty 不抛)/ plugin(平台原语:日志/调度/数据目录)/ cfg(惰性只读配置面 ConfigView)。 |
| **ServiceLifecycle(服务生命周期)** | ★v4.1:可选生命周期接口,start/stop **默认空实现**;纯无状态服务(Facade 类)不必实现;实现者须保证 **stop 与 start 严格对称**。 |
| **ConfigView(惰性只读配置面)** | ★v4.1:ServiceEnv#cfg() 返回的惰性只读配置面,由 L1 ConfigRepository 实现;/reload 后自动读新值。 |
| **服务↔会话三通道** | ★v4.1:服务触达会话只有三条通道,无第四条:①**订阅 SessionEventBus**(SessionCreated/MemberJoined/Left/ModeSwitchStarted/Completed/SessionDissolved/SessionComponentQuarantined——感知生灭的官方通道,订阅句柄在服务 stop 中注销);②**只读快照**(HydrokarbsApi.sessions() 不可变 record;★v1.15/33 号:sessions() 改产 `SessionHandle` 列表——句柄为不可变定位器,快照经句柄 `info()` 现取(消亡 → empty),不持活引用的口径不变;引擎内可经 SessionService 遍历 + MatchModeInstanceAccess 只读查询;不得持有活实例/活组件引用跨切换,现取不缓存);③**SessionService 受控命令**(switchMode/dissolve/createSession——服务属外部调用方(切换来源②),与 /sm 命令同权;force 是显式选择,走 review 条款)。★v1.1-D10 注:通道③受控命令清单不变;跨 jar 外部调用方经 `HydrokarbsApi` 的成员操作不构成第四通道(调用方阵营不同,见 19-D2/D10)。 |
| **服务与组件互不可见** | ★v4.1:双向冻结(ArchUnit):组件/模式不得 import engine/service/** 与 service/**;服务不得 import mode/** 与 component/**。服务不是第三级组件挂载级(组件挂载仅两级,A10 原文保留);MatchModeInstanceServices(3 成员)与 SessionServices(2 成员)**不加成员**——组件要消费基础设施走既有先例(L1 适配器 + 包装组件;同款具体先例属宿主扩展,见 Shdf具体玩法设计案/09),不是加 svc 成员。 |

## 引擎三迁组件(v3 从引擎降级为组件的三个机制)

| 术语 | 定义 |
|---|---|
| **PhaseComponent** | 阶段状态机组件:持有阶段表(Timed/Await)数据,推进/转场/计时/PhaseChanged 事件(事件类型住本组件包)。引擎对"阶段"零认知。 |
| **EndGateComponent** | 结束判定门组件:持有 EndCondition 列表按 priority 求值,命中即 `svc().self().end(new EndResult(intent))`。结束是组件的主动声明,不被框架轮询。 |
| **EndCondition / GateVerdict / MatchOutcome** | 判定器(纯函数,产出 GateVerdict)/ 判定产物(结局 + 可选去向)/ **玩法结局词汇**(Kind: WIN/DRAW/NEXT/ABORTED + 胜者 + reason,住 component/endgate——引擎零认知,v3.1 从引擎 EndResult 拆出)。 |
| **EndResult(引擎侧)** | 框架的结束货币:**仅携带可选 TransitionIntent(下一站)**,不含胜负/原因(v3.1 瘦身)。 |
| **RuleBookComponent** | 声明式规则簿组件:持有 Rule(trigger/when/then 纯函数)列表,事件驱动求值。可选件。 |

## 切换与交接

| 术语 | 定义 |
|---|---|
| **SwitchMode(模式切换)** | 框架 API:旧实例组件 onHandoff + close → 名册原地位移 → createInstance + awakeAll + startAll,主线程单 tick 原子,三级回退。 |
| **Handoff(交接)** | 模式间传递数据的类型化键值袋。**实例数据**出实例的**唯一合法通道**(★v4-D10:跨模式数据另可寄居会话级组件——两通道口径;会话组件状态随 dissolve 全灭)。命名空间 = 写入方自己的 id(模式 id / 组件 id);★v3.1 框架不写任何键(HandoffKeys 已删),标准键随主人:`VoteComponent.HANDOFF_RESULT`、`StatsComponent.LAST_SUMMARY`。 |
| **TransitionIntent(转移意图)** | "下一站去哪"的指令:ModeSelection + 携带的 Handoff。由 EndGate 的判定器或外部调用方产出。 |
| **Fallback Mode(回退模式)/ 回退模式单通道** | 回退模式 = 切换装配失败/无显式去向结束时切换去的兜底模式,**配置项**(`session.fallback-mode`),引擎不认识任何具体模式 id。**★v1.13/31 号:回退单通道**——缺省去向管线(`MatchModeMeta.defaultTransition` 三值)退场后,无显式去向的结束**一律**回落本配置项(默认 = 出厂空模式 `limbo`,见「EmptyMatchMode」条);回退链单点收敛 `fallbackSwitch`:回退无效(空串/未注册)或被拒/异常 → SEVERE + 即时强制解散(`FALLBACK_DISSOLVE`),无显式去向的自动切换受最短驻留节流(空串先行即散 → 驻留不足回填重试 → 满后 force 切回退);可改配任意已注册模式 id,该模式不应依赖「无意图结束」自持。(★v1.3/21-D3 旧口径「默认空、框架不设出厂模式,空/未注册 → 跳过一级直接二级 dissolve」自 v1.13 废止,修订为「框架不设**玩法**出厂模式,恰设一个兜底空模式」。) | |
| **Min-dwell(最短驻留)** | 两次自动切换之间的最小间隔(默认 100 tick),防切换风暴。 |

## 角色接入(SHDF 宿主扩展,非框架组成)

> 角色系统接入是 SHDF 宿主扩展,见 Shdf具体玩法设计案/;两层术语(桥接层与角色组件)的定义见该目录 CONTEXT-玩法术语.md「角色接入」节及其 08、09 两篇。框架侧原「与模式声明相关的能力标签机制」已随 ★v1.7 全链退场(25 号,「模式声明层」Capability 条改历史口径并入「历史术语」表)——外部能力 = 会话组件桥接(★21/★60)。

## 世界

| 术语 | 定义 |
|---|---|
| **WorldPort(世界端口)** | 世界的实例化/引用计数/释放/**副本销毁**的端口(engine 内部口,不经 `HydrokarbsApi`)。只有 SessionService 调用;模式与组件无感。(**★v1.5**:端口面 = `acquire`/`release`/`discardSession` 三方法——会话解散经 discardSession 销毁该会话名下全部副本,**复用窗口 = 会话生命周期**(★v1.5-D1,23 号);契约见 03 §3.7。) |
| **世界模板(Template)** | 地图的世界母本——**★v1.8(26 号)新口径:世界目录 = 声明自带 `worldDirectoryPath`**(构造期规范化绝对路径,可指任意位置;不再约定寄居 `maps/templates/<id>/`——该约定根移「历史术语」表)。副本按 (map, session) 实例化。(★v1.5:副本复用只发生在会话存活期——会话解散后名下副本目录全部销毁,见 ResetStrategy 与 Dissolve。) |
| **声明词料(Declaration Metadata)** | ★v1.11/29 号(ADR-0017):`MapDefinition.metadata` 的类型 = `AttributeBag` 纯容器(声明级单写方;`Key<T>(name,type)` 二元组、`Builder` 唯一写侧、`Map.copyOf` 收口、`get/getOrDefault/has` 三读口、`empty()` 共享空实例)。**构造即冻结由容器结构保证**(无公开写口,装配来源 `Map` 的后续改动不跟随);`metadata` 非空——无词料传 `AttributeBag.empty()`。框架只解释 `MapDefinition.TAGS` 一键,其余词料由宿主自定 `Key` 原样携带。 |
| **AttributeBag(纯容器)** | ★v1.11/29 号(ADR-0017):声明词料的唯一容器(定义见「声明词料」条)。**与「历史术语」表里的旧属性袋同名不同物**:旧机制 = 带命名空间/授权表/`attrs()` 的运行期键值袋(已废止);本类 = 零 Bukkit 依赖、无命名空间、无授权表、不参与实例 `close()` 链的纯不可变容器。 |
| **tags()(标签读口)** | ★v1.11/29 号(ADR-0017):`MapDefinition#tags()` = 全框架唯一标签读口——`metadata.getOrDefault(MapDefinition.TAGS, Set.of())`;`TAGS` 缺失或值不是 `Set` 实例 → 空集,空集 = 不被任何模式按标签选中(20-D3 口径)。`MapRegistry#byTag` 与 `MapInfo` 快照只经此口取标签。 |
| **地图注册(registerMap)** | ★v1.2 引入、★v1.4 起为地图输入的**唯一通道**:宿主/伴随插件经 `HydrokarbsApi#registerMap(Plugin, MapDefinition)` 显式注册地图声明、`unregisterMap(Plugin, String)` 摘除;`MapRegistry` 只剩显式注册/摘除两口,**目录顺序 = 注册序**。**地图交成品不交工厂**:`MapDefinition` 纯静态值,直接注册建好的实例合法(与模式必须交 `MatchModeProvider` 工厂相对,20-D1;**★v1.11/29 号(ADR-0017)起:声明即路径 + 三字段 `(worldDirectoryPath, resetStrategy, AttributeBag metadata)`,`metadata` 为构造即冻结的纯容器(非空,无词料传 `empty()`)**)。注册期校验 id 查重(id = **声明目录名,路径派生**;同一路径再注册必撞,重复点名先到者)/**世界目录存在性**(声明自带路径存在且为目录,缺失/非目录响亮抛带完整绝对路径——**★v1.8:模板根约定退场,注册前路径已就位**,14.39);标签 = `MapDefinition#tags()` 唯一读口(`TAGS` 约定键,缺省空集),处置策略 = `resetStrategy` 声明字段;词料容器构造即冻结(注册后无公开写口);卸载走显式 + `PluginDisableEvent` 兜底,**有会话正用 → 摘除被拒**(20-D4 守卫语义);地图声明不经任何 reload(所有权在提供方插件,20-D5);`WorldPort` 世界生命周期不受注册通道影响(20-D7)。(**★v1.4(22 号)**:配置文件声明地图特性删除——原「文件装载之外的第二输入通道」「与文件式共用命名空间」「注册式(地图)」口径废止,文件式装载移入「历史术语」表。) |
| **ResetStrategy(还原策略)** | 副本处置方式:REUSE_DIRTY(会话存活期内打完复用——归零释放保留目录,切换换图亦保留)或 TEMPLATE_RESTORE(归零即删副本、下次 acquire 从声明世界目录重新复制)。(**★v1.5**:两种策略的效力均以**会话存活**为前提——复用窗口 = 会话生命周期,会话解散后该会话名下全部副本**无论策略一律销毁**(★v1.5-D1,23 号);两者词汇与边界清单("打完不重建靠 CleanupComponent 还原""切走删副本重复制")原样。)(**★v1.8(26 号)**:策略词料曾在声明词料里以 `map.reset-strategy` 约定键表达(历史口径;该键与门面已由 29 号退场)——枚举类型与两值不变,消费管线(归零处置/discardSession)零改动。)(**★v1.10/28 号(ADR-0016,2026-10-28)**:`reset-strategy` 约定键从未被生产代码写入,策略回到声明字段——`MapDefinition.resetStrategy`(三字段之一,二参便利构造缺省 `REUSE_DIRTY`);值域两值与消费管线零改动。) |
| **遗留人员(Leftover Personnel)** | ★v1.9(27 号):**物理位于某个地图副本世界内的全部在线玩家**(`World#getPlayers()`),**不以会话名册为界**——名册外滞留者(被外力传入/重连镜像面等)正是既有清场失败的主因;与「名册成员」是超集关系(名册 ⊂ 世界内人员),与「名册内/世界外」(断线宽限期镜像面)互为镜像。清场两口的唯一覆盖面(27 号 §6-N1/N2:重连落位与通用安全态清理不在其覆盖面,均为既有行为原样)。 |
| **世界清场(World Clear)** | ★v1.9(27 号):切换/解散接缝上的**内核清场动作**——把旧(或待销毁)地图世界内的遗留人员传送至出生点(切换 = 目标副本世界 `getSpawnLocation()`;解散 = 主世界出生点,与 `hubRelocator` 同源 `hubSpawn()` 单一口径),保证「世界内有玩家 → unloadWorld false → 保留目录」路径不再触发。平台语义收敛于 `SessionServiceImpl.Builder` 两个 **L0 注入函数**(`worldClearer`/`hubWorldClearer`,落点 `HydrokarbsAssembly`),**不经 `WorldPort`、不经公共 API**;切换双分支 = 同世界复用(判据 = `org.bukkit.World` 引用相等,含首切空值分支)B3.5 先行于组件摆放、异图 B5 finally 殿后(完成事件之后、release 之前);解散 = 「先名册后世界」。**best-effort**:失败 WARN 不阻塞切换/解散;「卸载失败保留目录」(23-D2)仍为最后防线。 |

## 对外

| 术语 | 定义 |
|---|---|
| **HydrokarbsApi** | 本框架对第三方插件的冻结公共面(查会话、控切换、注册模式、查战绩),经 ServicesManager 暴露。(**★v1.3**:「查战绩」面随 R1 悬置——归宿主线消费;框架冻结面 7.1 落地时不含 DB 依赖项,回填裁决见 21 §6-Q1。)(**★v1.6(24 号)**:显式注册收敛——冻结面 19 方法(API_VERSION = 5);模式注册 = `registerMode(Plugin, MatchModeProvider)` 显式法 + `unregisterMode`(只摘自己的 id,摘除触发回退编排);会话组件贡献 = `registerSessionComponents`/`unregisterSessionComponents` 显式法;原 ServicesManager 提供方通道(`MatchModeProvider`/`SessionComponentProvider` 注册)退场——HydrokarbsApi 自身仍经 ServicesManager 暴露,但第三方注册不再走它。)(**★v1.7(25 号)**:能力标签机制退场——冻结面 19 → **17 方法**(API_VERSION = **6**),能力标签两法 `registerCapability`/`capabilities` 删除;外部能力 = 会话组件桥接(经既有显式注册两法与 `SessionRecipe`,消费方 `requires`/`requiresOptional`;★21/★60)。)(**★v1.15(33 号)**:会话控制句柄落地——冻结面 17 → **14 方法**:控制/成员三法(`switchMode(sessionId,…)`/`joinSession`/`leaveSession`)迁 `SessionHandle` 句柄面,`sessionOf`/`session`/`sessions`/`createSession` 改产 `Optional<SessionHandle>`/`List<SessionHandle>`/`SessionHandle`(见「会话控制句柄」条);其余 10 法不动,本接口只留解析口。) |
| **会话控制句柄(SessionHandle)** | ★v1.15/33 号(ADR-0021):单个会话的控制与信息面——**不可变定位器**:只持会话 id 与委托(`SessionService` + 主线程执行器),不持活 `Session`/名册/实例引用,每次操作现解析(「现取不缓存」纪律向控制面的延伸);经 `HydrokarbsApi#sessionOf/session/sessions/createSession` 解析获得,会话消亡后不失效为错误而是走各方法失效语义。五法:`sessionId()`(纯值,消亡后仍可读)/ `info()`(组合式——句柄上唯一信息口,`Optional<SessionInfo>` 现取快照,会话已消亡 → empty;`SessionInfo` record 原样保留)/ `join(UUID)`(引擎裁决原样透传,主线程)/ `leave(UUID, LeaveReason)`(**会话域守卫**:仅当玩家现属会话 == 本句柄会话才移出,否则 no-op;主线程内查后调,无竞态)/ `switchMode(MatchModeSelection, Handoff)`(任意线程发起、Impl 回主线程、future 主线程完成;会话消亡 → future 完成 `Rejected(MODE_NOT_FOUND)`,异常经 future 传播、永不悬挂)。实现体 `SessionHandleImpl` 包私有 final(构造注入);会话快照转换 `snapshot` 单点居实现体(`HydrokarbsApiImpl` 共用——「一份不写两处」)。 |

## 历史术语(已废弃,勿在新代码使用)

| 旧术语 | 替代 | 废止于 |
|---|---|---|
| Match / GameMode / MatchPlan / MatchParticipant / MatchEvent / WinCondition / MatchOutcome | MatchModeInstance / MatchMode(聚合根)/ —(已删除)/ Participant / MatchModeInstanceEvent / EndCondition / MatchOutcome(v3.1 复活于 component/endgate,作内容层结局词汇)+ EndResult(引擎侧,仅去向) | v2 |
| `Mode` 接口的 plan()/rolePolicy()/transition()/validateMap()/requires()/joinPolicy()/recordsHistory() | Builder 参数、MatchModeMeta 字段、组件 Spec 构造数据、Spec#checkMap | v3 |
| `Component`(接口,与 Adventure 文本 Component 撞名)/ MatchContext | `MatchModeComponent`(基类)/ `MatchModeInstanceServices`(svc) | v3 |
| 引擎内 PhaseChain/PhaseNode/RuleEngine/胜负求值 | PhaseComponent / EndGateComponent / RuleBookComponent(L3 组件) | v3 |
| RoleLoadoutComponent | 已并入角色组件的 Loadout facet(v3;见 Shdf具体玩法设计案/09 与该目录 CONTEXT-玩法术语.md) | v3 |
| TransitionPolicy(策略对象) | MatchModeMeta.defaultTransition(纯数据) | v3 |
| GameMode 的 META-INF/services 注册 | MatchModeProvider 工厂注册 | v3 |
| Mode / ModeMeta / ModeComponent / ModeInstance / ModeProvider / ModeRegistry / ModeSelection / ModeConfig / ModeInfo | MatchMode / MatchModeMeta / MatchModeComponent / MatchModeInstance / MatchModeProvider / MatchModeRegistry / MatchModeSelection / MatchModeConfig / MatchModeInfo(消除与 Bukkit GameMode 的撞名) | v3.1 |
| HandoffKeys 框架保留键(VOTE_RESULT/LAST_SUMMARY/RETURN_REASON) | VoteComponent.HANDOFF_RESULT / StatsComponent.LAST_SUMMARY / "为何离开"随 MatchSummary 携带(框架不写交接键) | v3.1 |
| EndResult.Kind 枚举(WIN/DRAW/NEXT/ABORTED)+ winnerTeamId | MatchOutcome(component/endgate);引擎 EndResult 仅 `Optional<TransitionIntent>` | v3.1 |
| enum Capability{ ROLE }、MatchModeMeta.LOBBY/COMPETITIVE 常量 | 字符串能力标签(桥接注册 "role")/ 各模式工厂自建 meta | v3.1 |
| **字符串能力标签机制全链**(v3.1 形态;含 `enum Capability` 同源的 `CapabilityRegistry`、`HydrokarbsApi#registerCapability`/`capabilities` 两法、`MatchModeMeta.requiredCapabilities` 分量、`MatchModeInfo` 快照分量、`SessionComponent.Specification#capabilities` 与 Snapshot 分量、`ServiceSpec.capabilities` 与 seal/shutdown 两端注册、切换 A2 能力校验、`ProviderUnloadListener` 能力兜底块与 `capabilityClaims` 簿记) | **外部能力 = 会话组件桥接**(★v1.7/25 号,用户裁决:字符串契约不严谨):提供方(或宿主自持防腐层)经 `registerSessionComponents(Plugin, specs)`/`SessionRecipe` 贡献类型化桥接会话组件,消费方组件 `requires`/`requiresOptional` 类型化声明依赖——硬依赖缺失在装配期响亮(干跑合并校验/`createSession` 拒绝),运行期降级/quarantine 由桥接组件自身承接;软依赖 = `requiresOptional`(既有机制,★21/★60);字符串域词汇(Handoff 键/配置键等)纪律不受影响 | v1.7(25 号) |
| 切换 B3 角色兜底 clearRole / engine 端口 RolePort、DisplayPort / engine 配置 role-sample-interval | 已删除——清场唯一闸/消费面/采样间隔均已归玩法线组件与其 modes 配置(见 Shdf具体玩法设计案/08、09) | v3.1 |
| `MatchModeMeta.joinPolicy`(OPEN/SPECTATE/CLOSED 入场枚举)/ JoinPolicy 枚举类型 | JoinControlComponent(#24)+ engine 端口 JoinAdmission(ADMIT/SPECTATE/REJECT,§3.2.1);不挂 = 默认全接纳。**★v1.12/30 号注:此后 `JoinDecision.Kind` 已收缩为 `ADMIT`/`REJECT` 二值,`SPECTATE` 退场** | v3.2 |
| `JoinDecision.Kind#SPECTATE` | 随 Kind 收缩退场;「观战」本身是组件侧概念非引擎词汇,观战安置由组件在 ADMIT 后自理 | v1.12(30 号/ADR-0018) |
| `SessionPolicy` 的空场部分(空场判定 / idleTimers 空场) | EmptyArenaComponent(会话级组件,第一波下放);连接追踪与宽限 sweep 仍在内核 | v4 |
| 原(组件单一挂载级)的表述 | 两级挂载:实例级 MatchModeComponent + 会话级 SessionComponent | v4 |
| Java 包根 `com.shdf.match`(12 §12.1 曾注「历史沿用不改」) | 框架 `com.hydrokarbs.*`(主类 `com.hydrokarbs.HydroKarbsPlugin`);宿主另立独立顶级包 `com.shdf.*`(旧根 `com.shdf.match` 不复用) | v1.1(D13) |
| `engine/port/PersistencePort` + `bridge/persistence/PersistencePortImpl` + L5 `data/**`(Database/AsyncExecutor/StatsFlusher/repo/、model/) + build 的 HikariCP/mysql 依赖与 shade + config `database:` 块 + `engine.shutdown-flush-budget-seconds` + `sql/` 资源 | **持久化整体出框架**(21-D1/R1):宿主自建,经自己的组件/服务直连自己的 DB 栈;`StatsComponent` 采集与 `StatsDelta` 出口留框架;★10~★12 命题随宿主线执行;设计资产以 10-数据库存档保全(§10.5 例外留框架) | v1.3(D1) |
| `component/display/**`(DisplayComponent) | **表现层出框架**(21-D2/R2):框架基线移除 `component/display/` 与 `render/` 与 config `display:` 块;组件与渲染器迁宿主(`com.shdf.*` 对应包),数据驱动设计与挂法见 Shdf具体玩法设计案/10-表现层组件.md;施工子阶段 4.3 撤销;P3 组件计数 L3 实数 21→20 | v1.3(D2) |
| `DisplayModel`/`DisplayLine`/`Countdown`/`Progress`(中立表现模型,原住 `com.hydrokarbs.component.display`) | 随 D2 迁 Shdf具体玩法设计案/10-表现层组件.md(标注「自框架 06 §6.2 v1.3 迁入」,原文完整);框架基线无表现层类型 | v1.3(D2) |
| 文件式地图装载(`maps/*.yml` 启动扫描:ConfigRepository.loadMaps / YamlCodec.readMap / MapDefinition.from / MapRegistry.replace 与文件半边、无参构造)+「文件 + 注册双通道」「注册式(地图)」限定口径 | **地图注册通道唯一**(★v1.4/22 号):地图只经 `HydrokarbsApi#registerMap/unregisterMap` 显式注册/摘除,`MapRegistry` 目录顺序 = 注册序;模板目录约定 `maps/templates/<mapId>/`、注册期校验(响亮抛)、正用摘除被拒(20-D4)不变 | v1.4(22 号) |
| **「模板寄居框架数据目录 `maps/templates/<mapId>/`」约定根** + `MapDefinition` 结构分量(displayName/tags/bounds/spawns/regions/interactPoints/metadata/resetStrategy)+ `map/` 结构类(Bounds/RegionSet/NamedRegion/SpawnPoint/InteractPoint;Bounds 从未落码) | **地图声明按路径直交**(★v1.8/26 号):`MapDefinition` 声明直指任意路径、注册前落盘就位;id = 声明目录名路径派生(查重/副本命名/钉图键同源);区域/出生点等结构词料归调用方自理;现行形状 = 三字段 `(worldDirectoryPath, resetStrategy, AttributeBag metadata)`,标签经 `MapDefinition.TAGS` + `tags()` 读取、处置策略 = `resetStrategy` 字段(★v1.11/29 号(ADR-0017);路径直交/id 派生/结构词料自理不变) | v1.8(26 号) |
| **`AttributeBag`(属性袋)/ `AttributeBag.Key`(命名空间 + 名字 + 类型的通用键值袋)+ 各端口/只读面 `attrs()`/`attr()`** | **状态唯一 = 组件字段 + 强类型读口**(弃通用键值袋——零生产写路径);地图声明词料 = `MapDefinition.metadata`(★v1.11/29 号起为 `AttributeBag` 纯容器)+ `MapDefinition.TAGS`/`tags()` 标签读口、`resetStrategy` 声明字段;`Handoff` 为独立的跨模式交接机制,不属同类。**★v1.11/29 号(ADR-0017)注:现役 `AttributeBag` = 声明词料纯容器,与废止的旧机制同名不同物——旧名/旧机制(命名空间/授权表/`attrs()`)仍禁用** | ADR-0016(2026-10-28;旧机制)、ADR-0017(同名纯容器现役) |
| 两参 `createSession(String, String)` 重载(公共 API 建会话) | `createSession(SessionRecipe)`——公共 API 建会话唯一形态(API_VERSION 3→4,★19/★44 冻结测试随裁决合法更新) | v1.4(22 号) |
| `SessionComponentProvider` SPI(全局默认会话组件贡献,经 ServicesManager 注册、ServiceRegisterEvent 收录、坏 provider SEVERE 拒收录会话照常)+ 模式的 ServicesManager 提供方通道(`register(MatchModeProvider.class, …)` + 事件动态收录/摘除、「先启补收录」)+ `listener/ProviderServiceListener` | **显式注册收敛(★v1.6/24 号)**:模式 = `HydrokarbsApi#registerMode(Plugin, MatchModeProvider)`(载荷,响亮闸)+ `unregisterMode`(只摘自己的 id + 回退编排:fallback-mode force 切换 / FALLBACK_DISSOLVE);贡献 = `registerSessionComponents(Plugin, specs)`(干跑校验响亮上抛、幂等重收录)+ `unregisterSessionComponents`;第三方必须 `depend` 框架、`load(HydrokarbsApi)` 后显式登记;disable 兜底四族(能力/模式/地图/贡献,ProviderUnloadListener) | v1.6(24 号) |
| **缺省去向管线**:`MatchModeMeta.defaultTransition` 分量 + 嵌套枚举 `DefaultTransition`(FALLBACK/STAY/ROTATION)+ `MatchModeInfo` 第九分量 + 驱动侧 `defaultTransitionOf` 分派 +「框架不设出厂模式」(21-D3 的 fallback 默认空口径) | **回退模式单通道**(★v1.13/31 号/ADR-0019):无显式去向的结束一律回落 `session.fallback-mode`(默认 = 出厂空模式 `EmptyMatchModeFactory`,id=`limbo`);`MatchModeMeta` 三分量、`MatchModeInfo` 八分量、API_VERSION 7→8;回退无效/被拒/异常 → SEVERE + 即时 `FALLBACK_DISSOLVE` | v1.13(31 号/ADR-0019) |
| `HydrokarbsApi.API_VERSION` 常量 + `MatchModeProvider#requiredApiVersion()` SPI 默认方法 + 框架启动版本日志行 + 注册点版本打点字段 + 消费方 `onEnable` 首行版本对照检查(`api == null \|\| api.API_VERSION < REQUIRED_API_VERSION`) | **API 版本协商机制整体退场**(★v1.14/32 号/ADR-0020):运行期版本对照是自我比较(三方同源,真正的崩点不经过版本数字),兼容性陈述改由版本链文档 + 冻结测试(★19/★44)+ maven 坐标承载;消费方首行收敛为 **api 在场检查**(`api == null` → SEVERE + `setEnabled(false)`,拒载不崩服语义保留);`MatchModeProvider` SPI 面收敛 `id`/`build` 两法;14.36 条目废止(编号空置) | v1.14(32 号/ADR-0020) |
| `HydrokarbsApi#switchMode(String, MatchModeSelection, Handoff)` + `#joinSession(String, UUID)` + `#leaveSession(UUID, LeaveReason)`(api 面会话侧直控三法) | **会话控制句柄**(★v1.15/33 号/ADR-0021):三法迁 `SessionHandle` 五法面——`api.sessionOf(player)`/`api.session(id)` 解析句柄后 `handle.join(...)`/`handle.leave(...)`/`handle.switchMode(...)`;「id 不存在」自 future 内 `Rejected` 前移为解析点 `Optional.empty()`(同步早失败);`leave` 收窄会话域守卫(现属会话 == 本句柄会话才移出,否则 no-op) | v1.15(33 号/ADR-0021) |
