# ADR-0012:API 面显式注册模式与会话组件(v1.6)

- 状态:已接受(2026-10-27)
- 关联:[24-v1.6修正案-API面显式注册模式与会话组件.md](../../24-v1.6修正案-API面显式注册模式与会话组件.md)(变更指示全文);判据与 [ADR-0010](0010-api-surface-consolidation.md)(22-D1/D3「同一能力只留一份口径」)同源;**Supersedes(部分)** [ADR-0007](0007-standalone-framework-plugin-split.md)——其决策 9(D14 通道一:`SessionComponentProvider` 经 ServicesManager 注册、事件收录)随本裁决退场,D14 的贡献边界不变式(干跑合并校验 / 坏贡献拒收录 / 模式仍无权 / 无运行期增删 / 合并序)由显式面 `registerSessionComponents` **原样承接**;`MatchModeProvider` 的 ServicesManager 注册半边一并退场(该通道未设 ADR,口径住 07 §7.2 与 12 §12.2——其注册路径的裁决记录自此为本文)
- 验收:★59(§16.2 追加;★19/★44 冻结快照随 v5 面合法重录——修正案 24 §3/§5 为背书)

## 背景

至 v1.5,「第三方给框架注册东西」并存三条通道,失败语义、摘除路径与时序各不相同:

1. **能力标签与地图**(★v1.1-D4 / ★v1.2):自落地起就是显式 api 面(`registerCapability`/`registerMap`)+ `PluginDisableEvent` 兜底(7.5 施工决策把能力兜底从 19 号设计的 `ServiceUnregisterEvent` 改判为 `PluginDisableEvent`)——注册点在调用方 onEnable 的一行代码里,失败响亮可捕获,摘除路径与注册路径同构(同一 `unregisterX(Plugin, id)` 形态)。
2. **模式**(`MatchModeProvider`,v3 起):ServicesManager 被动半边——第三方 `register(MatchModeProvider.class, …)`,框架经 `ProviderServiceListener` 监听 `ServiceRegisterEvent` 收录 / `ServiceUnregisterEvent` 摘除。双失败语义(启动期 ServiceLoader 半边响亮抛 `IllegalStateException`,运行期事件通道 SEVERE 拒收录、不上抛)、「先启补收录」(框架晚启时补扫既有注册服务)、事件回调抛异常中断整轮派发的顾虑,全部挂在这半边。
3. **会话组件贡献**(`SessionComponentProvider`,★v1.1-D14 通道一):同样走 ServicesManager 事件收录,坏 provider SEVERE 拒收录、会话照常。

通道 2/3 与通道 1 是**同一能力的两份口径**(注册、摘除、失败语义、归属簿记各一套),而显式半边已承载全部场景。事件半边还把「注册」从调用方代码里一行可 grep、可单测的调用,变成跨插件事件时序里的被动观测点:谁在何时注册、注册失败算谁的、摘除时回退编排挂在哪个回调——起服前不可审计,失败后不可就地暴露,只能起服派发事件来验证。

## 决策

**显式注册四法 + SPI 提供方通道退场 + 依赖反转**(全文见 24 号 §1 D1~D4):

1. `registerMode(Plugin, MatchModeProvider)` / `unregisterMode(Plugin, modeId)` / `registerSessionComponents(Plugin, specs)` / `unregisterSessionComponents(Plugin)` 进冻结面;注册闸全响亮(双口径合一);归属簿记 `modeClaims` 镜像 `capabilityClaims` 先例;「只摘自己的」守卫(越权响亮拒 / 缺席幂等)。
2. `SessionComponentProvider` / `ProviderServiceListener` 删除;`MatchModeProvider` 降格为 `registerMode` 载荷(engine/mode 包位、接口本体与 07 §7.2 白名单资格不变);`MatchModeRegistry#discover()` 的 ServiceLoader 本 jar 半边保留(纯 JVM 机制,离线可测;清单现存无工厂——框架不设出厂模式,21-D3)。
3. 依赖反转:第三方 `depend: [Hydrokarbs]`(纪律变前提)+ onEnable 首行 `load(HydrokarbsApi.class)` + 版本检查(14.36)+ 显式登记;「先启补收录」场景退役。
4. `API_VERSION` 4 → 5(删公共接口 = 破坏性;零过渡窗口,22-D2 同款)。

## 替代方案(已否)

