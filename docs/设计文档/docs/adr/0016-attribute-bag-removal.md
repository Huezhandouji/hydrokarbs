# ADR-0016:属性袋退场,MapDefinition 三字段定案

- 状态:已接受(2026-10-28;经 [28-v1.10修正案](../../28-v1.10修正案-属性袋退场与MapDefinition三字段定案.md) 追认为正式修正案)
- 关联:[28-v1.10修正案-属性袋退场与MapDefinition三字段定案.md](../../28-v1.10修正案-属性袋退场与MapDefinition三字段定案.md)(本裁决的变更指示与文档/验收落盘凭据)、[26-v1.8修正案-地图注册去模板根约定与MapDefinition声明瘦身.md](../../26-v1.8修正案-地图注册去模板根约定与MapDefinition声明瘦身.md)(声明词料与 `AttributeBag` 三口径的上一次裁决);判据与 [ADR-0010](0010-api-surface-consolidation.md)/[ADR-0012](0012-explicit-api-registration.md)(「同一能力只留一份口径」)、[ADR-0013](0013-capability-tags-removal.md)(「声明形状随真实消费面收敛」)同源;**Supersedes(部分)** [ADR-0014](0014-map-declaration-path-direct.md)——其决策 1 的声明形状(`(worldDirectoryPath, metaAttributes)` 两分量、`AttributeBag` 作词料载体)、决策 3(`map.tags`/`map.reset-strategy` 约定键)与决策 4(`AttributeBag` 三口径)随本裁决退场;其决策 2(声明按路径直交、`MapRegistry` 无模板根、注册校验直验路径)与决策 5(`API_VERSION` 6→7)继续有效
- 验收:本次验收 = `gradlew build` 绿 + `grep -rn "AttributeBag" src/` 零命中;冻结测试处置见 28 号 §5(★26 就地废止注、★63 就地注;★19 FROZEN 17 行不动、★44 BASELINE 合法重录);**不新增 ★ 编号**(无新命题:退场与形状定案由既有 ★63 的语境与构建/源码普查承接,★ 编号体系只增不改,本裁决不改写任何 ★ 行)

## 背景

至 v1.8,`AttributeBag`(engine 根,命名空间 + 名字 + 类型的三元组键值袋)在框架内已无真实消费面,三处事实共同判死:

1. **生产写路径为零**。全库普查:`attrs().set(…)` 在 `src/main` 无任何调用点——组件状态自 v3「状态唯一」以来全部住在组件字段里,共享只经 `getComponent(Xxx.class)` 的强类型读口(§2.8-决断三);实例级/会话级/参赛者三处 bag 出生后恒为空,只承担「可能有人要写」的假想扩展面。
2. **读路径只剩一门面两约定键**。v1.8(26 号)把地图声明词料整表搬进 `AttributeBag metaAttributes` 后,bag 的真实读方只有 `map/MetaAttributes` 门面:①`tags(Object)` 读约定键 `map.tags`;②`resetStrategy(…)` 读约定键 `map.reset-strategy`。其余 bag 读口(`get/getOrDefault/has`)在 `src/main` 零调用。
3. **`reset-strategy` 从未被生产代码写入**。`map.reset-strategy` 只有读口、没有任何 `set`——声明方要改处置策略,只能绕到门面写口;这条「约定键」实际是一条没有写方的读径,而 `ResetStrategy` 是框架**唯一**消费的枚举值(归零处置/`discardWorld` 判据,§3.7),把它放在弱类型词料里,等于让框架唯一的强类型消费面穿过一层 `Class<T>` 转换。

同时,上一版为 bag 追加的「声明级三口径」(26-D2:专用写口 + 注册期冻结 + 声明级生命周期)维护的是一份**没有组件写者、没有其它读方**的机制:冻结与生命周期为「防注册后偷改」而存在,而防的对象只有门面自身。组件侧的状态表达早已是「字段 + 强类型读口」,bag 是叠在「状态唯一」之上的第二份状态表达(且是唯一一份弱类型、无编译期检查的)。

## 决策

**属性袋全库退场;地图声明词料改为 `MapDefinition` 三字段 + 一条标签提取约定**:

1. **退场清单(全库删除,零过渡窗口)**:
   - `engine/AttributeBag`(`Key` record、`set/get/getOrDefault/has`、`bindComponent`、`isNamespaceCheckEnabled/setNamespaceCheckEnabled`)整类删除;
   - 各端口/容器的 `attrs()`/`attr()` 全链删除:`MatchModeInstancePort`、`Participant`、`SessionPort`、`MatchModeInstanceAccess`、`SessionView`、`MatchModeInstanceView`(只读适配面);`MatchModeInstance`/`Session` 的袋构造与 close() 链同步消失;
   - `map/MetaAttributes` 的写口与约定键(`set`/`frozen()`/`TAGS`/`RESET_STRATEGY`/`resetStrategy(…)`)删除,读口收为 `tags(Object)`;
   - `map/` 包的「声明级三口径」随 bag 退场——冻结不再靠拷贝与断言,改由冻结契约承担(见决策 3)。
