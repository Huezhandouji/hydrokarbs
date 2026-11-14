# ADR-0006:进程级服务层——ServiceRegistry 与组件/内核的三分结构

> **编号注(修正案原文编号 0005,因冲突顺延为 0006)**:本 ADR 在 `设计案/18-v4.1修正案-进程级服务层与ServiceRegistry.md`(§2/§4/§6.3/§12)中原文编号为 **ADR-0005**(`docs/adr/0005-process-service-registry.md`);因 `docs/adr/0005` 已被《架构冻结测试门禁》占用,编号顺延为 **ADR-0006**(本文件)。修正案原文中凡指进程级服务层的"ADR-0005"字样,一律读本文件。

- 状态:已接受(2026-10-21,v4.1 修正案)
- 关联:`设计案/18-v4.1修正案-进程级服务层与ServiceRegistry.md`(D1–D12 全文);ADR-0002(聚合根零行为与引擎纯度)、ADR-0004(会话级组件层)、ADR-0005(架构冻结测试门禁);`设计案/02-核心概念.md` §2.9;`设计案/03-会话与模式切换.md` §3.9;`设计案/12-代码骨架.md` §12.1/§12.2;`设计案/16-路线图与验收.md` §16.2(★34~★40);`施工计划/` 1.6(进程级服务装配)

## 背景(Context)

v4.1 之前,进程级单例散落在 `EngineTicker`/`CapabilityRegistry`/`MatchModeRegistry` 等处,需要**常驻在所有会话之上**的模块没有正式居所。三个驱动(逐条可验收)使现状不可维持:

1. **需要常驻在所有会话之上的模块**——跨会话聚合(全服账本/系列赛)、全局看板、进程级配对队列:**组件两级挂载装不下**(状态按会话隔离、随 `dissolve` 全灭),内核又不该长内容词汇;
2. **常驻行为要有统一纪律**——进程级单例目前散落在 `EngineTicker`/`CapabilityRegistry`/`MatchModeRegistry` 等处,无装配期依赖校验、无统一生命周期、无失败回滚;
3. **接外部系统的正式注册通道**——经济等基础设施能力的接入要能软依赖降级(缺席 = 功能关闭,不是崩溃),并复用 §8.8 能力标签口径。

核心症结:**组件两级挂载装不下跨会话聚合**。A10("组件挂载仅两级,禁止第三级")并未因此被打破——服务不是组件、不挂载,是与组件**并列**的对象类别(详见下文"与既有决策的关系"表)。因此 v4.1 新增"进程级服务层",组件、内核、服务构成三分结构。

## 决策(Decision)

**一句话:v4.1 新增"进程级服务层"——服务(Service)是与组件并列的另一类对象**:不挂进会话/实例、不进组件表、彼此经 `ServiceRegistry` 统一装配(两阶段:`install` 声明 → `seal` 校验+拓扑序创建+启动),经**订阅会话事件、只读快照、SessionService 受控命令**三条通道与会话对话;**组件体系与服务互不可见**,svc 成员数不变。以下 D1~D12 照修正案 §2 全文转录,编号与代码块照原样保留。

### D1 定位:服务不是第三级组件挂载级(A10 不破)

- A10 原文保留:**组件**挂载仅两级(实例级/会话级),禁止第三级组件、禁止兼任。
- 新增第三类对象:**进程级服务(Service)**——进程生命周期(`onEnable` 装配 → `onDisable` 关停),不随任何 Session/Instance 生灭。服务与组件的三条硬边界:①服务不进组件描述符表,`svc().components()` 查不到它;②服务没有五钩子派发,不占 `tickAll` 的任何一步(它自行经 `ServiceLifecycle#start` 订阅事件驱动);③组件与模式**不得 import** 服务类型,服务**不得 import** `mode/**` 与 `component/**`(D6)。
- 归属判据(决断一 v4.1 修订版,两问扩为**三问**,写入 02 §2.8):

| 判定 | 归属 | 例 |
|---|---|---|
| 不需要跨模式存活 | 实例级组件 | 阶段/计分/队伍/结束判定 |
| 跨模式存活,且是机制/状态权威/可靠性底线 | `SessionService` 内核 | 名册、连接追踪、切换算法、世界引用计数 |
| 跨模式存活,且是策略/裁决/聚合/展示,**状态可按会话隔离** | 会话级组件 | 空场解散(已实现);断线宽限等(候选) |
| **状态必须跨会话聚合,或本就是进程级机制/外部世界翻译** | **进程级服务(★本版新增)** | 全服账本、全局看板、配对队列、经济适配 |

