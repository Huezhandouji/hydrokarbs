# ADR-0017:声明词料纯容器,AttributeBag 回归与 TagProvider/MetaAttributes 退场

- 状态:已接受(2026-10-28;经 [29-v1.11修正案](../../29-v1.11修正案-属性袋纯容器回归与声明词料三件退场.md) 追认为正式修正案);**Supersedes(部分)** [ADR-0016](0016-attribute-bag-removal.md)——其「`Object` 不透明词料 + 按引用存储视为冻结」与「`TagProvider` 约定 + `MetaAttributes.tags(Object)` 唯一读口」两项决策被本裁决取代;其「属性袋机制退场」对**旧断言机制**的部分仍然成立——本裁决回归的纯容器是**不同机制**(`Key` 二元组、无命名空间、无组件授权表、不参与实例 `close()` 链),旧机制不复活
- 关联:[29-v1.11修正案-属性袋纯容器回归与声明词料三件退场.md](../../29-v1.11修正案-属性袋纯容器回归与声明词料三件退场.md)(本裁决的变更指示与文档/验收落盘凭据)、[28-v1.10修正案-属性袋退场与MapDefinition三字段定案.md](../../28-v1.10修正案-属性袋退场与MapDefinition三字段定案.md)(被本裁决部分取代的上一版)、[26-v1.8修正案-地图注册去模板根约定与MapDefinition声明瘦身.md](../../26-v1.8修正案-地图注册去模板根约定与MapDefinition声明瘦身.md)(路径直交与声明瘦身的上一次裁决);判据与 [ADR-0010](0010-api-surface-consolidation.md)/[ADR-0012](0012-explicit-api-registration.md)(「同一能力只留一份口径」)、[ADR-0016](0016-attribute-bag-removal.md)(「声明形状随真实消费面收敛」)同源
- 验收:本次验收 = `gradlew build` 绿 + `grep -rn "TagProvider\|MetaAttributes" src/` 零命中 + `grep -rn "namespace" src/main/java/com/hydrokarbs/engine/AttributeBag.java` 零命中;冻结测试处置见 29 号 §5(★26 维持废止注再校准、★63 就地注再校准;★19 FROZEN 17 行不动、★44 BASELINE 合法重录);**不新增 ★ 编号**

## 背景

28 号(ADR-0016)把 `MapDefinition` 第三分量定为 `Object metadata`,并规定「按引用存储、视为冻结」+ `TagProvider` 提取标签 + `MetaAttributes.tags(Object)` 唯一读口。该形状只维持了一个设计版本,暴露出三处结构缺陷:

1. **冻结只有契约、没有结构**。`Object` 无法保证不可变,框架不做深拷贝,注册方注册后偷改仍对已注册声明可见;28 §6-Q2 只能把责任留给注册方的自律条款。框架对一个**声明级**值没有机械冻结手段,与「构造即冻结」的既有声明纪律不一致。
2. **`TagProvider` 是只有一个方法的中间层**。框架对声明词料的全部消费就是标签集一个 `Set<String>`;为了从 `Object` 里取出它,28 号引入了一个约定接口,再配一个 `MetaAttributes` 门面。约定键退场后,门面的全部剩余职责也只是一个纯函数——两层中间物都没有独立语义。
3. **其余词料无处安放、也无法自描述**。`Object metadata` 迫使宿主把所有声明词料塞进自定义类型:框架不解释、读方也不知道有哪些键、键的类型只能靠约定。26 号之后消失的 `map.tags` 键本可以以类型化键的形式留在容器里,`Object` 却把它重新推回「不透明」。

同时,旧 `AttributeBag` 的退场理由(28-D1)必须继续成立:它的命名空间断言、组件授权表、各端口 `attrs()` 与实例 `close()` 链在生产代码零写路径,不能因为声明词料需要一个容器就整体复活。**需要的不是旧机制,而是一个同名的新容器**。

## 决策

**属性袋以纯容器形态回归;声明词料改为 `MapDefinition` 三字段 + 类型化词料键**:

1. **`AttributeBag` 重写为纯不可变容器**(`engine/AttributeBag.java:26`):
   - `Key<T>(String name, Class<T> type)` **二元组**,无命名空间分量(`engine/AttributeBag.java:29`);
   - `Builder#put` 是唯一公开写侧(`:90`),`build()` 一次收口;空表返回共享 `empty()`(`:52-54/98-100`);
   - 私有构造经 `Map.copyOf` 冻结映射结构(`:47-49`),实例上无任何公开写口(写侧仅嵌套 `Builder`);
   - 读口只有 `get`/`getOrDefault`/`has`(`:62-80`):缺席或值类型不符 → `empty`/fallback,`has` 只问键存在;
   - 零 Bukkit 依赖;不参与实例 `close()` 链;写只发生在声明装配期一次。
2. **`MapDefinition` 第三分量回归容器**:`record MapDefinition(Path worldDirectoryPath, ResetStrategy resetStrategy, AttributeBag metadata)`(`map/MapDefinition.java:34`);`metadata` 非空,无词料传 `AttributeBag.empty()`(`:43`);二参便利构造 `(Path, AttributeBag)` 缺省 `REUSE_DIRTY`(`:66-68`);路径规范化/缺目录名响亮抛/id = 声明目录名原样(`:40-50/71-73`)。
3. **约定键与唯一标签读口**:`MapDefinition.TAGS = AttributeBag.Key.of("tags", stringSetType())`(`map/MapDefinition.java:37-38`,受检擦除转换只校验到 `Set` 层);`tags()` = `metadata.getOrDefault(TAGS, Set.of())`(`:79-81`)——全框架唯一标签读口;`MapRegistry#byTag` 只经此口(`map/MapRegistry.java:157-166`);`MapInfo` 构造期 `Set.copyOf` 再收口(`api/view/MapInfo.java:16-19`)。其余词料由宿主自定 `Key` 原样携带,框架只解释 `TAGS`。
4. **三件退场(零过渡窗口)**:`Object metadata` 与「按引用存储、视为冻结」契约、`map/TagProvider` 整接口、`map/MetaAttributes` 整类全部删除;`map/` 包 = `MapDefinition` + `MapRegistry` 两件。`null` 语义由 `empty()` 取代。
5. **命名消歧**:旧 `AttributeBag`(命名空间 + 名字 + 类型、断言、授权表、`attrs()`)仍在 CONTEXT「历史术语」废止名单,废止不变;现役 `AttributeBag` 是不同机制。机械分界 = `Key` 二元组、无 `namespace`/`bindComponent`/`setNamespaceCheckEnabled` 成员、不参与实例 `close()`。
6. **公共面与版本**:`HydrokarbsApi` 17 方法签名一字不动;`API_VERSION` **维持 7**(方法面零变化,破坏性在声明形状侧);`ApiSurfaceTest` BASELINE 合法重录(增回 `AttributeBag` 及其 `Key`/`Builder`/三读口/`empty`,删 `TagProvider#tags()` 行,`MapDefinition` 第三分量类型改为 `AttributeBag`),背书 = 本 ADR;★19 FROZEN 17 行不动;★26 维持废止(命名空间断言检验不复活),★63② 由容器结构恢复。

## 替代方案(已否)

- **保留 28 号的 `Object metadata` + 按引用冻结契约**:**否,冻结没有结构**——28 §6-Q2 已承认「注册后偷改仍可见」;声明级值只靠注册方自律,与本框架对声明「构造即冻结」的一贯纪律相悖。纯容器把冻结从条款变成类型结构。
- **保留 `TagProvider` 作为标记接口,让 `AttributeBag` 实现它**:**否,多一层无收益**——框架对词料的唯一消费是标签集;实现接口只是把 `TAGS` 键的存在换一个发现方式,还要求容器类型实现声明专用接口。直接读常量键更短、更可检索。
- **恢复旧 `AttributeBag` 形状(命名空间 + 授权表 + `set` 断言)**:**否,旧机制已被 28 号判死**——生产写路径为零,断言与授权表维护的是一份没有写者的机制;本裁决只借容器形态,不借运行期机制。`Key` 去命名空间是这一分界的机械标记。
- **词料值深拷贝/快照冻结**:**否,通用深拷贝不存在**——只能覆盖 `Map/List/Set` 的表面,自定义值仍可逃逸,还会造出「注册方以为改的是自己的对象、框架手里却是旧快照」的假冻结(ADR-0016 同款否决)。裁决改为:容器结构冻结 + 词料值按不可变值交付。
- **直接用 `Map<String, Object>` 当词料**:**否,失去编译期键类型**——字符串键 + `Class<T>` 强转会把类型错误推到运行期;`Key<T>(name,type)` 让 `get` 在一次调用里完成类型校验,读口语义(缺席/类型不符 → empty)也只有一处。
- **把 `tags` 做成独立 `Set<String>` 字段、不再有容器**:**否,其余词料无处安放**——宿主展示名/轮换线索/赛程标签仍需随声明携带;单一字段会迫使这些词料退回约定键或第二个不透明对象。