2. **`MapDefinition` 定案三字段**:`record MapDefinition(Path worldDirectoryPath, ResetStrategy resetStrategy, Object metadata)`(`map/MapDefinition.java:30`)——
   - **构造即权威**:路径 `toAbsolutePath().normalize()` 规范化入库;缺目录名(id 无从派生)构造期响亮抛;`resetStrategy` 非空;
   - **二参便利构造** `(Path, Object)`:`resetStrategy` 缺省 `REUSE_DIRTY`(未声明即复用,缺省语义与 23 号复用窗口闭环原样,`MapDefinition.java:48-49`);第二参是 `metadata`,不是策略;
   - **`metadata` 按引用存储、视为冻结**:本记录**不做深拷贝**,同一引用的后续变动对已注册声明可见;需要冻结效果时由注册方交出不再改动的值;`metadata` 允许 `null`(= 无声明词料);
   - **id = 声明目录名**(路径派生;目录查重/副本命名/钉图键三同一源,ADR-0014 决策 2 续行)。
3. **`TagProvider` 约定 = 声明词料的唯一解释面**:`map/TagProvider{ Set<String> tags(); }`(`map/TagProvider.java:13/16`);`MetaAttributes.tags(Object)` 是唯一读口——`metadata` 实现 `TagProvider` 时取其 `tags()` 的不可变拷贝,否则(含 `null`)返回空集(`map/MetaAttributes.java:23-28`)。空集 = 不被任何模式按标签选中(20-D3 口径)。`tags()` 不得返回 `null`;实现随 `metadata` 一起按引用冻结。
4. **`Handoff` 不随退场**:交接袋是独立机制(跨模式通道、键随主人、§3.3),其 `Key<T>` 与「命名空间 = 写入方 id」纪律原样;仅类注里与属性袋类比的两句措辞改为自述(`Handoff.java:10/16`),机制零改动。
5. **公共面与版本**:`HydrokarbsApi` 17 方法签名一字不动;`API_VERSION` **维持 7**(方法面零变化,破坏性在声明形状侧);`ApiSurfaceTest` 白名单与 BASELINE 合法重录(删 `AttributeBag` 及其 `Key`/方法行、各端口 `attrs()`/`attr()` 行,增 `TagProvider#tags()` 行与 `MapDefinition` 三分量/`id()` 行),背书 = 本 ADR。

## 替代方案(已否)

- **只删声明级三口径、保留 `AttributeBag` 供组件使用**:**否,零消费者**——生产写路径为零(普查见背景 1),保留的是「将来也许有人写」的假想 seam;且它仍是弱类型、无编译期检查的状态第二表达,与「状态唯一 = 字段 + 强类型读口」(§2.8-决断三)冲突。21-D1/25-D1 同款判据:零消费面的机制件删除,不为假想需求留接口。
- **声明词料继续走约定键,只删 bag 的组件半边**:**否,剩下的是没有写方的读径**——`map.reset-strategy` 从未被生产代码写入(背景 3),`map.tags` 的写方也只有门面;约定键的形式价值(「写入方与读方同源」)在唯一写方就是框架门面时不成立。`resetStrategy` 是框架唯一消费的强类型枚举,做成字段后缺省语义在构造期一处收口(二参构造),无需任何字符串键与类型转换。
- **`metadata` 深拷贝/快照冻结(用不可变容器包装)**:**否,通用深拷贝不存在**——`metadata` 是不透明对象,拷贝只能覆盖 `Map/List/Set` 的表面,自定义类型仍可逃逸;表面拷贝还会造出「注册方以为改的是自己的对象、框架手里却是旧快照」的假冻结。裁决改为显式契约:**按引用存储、视为冻结、注册方注册后不得再改**(`MapDefinition` 类注落字,MapRegistryTest 的脱钩断言族随旧口径退场)。该契约与 20-D1「交成品不交工厂」一致:交出的值由交出方保证不再变。
- **`Set<String> tags` 独立字段、不要 `metadata`**:**否,词料是宿主的扩展面**——框架只解释 `TagProvider` 一个约定,其余声明词料(宿主自用的展示名/轮换线索/赛程标签)原样携带、框架不解释;把唯一词料固化成 tags 字段,其余词料无处安放,又会退回约定键/属性袋。
- **保留 `MetaAttributes` 门面(含 `set/frozen`),只换后端**:**否,门面唯一职责是携带两约定键的词汇**——约定键退场后,门面的全部剩余职责就是 `tags(Object)` 一个纯函数读口,保留写口即保留一条无消费者的公共写路径(One-Version Rule:同一形状不留两套表达)。

