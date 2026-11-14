# ADR-0018:入场裁决二值化,JoinDecision.Kind 收缩为 ADMIT/REJECT

- 状态:已接受(2026-10-29;经 [30-v1.12修正案](../../30-v1.12修正案-JoinDecision二值化与SPECTATE退场.md) 追认为正式修正案)
- 关联:[30-v1.12修正案-JoinDecision二值化与SPECTATE退场.md](../../30-v1.12修正案-JoinDecision二值化与SPECTATE退场.md)(本裁决的变更指示与落盘凭据)、[ADR-0003](0003-join-policy-as-component.md)(入场策略即组件:机制不变、本裁决**不取代**,只收缩裁决词汇)、[28-v1.10修正案](../../28-v1.10修正案-属性袋退场与MapDefinition三字段定案.md) §6-Q1 与 [29-v1.11修正案](../../29-v1.11修正案-属性袋纯容器回归与声明词料三件退场.md) §6-Q1(版本轴先例);判据与 [ADR-0010](0010-api-surface-consolidation.md)/[ADR-0012](0012-explicit-api-registration.md)(「同一能力只留一份口径」)、[ADR-0016](0016-attribute-bag-removal.md)/[ADR-0017](0017-attribute-bag-pure-container.md)(「形状随真实消费面收敛」)同源
- 验收:本次验收 = `gradlew build` 绿 + `grep -rn "SPECTATE" src/ examples/` 零命中;冻结测试处置见 30 号 §5(★19 FROZEN 17 行零改动、★44 BASELINE 删 `JoinDecision$Kind#SPECTATE` 一行后合法重录);**不新增 ★ 编号**

## 背景

`JoinDecision.Kind` 自 v3.2 起是三值枚举 `ADMIT`/`SPECTATE`/`REJECT`。逐行核对引擎对 `Kind` 的全部消费,只有两处分支:

1. `MatchModeInstance#startAll`(`engine/instance/MatchModeInstance.java:139`):`== REJECT` → `continue`(留在名册、不进参赛者表);其余一律 `participants.put`(`:142`)。
2. `SessionServiceImpl#join`(`engine/session/SessionServiceImpl.java:631`):`== REJECT` → `Rejected`;其余一律 `registerMember` + `admitParticipant` + `Joined`(`:635-637`)。

也就是说:`SPECTATE` 与 `ADMIT` 在引擎里走**同一条路径**——名册照常登记、参赛者表照常进入。「参赛位/观战位」的差别引擎从不区分、也从不回报;它只在文档规则段里以「差别只在成为 Participant 的方式」出现,而那种方式由组件在 `ADMIT` 之后自行实施(`VisibilityComponent`/`TeamComponent` 的 SPECTATOR 队是组件侧词汇)。

三值因此是一处**结构冗余**:一个没有消费者的安置语义占用了引擎结构词汇位,并持续暗示引擎认识「观战」。引擎对入场的真实区分只有一个:**收不收**。观战是玩法/表现概念,不该占引擎结构词汇位。

## 决策