### D2 ServiceRegistry:两阶段装配契约

```java
public final class ServiceRegistry implements AutoCloseable {
    public <S> void install(ServiceSpec<S> spec);   // 阶段一:仅声明。重复 id / 重复类型 → IllegalStateException(点名先到者);seal/shutdown 后 install 抛
    public void seal();                             // 阶段二:依赖校验 → 拓扑排序 → 按序 factory(ServiceEnv) 创建 → start → 注册能力标签
    public <S> Optional<S> get(Class<S> type);      // seal 之前调用抛;未注册 → empty
    public <S> S require(Class<S> type);            // 缺席 → 人话异常
    @Override public void shutdown();               // 逆序 safeStop(异常记日志不中断)→ 幂等 → 摘除能力标签
}
```

- **seal 语义**:①缺必需依赖 → `ServiceDependencyException` 点名双方;②依赖环 → 点名环上服务;③拓扑序创建,**声明序为同层稳定次序**;④任一工厂/start 抛 → **回滚已启动服务(逆序 stop)再抛**,不留半启动态(镜像 switchMode"绝不停在中间态")。
- **shutdown 语义**:逆序、逐个 try/catch 不中断、幂等(连调 3 次 stop 恰一次);此后 `install` 抛。
- 消费面只有 `install/seal/get/require/shutdown` 五方法——**上帝注册表**是陷阱(14.32):事件转发、权限查询等一律不做。

### D3 ServiceSpec:与组件 Specification 的两处刻意差异

- 字段:`id / type / factory / required / optional / capabilities`;Builder 构建,构建期校验 id 非空。
- **无 `freeze()`**(与组件 Spec 的差异及理由,须写入 ADR-0006):组件 Spec 需要冻结是因常量 Spec 跨模式共享(`BOMB_POLICY`);服务 Spec 由 **L0 现场构建、只被一个注册表消费**,无共享串改问题——不加仪式(YAGNI)。
- **工厂收 `ServiceEnv` 而非 svc**:服务不持服务集 record,依赖在创建期经 `env.dep(Class)` 注入(拓扑序保证就绪);`depOpt` 表达可选依赖(缺席 empty,不抛);`plugin()`/`cfg()` 提供平台原语(`cfg()` 返回惰性 `ConfigView`,/reload 后自动读新值)。

### D4 单类型单服务 + ServiceLifecycle

- 一个服务 = 一个主类型;需要两个接口面就做一个门面接口,不做 multi-provide。
- `ServiceLifecycle { default void start(); default void stop(); }`:纯无状态服务(Facade 类)不必实现;实现者须保证 **stop 与 start 严格对称**。

### D5 服务 ↔ 会话:三条通道,无第四条

| 通道 | 方向 | 授权 |
|---|---|---|
| **订阅 `SessionEventBus`**(`SessionCreated / MemberJoined/Left / ModeSwitched / SessionDissolved / SessionComponentQuarantined`) | 会话 → 服务 | 感知生灭的官方通道(§3.1 事件行既有定位"给跨切面逻辑用");订阅句柄在服务 `stop` 中注销 |
| **只读快照**(`HydrokarbsApi.sessions()` 不可变 record;引擎内代码可经 `SessionService` 遍历 + `MatchModeInstanceAccess`) | 会话 → 服务 | 只读;**不得持有活实例/活组件引用跨切换**(镜像 ★29 的"现取不缓存"纪律) |
| **受控命令**(`SessionService#switchMode / dissolve / createSession`) | 服务 → 会话 | 服务属**外部调用方(切换来源②)**,与 `/sm` 命令同权;`force` 是显式选择,走 review 条款;无其他影响力端口 |

### D6 服务与组件互不可见

- 组件/模式不得 import `engine/service/**` 与 `service/**`;服务不得 import `mode/**` 与 `component/**`——双向冻结(ArchUnit,★40)。
- `MatchModeInstanceServices`(3 成员)与 `SessionServices`(2 成员)**不加成员**——服务不经 svc 注入组件(★25 口径不变)。组件要消费基础设施,走既有先例:**L1 适配器 + 包装组件**(RoleComponent 之于 RoleFacade 的同款),不是加 svc 成员。