## 权衡

- **得**:①**状态表达只剩一份**——组件状态 = 字段 + 强类型读口,声明词料 = `MapDefinition` 三字段 + `TagProvider`,弱类型公共面清零(`grep AttributeBag src/` 零命中);②**声明形状 = 真实消费面**——id(派生)/`resetStrategy`(强类型字段,缺省 `REUSE_DIRTY` 在构造期一处)/tags(约定提取),每个字段都有框架消费点;③**端口面收窄**——五个只读面各少一个成员,端口不再为「可能有人要写」保留通用写口;④`TagProvider` 是最小约定(一个方法),实现方可自持任意 metadata 类型。
- **失**:①**声明词料失去写入侧便利**——没有 `MetaAttributes.set/frozen` 与约定键写法,注册方要自己持有 metadata 类型并实现 `TagProvider`(纯静态值仍可交,`null` = 无标签);②**冻结只能靠契约**——按引用存储无机械保证,注册后偷改仍可见(旧 `copyOf` 双保险退场的直接代价);③**声明形状在同一 `API_VERSION=7` 内再次变更**——方法面零变化故版本常量不动,但 v1.8 窗口内已按两分量写法落地的消费方仍需改构造/读口(版本轴未能区分两次形状,登记风险);④`MapInfo` 形状不变(`(id, tags, resetStrategy)`),其 `resetStrategy` 来源从约定键改为声明字段,快照构造代码随改。
- **判据同源**:与 21-D1(零消费者机制件删除)、25-D1(同一能力退平行层)、26-D2/D3(声明形状随消费面收敛)同一推理形状;退场清单内的全部件在 `src/main` 无消费方,机制面零残留。

## 后果

- 正:`AttributeBag` 全库零残留(类/`Key`/端口 `attrs()`/`attr()`/`bindComponent`/命名空间开关);`MapDefinition` 三字段定案(`(worldDirectoryPath, resetStrategy, Object metadata)`;二参构造缺省 `REUSE_DIRTY`);`map/TagProvider` + `MetaAttributes.tags(Object)` 为标签唯一解释面;`map/` 包四件(MapDefinition/MapRegistry/MetaAttributes/TagProvider);`API_VERSION` 维持 7、`HydrokarbsApi` 17 法不动;`ApiSurfaceTest` BASELINE 合法重录(背书 = 本 ADR)。
- 负:消费方迁移——`new MapDefinition(路径, metadata)`(两参)或三参构造;标签声明从「门面写约定键」改为「metadata 实现 `TagProvider`」;`MetaAttributes.set/frozen/resetStrategy(…)` 调用点编译期即断;`metadata` 冻结退化为契约(背景/决策 3);`API_VERSION=7` 窗口内两次声明形状变更无法用版本号区分(权衡-失③)。
- 验收:`gradlew build` 绿 + `grep -rn "AttributeBag" src/` 零命中(本次验收);不新增 ★ 编号,★19(FROZEN 17 行)与 ★44(BASELINE 重录)的既有处置口径不变。
- 文档修改点(本裁决同步落盘):`02-核心概念.md` §2.7(改写为「状态唯一 = 字段 + 强类型读口;声明级 = 三字段 + `TagProvider`」)与 §2.2/§2.5/§2.5.1 骨架的 bag 行;`14-陷阱清单.md` 14.20/14.26/14.29(条目号不动,注内给新旧口径);`CONTEXT.md`(属性袋入「历史术语」,补「声明词料」「TagProvider」);`26-v1.8修正案` 头部状态注(正文存档不改写);`07-公共API.md`(§7.1 契约块/§7.2 冻结与值对象行/§7.5 地图行);`12-代码骨架.md`(包树/骨架块/★v1.8 增量注后追加);`13-扩展步骤.md`(§13.1 步骤 6/§13.11 步骤 3~4);`03-会话与模式切换.md`(§3.1 会话属性行/§3.3 类比句/§3.7 策略来源行);`01-分层架构.md`(L2 引擎盒/遗留对照行);`10-数据库.md`(§10.5 主线程读表);`00-假设与体检.md`(§0.5.14 时点注);`16-路线图与验收.md`(★63 就地注/硬边界图);`AGENTS.md` §4-10;`docs/设计文档/README.md`(v1.8 定位节注/白名单例外条款/ADR 索引)。