1. **`Kind` 收缩为二值**:`public enum Kind { ADMIT, REJECT }`(`engine/port/JoinDecision.java:18-23`);`SPECTATE` 常量删除。引擎行为零变化——两处 `== REJECT` 分支、两处无实现者默认 `ADMIT`、`JoinResult` 的 `Joined`/`Rejected` 两分支全部原样。
2. **观战安置归组件层**:引擎结构词汇只表达「收不收」;「怎么安置」是玩法/表现概念,组件在 `ADMIT` 之后用自己的词汇自理。消费点注释统一口径「ADMIT 成为参赛者;观战安置非引擎词汇,归组件层」。
3. **公共面与版本**:`HydrokarbsApi` 17 方法签名一字不动;`API_VERSION` **维持 7**(方法面零变化,破坏性在枚举常量侧);`ApiSurfaceTest` BASELINE 删 `JoinDecision$Kind#SPECTATE` 一行后合法重录,背书 = 本 ADR;★19 FROZEN 17 行不动。
4. **测试改写**:`SessionRosterTransferTest` 触发点②的原观战位用例随枚举删除改写为第二例 `ADMIT`;修测试理由按 AGENTS §4-12 写入测试 javadoc;覆盖不缩水(`REJECT` 不收/名册不登记 + `ADMIT` 进实例两面均保留)。
5. **词汇边界**:「观战」仍是现役**组件**概念(`06-共享组件.md` #16 `VisibilityComponent`、`01-分层架构.md:208`、`SessionIngestListener.java:81` 的组件侧职责表述保留);退场的只是引擎枚举常量,不是「观战」这件事。
6. **版本轴登记**:**同一 `API_VERSION=7` 窗口内的又一次公共面收缩**不升版本常量——沿用 28 §6-Q1 / 29 §6-Q1 先例:编译期暴露是主要失败面;出现真实消费方误用时再评估版本轴。

## 替代方案(已否)

- **保留三值,让 `SPECTATE` 继续存在**:**否,引擎从不消费它**——保留即维持「引擎认识观战」的假象,并让下一位实现者以为有观战位可落;无消费者的枚举常量与 21-D1/25-D1 同一判据(零消费者机制件删除)。
- **让引擎真正区分参赛位/观战位(参赛者表加安置标记或安置回调)**:**否,越界**——安置是玩法/表现;引擎一旦持有安置语义,就得定义观战的运行期契约(能否交互/能否被选为目标/能否计分),那正是内容词汇进 engine 的红线(ADR-0002/★4)。
- **新增第二个枚举(裁决 + 安置)或让 `JoinResult.Joined` 携带安置方式**:**否,多一层无消费者的公共面**——组件自己就知道它怎样安置,引擎回执只会造出第二份状态(违反「状态唯一」)。
- **升 `API_VERSION` 以标记枚举常量删除**:**否,先例已定**——方法面零变化不升版(28 §6-Q1/29 §6-Q1);编译期断是主要失败面,版本轴不区分声明/常量的形状变更。

## 权衡

- **得**:①**结构词汇只剩真实区分**——`Kind` = 收不收,两处分支与词汇一一对应;②**引擎不再暗示观战位**——观战回归组件词汇,安置方式由组件自理;③**公共面缩小**——一个无消费者常量退出白名单,`ApiSurfaceTest` 基线更短;④**行为零风险**——两处 `== REJECT` 与默认 `ADMIT` 全部原样,`JoinResult` 不变。
- **失**:①**同一 v7 窗口内又一次公共面收缩**——`JoinDecision$Kind#SPECTATE` 的删除在编译期暴露,运行期二进制只在误用时炸(Q1);②**组件侧安置无引擎回执**——`Joined` 不携带安置方式,跨组件查询需安置组件自建强类型读口(Q2);③**「观战」词汇从结构位退到组件位**——注释与文档里的「观战」不再受枚举约束,存在语义漂移可能(Q3);④**历史文档残留**——17~29 与既有 ADR 中的三值叙述按存档保留,读者需按头部注/本 ADR 辨别时点。
- **判据同源**:与 21-D1(零消费者机制件删除)、25-D1(同一能力只留一份口径)、28-D2/D3、29-D1(形状随真实消费面收敛)同一推理形状;本次只是把「引擎裁决结果」从「三个名字」收敛为「引擎真实区分的两个」。

## 后果

- 正:`JoinDecision.Kind` = `ADMIT`/`REJECT`;`SPECTATE` 全库退场;引擎两处 `== REJECT` 与默认 `ADMIT` 原样;`HydrokarbsApi` 17 法与 `API_VERSION = 7` 零改动;★19 FROZEN 17 行不动、★44 BASELINE 删一行后合法重录(背书 = 本 ADR);不新增 ★ 编号,计数维持 65。
- 负:任何写 `JoinDecision.Kind.SPECTATE` 的代码在编译期即断(当前 `src/`/`examples/` 零命中);组件若需要观战安置,须在 `ADMIT` 之后以自己的词汇实施,引擎不提供安置回执;历史存档中的三值叙述保留。
- 验收:`gradlew build` 绿 + `grep -rn "SPECTATE" src/ examples/` 零命中(本次验收);不新增 ★ 编号。
- 文档修改点(本裁决同步落盘):`03-会话与模式切换.md`(§3.1 状态机图、§3.2.1 javadoc/枚举/规则段、头部版本行);`00-假设与体检.md`(§0.5.4 行内现行口径注);`06-共享组件.md`(#24 行与「第四迁」段注);`CONTEXT.md`(现役 `JoinControlComponent` 行、历史术语表新增 `JoinDecision.Kind#SPECTATE` 行、头部版本链);`docs/设计文档/README.md`(头部版本链、v3.2 定位段注、修正案/ADR 索引、阅读路径);`docs/设计文档/AGENTS.md` 简报未列入、本窗口不动(其横幅版本链与 §4-10/§5 的术语口径建议同步)。
