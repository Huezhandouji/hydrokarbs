# ADR-0002:模式聚合根零行为,引擎不含玩法级状态机

- 状态:已接受(2026-10-15,架构设计案 v3.0)
- 关联:`设计案/README.md`;`设计案/02-核心概念.md`(§2.3/§2.4/§2.8);`设计案/09-角色组件层.md`;ADR-0001
- 取代:v2 设计中 `Mode` 接口(10 方法,含 `plan()`/`transition()`/`validateMap()`/`rolePolicy()`)与引擎内 `phase/`、`rule/` 两个包

## 背景(Context)

v2 完成了会话化(Session + switchMode + 大厅模式化),但 `Mode` 仍是一个"胖接口":一半方法是行为(`plan(map, cfg)` 产出一揽子计划、`transition()` 是策略对象、`validateMap()` 是校验逻辑)。同时引擎里长着 `PhaseChain`(阶段状态机)、`RuleEngine`、胜负求值——框架一边宣称"玩法可插拔",一边在引擎里内置了三种玩法机制。

同一工作区里的角色系统(ShadowHunterRoles)已经用生产代码验证了另一条路:`core/Role` 聚合根只有 id/名称/描述/图标/阵营声明 + 有序组件描述符表,**全类零行为分支**;行为(能量/理智/热键栏/阵营/任务)全部住在 `RoleComponent` 子类里,经 svc 构造期注入、声明序派发、装配期依赖校验。它是活的反例:不需要"模式接口",只需要"组件表"。

## 决策(Decision)

1. **`Mode` 改为 final 类聚合根**:id/displayName/description/icon/mapTags/ModeMeta + `LinkedHashMap<id, ComponentEntry>`;仅有的两个方法是 `verifyDependencies()` 与 `createInstance()`(都是装配期职责,照抄 `Role`)。`plan()` 消失——运行差异经 `InstanceServices` 注入组件,模板进程级复用。
2. **组件基类 `ModeComponent` 照抄 `RoleComponent`**:svc 构造期注入四条件、`awake/start/update/onEvent/onHandoff/stop` 生命周期(与 start 严格对称)、`Specification`(requires/requiresOptional/providedType/freeze/`checkMap`)、装配期依赖校验算法逐行照抄 `Role#verifyDependencies`(自环硬失败、环允许、awake 序=声明序)。
3. **引擎三迁**:`PhaseComponent` / `EndGateComponent` / `RuleBookComponent` 降级为 L3 组件;"要不要阶段/结束判定/规则簿"与"要不要队伍/重生"成为同一种决策——Builder 里加不加一行。引擎对三者零认知,只提供 `svc().self().end(EndResult)` 与 `switchTo(target, payload)` 两个基础端口(组件主动声明,不被轮询)。
4. **`InstanceServices` 三成员封闭**(self/components/modeInfo,照抄 `ComponentServicesPort`):不为任何领域增设转发成员,组件协作走 `getComponent`。
5. **状态唯一**:`ModeInstance` 容器不持有玩法状态(照抄 `RoleInstance`),team/score/占用表/进度全在组件;`Participant` 上的 team/role 字段拆除,改经组件读口查询。
6. **`RoleComponent` 收拢角色接入**(§9):桥接层保持无状态翻译(采样调度移出);游戏侧选角授权/数值介入/敌对口径/奖励映射/局末释放收拢为一个组件的四个内部 facet;模式与其他组件不得接触桥接类型(ArchUnit 冻结)。
7. **`ModeProvider` SPI**(`{ id(); Mode build(cfg); }`):聚合根是 final 类,发现面改为工厂接口;`build()` 后不可变,第三方不能给已注册模式追加组件(A9,杜绝运行期装饰)。

## 备选方案(Considered)

- **保留 v2 接口式 Mode**:否决。10 个方法里 6 个是行为,每个新玩法都要"实现一揽子方法";且接口一旦发布就是公共契约,瘦身只会越来越难。
- **Phase/Rule 留在引擎、只做 Mode 瘦身**:否决。那是半吊子:引擎仍内置玩法机制,"组件化"停留在口号;且切换的 ENDING 收口仍要依赖引擎解释"阶段是否走完"。
- **RoleComponent 拆成四个独立组件**(Loadout/State/Hostility/Rewards):否决。四者状态同源(围绕"本实例的角色占用"),拆开必引入跨组件同步;facet 作为内部接缝即可(参照角色系统 FactionComponent 的"一个组件管一个概念的全部读写")。

## 后果(Consequences)

- **正面**:新增模式从"实现接口"降为"填表"(CTF 估行数 150~250 → 120~200,且无生命周期代码);引擎代码量净减(少两个包);"大厅无特权"更强(极简大厅=少几行 addComponent,无开关参数);与角色系统共用同一套架构心智,两边代码风格互为参照。
- **负面/成本**:v2 术语再改一轮(ModePlan 删除、Component→ModeComponent 等)——框架未动工,零成本窗口关闭前完成;聚合根与 svc 的方法/成员集需要测试冻结(`AggregatePurityTest`/`InstanceServicesMembersTest`),否则会"顺手"回胖;组件间协作风俗从"上下文找"改为"组件读口",初学者有适应成本。
- **不可逆点**:一旦实现落地并发布 `HydrokarbsApi`,`Mode`/`ModeComponent`/`InstanceServices` 进入公共词汇;引擎三迁若日后回退(把 Phase 拉回引擎)将同时违反 `EnginePurityTest` 与已发布的组件公共面——本 ADR 即为该回退的预先否决。