- **SPI 与显式双轨共存(事件半边继续服务「不愿 depend 的插件」)**:**否,One-Version Rule 直接否决**(07 §7.2:同一能力两份口径,收敛优于并存——22-D1/D3 对两参 `createSession` 与文件式地图通道的同款裁决)。双轨意味着:双失败语义(响亮 vs SEVERE 拒收录)每次讲两遍、摘除路径两条(事件回调 vs 显式调用)、「先启补收录」的特殊时序注永远挂在 07/12/13 三处;而「不愿 depend」的受众在框架基线场景中不存在——消费框架 api 本身就要求 `load(HydrokarbsApi)`,不愿 depend 的插件连 api 都拿不到,双轨服务的是一个不存在的消费者(A9/A13 假想接缝判据)。
- **仅加四法、不删 SPI(双通道并存,新代码走显式)**:**否**——「新代码走显式」无法机械守卫:冻结测试管得住签名,管不住谁还在往 ServicesManager 上 register;双口径注(响亮 vs 拒收录)将无限期悬挂。与 ADR-0010 否决「薄便利方法」的理由同构:并存形态本身就是需要专门维护的承诺面,多一份口径的成本不是零,收益为零。要么删,要么别加。
- **保留事件半边、把摘除回退留在监听器(注册改显式、摘除留事件)**:**否**——半删半留会让「注册走 api、摘除走事件」的分裂比全事件通道更难解释;且回退编排留在监听器则无法被显式 `unregisterMode` 复用(「编排逻辑一份不写两处」的落点消失),兜底与显式两条摘除路径各自维护一份回退语义,正是本裁决要消灭的状态。
- **(会话组件)贡献面维持纯 recipe 通道、不提供全局注册**:**否**——19-D14 的受限开放裁决(宿主级默认策略可插拔,坏贡献不进会话)是已验收面;显式化只是换承载点,不回退裁决。同理,贡献注册面维持「受限」:模式仍无权贡献、无运行期增删(24-D1)。

## 权衡

- **得**:①**注册点显式可审计**——grep `registerMode` 即全量注册点;注册失败在 onEnable 就地暴露 = 拒载信号,而不是起服后翻事件日志;注册期双口径合一,坏注册物「进不了注册表且报得响」。②**摘除路径与 registerMap 同构**——四族注册物(能力/模式/地图/贡献)一套纪律:显式反注册 + provider 归属簿记 + `PluginDisableEvent` 兜底 + 守卫语义各自原样;第三方作者学会一张地图的摘除就会全部四族的。③**事件时序复杂度退役**——无 `ServiceRegisterEvent`/`ServiceUnregisterEvent` 派发顺序顾虑、无先启补收录特殊路径、无事件回调异常中断派发面;`MatchModeRegistry` 双发现只剩纯 JVM 机制(引擎零 Bukkit 静态调用,离线可测性不减反增)。④失败语义单一:响亮,全注册面一致。
- **失**:**失去平台被动发现与先启兼容**——服务注册机制「加载序无关」(softdepend 即可、晚启自动补收录)不复存在,依赖时序硬化为 depend 前提;忘记 depend 的存量插件从「可能侥幸工作」变为确定拒载。缓解:形态 B 的 depend 纪律(19 号)本就是消费框架 api 的事实前提——`load(HydrokarbsApi)` 在 api 缺席时返回 null,依赖反转只是把「事实上的前提」写明成「声明上的前提」;14.36 拒载路径对误配给出明确 SEVERE 日志而非静默缺席;破坏性面经 API_VERSION 5 + 编译期报错(SessionComponentProvider 实现类直接编译不过)双重暴露。
- **判据同源**:与 22-D1/D3(同一能力收敛为单口径)、21-D1(零消费者端口删除)同一推理形状;与 20-D1 的对照自此获得完整对称表述——**模式收工厂 / 地图交成品,差异 = 配置生命周期归属**(工厂交声明让框架管 `modes/<id>.yml`,成品交实例因为声明无配置注入),不是注册通道差异。

## 后果

- 正:★59 冻结(四法语义 + SPI 半边清零 + 兜底四族);HydrokarbsApi 编译面 15 → 19(增的全是显式注册,删的是 SPI);注册/摘除/兜底三类动作收敛为 api 面 + `ProviderUnloadListener` 一处;注册期失败语义与能力/地图通道完全同构。
- 负:`API_VERSION` 5 窗口内未迁移的 SPI 注册消费方拒载或编译失败;代码侧 javadoc/资源清单注释中的「★v1.5」字样与 ServicesManager 注册表述为施工存档(24 §7 注),不回改;先启补收录兼容面消失(由 depend 纪律承接)。
- 验收:★59(命题文本见 24 §5;落 §16.2 由「编辑·正文与术语」执行)。
- 未决移交:24 §6(战绩悬置承接 / 贡献摘除粒度整批不拆 / 出厂模式摘除面不预置)。