### D7 包位置、线程、可测性

- `engine/service/`:装配**机制**,受 `EnginePurityTest` 禁词管辖(禁词表不变);`service/`:内容性**参照实现**,纪律 = 不 import `mode/**`/`component/**`、只经 D5 三通道触达会话、只消费 L1 适配器与引擎公开面。
- 线程:全部方法主线程(`onEnable`/`onDisable` 天然满足);注册表内部普通集合,不加锁,单线程约定写类头。
- `ServiceRegistry` 核心**零 Bukkit import**——全部验收离线可测(毫秒级,无需活服)。

### D8 常驻模块选型四案 + 注册受众五案(裁决记录,写入 13)

- **常驻模块选型**(按侵入度从低到高,先问再落):①拉取式(只读快照+事件,零侵入)→ ②伴随插件(框架零改动,消费 `HydrokarbsApi`)→ ③防腐包装式(L1 适配器+Port+包装组件)→ ④上报式(会话级组件本地权威 + 进程级服务汇总)。
- **注册受众**:A 显式装配(L0 基线,服务少时最优)/ B ServiceRegistry(内部服务群 ≥3 时)/ C ServicesManager(跨插件,既有机制)/ D `ServiceHandle<S>`(可选能力懒句柄,泛化 RoleFacade 懒解析 + `CapabilityRegistry`)/ E 版本化 SPI(第三方提供基础设施——**本版不做**,登记未决 §12-Q8)。
- 第一版落地:**A + B**(L0 显式装配进 B 注册表);C/D 沿用既有先例口径;E 不做。

### D9 公共 API 冻结面不变

`HydrokarbsApi` 方法集不动;服务、`ServiceRegistry`、服务查询一律**不进公共面**(第三方消费/提供服务列为未决 §12-Q9;将来开放走"只增不改")。`SessionService` 公开方法签名不变。

### D10 候选清单复核判据(与 §6.4 会话级组件候选的关系)

原"跨局累计/会话级看板"等候选,现在有两个可能落点(会话级组件 vs 服务)。**裁决一问:状态是否按会话隔离?** 隔离 → 会话级组件;跨会话聚合 → 服务(必要时两者组合:会话组件本地权威 + 服务汇总,即选型④)。本版不改变任何候选的既定归属,仅补判据。

### D11 命名修正:Instance 家族统一加 `MatchModeInstance` 前缀

- 动因:①与 `Session*` 家族并列时,`Instance*` 读不出宿主(最典型:`InstanceAccess` 其实是**会话侧**拿到的实例只读面);②"Instance" 在 Java 语境有泛化歧义(bean instance / JVM instance);③与 v3.1"裸 Mode → MatchMode"同一先例、同款理由。
- 规则:**类型名中的 `Instance` → `MatchModeInstance`**。三处刻意不改:包名(`engine/instance/`,v3.1 先例:包名不改)、方法名(`currentInstance()` / `tickCurrentInstance()` / `instanceEnded(...)`——上下文无歧义)、昵称 `svc`。
- **刻意不用缩写**(`MatchModePort` / `MatchModeEventBus` / `MatchModeStarted` 之类):会被误读为"模式模板的端口"或"进程级总线"——模板不可变、没有端口,启停的是实例不是模板;全称消除歧义。
- 对照表(写入 §0.5.6 迁移表;各文档正文与骨架按表替换,逐字历史段落不改写):

| 旧名 | 新名 |
|---|---|
| `InstanceServices` | `MatchModeInstanceServices` |
| `InstancePort` | `MatchModeInstancePort` |
| `InstanceAccess` | `MatchModeInstanceAccess` |
| `InstanceClock` | `MatchModeInstanceClock` |
| `InstanceId` | `MatchModeInstanceId` |
| `InstanceEvent` | `MatchModeInstanceEvent` |
| `InstanceEventBus` | `MatchModeInstanceEventBus` |
| `InstanceEventPort` | `MatchModeInstanceEventPort` |
| `InstanceStarted` | `MatchModeInstanceStarted` |
| `InstanceEnded` | `MatchModeInstanceEnded` |
| `InstanceView`(示例类型) | `MatchModeInstanceView` |
| `InstanceServicesMembersTest` | `MatchModeInstanceServicesMembersTest` |
| `InstanceCloseIdempotencyTest` | `MatchModeInstanceCloseIdempotencyTest` |