## 权衡

- **得**:①**冻结结构化**——`Builder` 唯一写侧 + `Map.copyOf` 收口,注册后无公开写口,来源 `Map` 的后续改动不跟随(`MapRegistryTest.registeredDeclarationIsFrozenAndHasNoMutationChannel`);②**类型化词料键**——`Key<T>(name,type)` 让每个键自描述,`get` 在一次调用里完成缺席/类型校验;③**中间层清零**——`TagProvider`/`MetaAttributes` 两个只有一个职责的类型退场,标签读口只剩 `MapDefinition#tags()`;④**旧机制零复活的机械分界**——`Key` 二元组、无命名空间/授权表/`close()` 链,review 可一眼判定。
- **失**:①**同名不同物的认知成本**——「属性袋已退场」与「`AttributeBag` 在代码里」必须同时成立,靠 CONTEXT 消歧与机械分界承接(Q4);②**值深度冻结仍靠契约**——`Map.copyOf` 只冻结映射结构,可变值仍可与外部共享(Q2);③**`TAGS` 元素类型擦除**——`Set<String>` 只校验到 `Set` 层(Q3);④**源码/二进制不兼容**——第三分量 `Object` → `AttributeBag`,消费方须重新编译,而 `API_VERSION` 不变(Q5);⑤**同一 v7 窗口内第三次声明形状变更**——版本轴仍不区分(Q1)。
- **判据同源**:与 21-D1(零消费者机制件删除)、25-D1(同一能力退平行层)、28-D2/D3(声明形状随消费面收敛)同一推理形状;本次只是把「声明词料」从「一个不透明对象 + 一条提取约定」收敛为「一个纯容器 + 一个常量键 + 一个读口」。

## 后果

- 正:`engine/AttributeBag`(纯容器:`Key` 二元组、`Builder` 唯一写侧、`Map.copyOf` 收口、`get/getOrDefault/has` 三读口、`empty()`)回归;`MapDefinition(Path, ResetStrategy, AttributeBag)` + `TAGS` + `tags()` 定案;`map/TagProvider`、`map/MetaAttributes` 与 `Object metadata` 全删;`map/` 包两件;`API_VERSION` 维持 7、`HydrokarbsApi` 17 法不动;★19 FROZEN 17 行不动、★44 BASELINE 合法重录(背书 = 本 ADR)。
- 负:消费方迁移——`new MapDefinition(path, metadataObject)` 改为 `new MapDefinition(path, bag)`(无词料传 `AttributeBag.empty()`);标签声明从「`metadata` 实现 `TagProvider`」改为 `new AttributeBag.Builder().put(MapDefinition.TAGS, Set.of("tag")).build()`;`TagProvider`/`MetaAttributes` 调用点编译期即断;值深度冻结仍靠契约;`TAGS` 元素类型只校验到 `Set` 层。
- 验收:`gradlew build` 绿 + `grep -rn "TagProvider\|MetaAttributes" src/` 零命中 + `engine/AttributeBag.java` 无 `namespace` 命中(本次验收);不新增 ★ 编号,★26 维持废止、★63② 由容器结构恢复。
- 文档修改点(本裁决同步落盘):`02-核心概念.md` §2.7(纯容器语义再改写);`07-公共API.md`(§7.1 契约块、registerMap 行、§7.2 冻结/值对象/版本协商行、§7.5 地图行);`14-陷阱清单.md` 14.20③/14.26/14.29(条目号不动);`16-路线图与验收.md`(★26/★63 再校准注、★v1.11 修订注、16.4 速查);`00-假设与体检.md`(v1.11 定位、§0.5.17);`03-会话与模式切换.md`(§3.7 策略来源句);`12-代码骨架.md`(§12.1 包树、§12.2 增量注);`13-扩展步骤.md`(§13.1/§13.11);`CONTEXT.md`(历史术语注 + 现役 `AttributeBag`/`tags()` 词条);`README.md`/`docs/设计文档/README.md`/`AGENTS.md`(版本链、硬边界、索引);`26`/`28` 号头部状态注(正文存档不改写)。