- 不改名清单:`Participant` / `Participant*` 事件(与 `SessionMember` 的两层分立 v1→v2 已裁定,且不以 Instance 开头);`Session*` 全族;`MatchModeInstance` 本名;`Custom` 载荷。

### D12 L0 装配编排:单一静态装配类 + 单一编排函数

- **形状**:`SHDFMatchAssembly`——静态无状态类,`static Context assemble(JavaPlugin)` 一函承载 onEnable 全部装配序列(线性,自上而下可读完);`static void shutdown(Context)` 镜像 onDisable。主类退化为两行委托(`onEnable = assemble(this) 存字段;onDisable = shutdown(ctx)`)。`Context` 是不可变 record(装配产物聚合),由**插件实例字段**持有。
- **纪律一:静态类可有静态函数,不许有静态可变状态**——装配产物只经返回值交付(插件重载安全;镜像 ★27 对进程级静态的警惕)。
- **纪律二:`assemble` 保持线性装配语句**——除 topology/config 分支外无业务逻辑;超过一屏时抽**私有分段 helper**(仍只含语句),**不复活多个公共装配类**(对本规模的过度拆分)。(**★v1.3-D5 注(ADR-0009):topology 分支随启动建会话特性删除——`assemble` 中不再有会话装配语句;「线性装配语句」纪律不变。**)
- **命名**:不得叫 `Bootstrapper`(12 §12.1 已有 Paper `PluginBootstrap` 占位类);定名 `SHDFMatchAssembly`。
- 理由:装配事实"一页纸"成为字面事实;主类纯净;无静态泄漏;单一接线点从"单一类"收紧为"单一函数"。

## 备选方案(Considered)

- **第三级组件挂载**。否:A10 明令且组件语义不符。
- **全塞 `SessionService` 内核**。否:内核长内容词汇,上帝对象。
- **伴随插件承载一切**。否:进程内聚合需要引擎事件与装配纪律。

## 后果(Consequences)

- **正面**:三驱动可达 + 进程级单例首次有装配纪律。
- **成本**:对象类别 +1,以 D1 硬边界 + ★34~★40 对冲。
- **不可逆点**:`engine/service/` 包名与"服务非组件"定位进 ADR 后,改动即破坏性变更。

### 与既有决策的关系

| 既有决策 | v4.1 处理 |
|---|---|
| A10"组件挂载仅两级,禁止第三级" | **不破**:服务不是组件、不挂载,是并列的对象类别;A10 原文保留,新增 D1 边界三条 |
| ADR-0002(聚合根零行为 + 引擎纯度) | 不动;`engine/service/` 在引擎包内,同受 `EnginePurityTest` 禁词管辖(禁词表不变) |
| ADR-0004(会话级组件层) | 不动;其候选清单复核判据由 D10 补充(隔离 → 会话组件;跨会话聚合 → 服务) |
| §8.8 CapabilityRegistry("L1/第三方注册") | **语义扩注**:注册者新增"服务(seal 期)"一种;机制、字符串标签口径、A2 校验均不变 |
| §3.1 SessionEventBus("给外部 API 订阅者与跨切面逻辑用") | **兑现**:服务就是该行预言的"跨切面逻辑"的第一类正式居民;总线机制零改动 |
| 旧插件体检"清场逻辑 4 份/空服判定 3 份" | 反证延续:服务层不新增任何清场路径;服务自己管自己的 stop 对称性(★38) |

## 未决问题

- **Q8(未决)**:第三方提供基础设施的版本化 SPI(E 案)是否开放、何时。**仲裁去向**:`13-扩展步骤.md` 选型节 + 本 ADR 未决项(修正案 §12 原文写"ADR-0005 未决项",即本文件,见文首编号注);**第一版不做**(D8 注册受众 E 案"本版不做")。本 ADR 不裁决,登记待下一版评估。
- 其余未决项均不在本 ADR 裁决,按修正案 §12 原样登记于各自去向:Q9(服务是否进 `HydrokarbsApi` 公共面 → 07 §7.4,第一版明确不进)、Q10(装配是否配置化 services.yml → 15,第一版 L0 硬编码)、Q11(服务热替换/热重载语义 → 15 热重载分级,第一版不支持)、Q12(§6.4 候选清单按 D10 复核 → 下一版评估,ADR-0004 候选段登记)——未静默决定。
