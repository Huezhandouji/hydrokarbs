# ADR-0021:会话控制句柄 SessionHandle——API 面收敛(17→14 法,控制面以句柄承载)

- 状态:已接受(2026-11-11;经 [33-v1.15修正案](../../33-v1.15修正案-会话控制句柄与API面收敛.md) 落盘为正式修正案)
- 关联:[33-v1.15修正案-会话控制句柄与API面收敛.md](../../33-v1.15修正案-会话控制句柄与API面收敛.md)(本裁决的变更指示与落盘凭据)、[ADR-0007](0007-standalone-framework-plugin-split.md)(D2 成员两法与 D10「不构成第四通道」——本裁决把两法迁入句柄,口径延续)、[ADR-0010](0010-api-surface-consolidation.md)(★v1.4 首次 API 面收敛——「同一能力只留一份口径」先例)、[ADR-0013](0013-capability-tags-removal.md)/[ADR-0019](0019-shipped-limbo-mode-and-fallback-single-channel.md)/[ADR-0020](0020-api-version-negotiation-removal.md)(「删/改公共面 = 破坏性变更 + 用户裁决豁免双轨」先例链;0020 之后无版本常量,本裁决是首个以修正案 + ADR 为唯一公告载体的破坏性变更)
- 验收:本次验收 = 主工程 `gradlew build` 绿(BUILD SUCCESSFUL;落盘窗口以 `gradlew test --rerun` 复跑全量:281 用例零失败)+ `gradlew publishToMavenLocal` 后 `gradlew -p examples/TheirPlugin build` 绿(独立工程 6 任务执行,含守卫测试);冻结测试处置见 33 号 §5(★19 FROZEN 17→14 行 + 新增 HANDLE_FROZEN 5 行双清单、★44 WHITELIST 增 `SessionHandle.class`/BASELINE 删 3 重录 1 增 5——合法重录);验收追加 ★71~★72(计数 70→72)

## 背景

v1.1(19-D2/D7)以来,会话的控制操作散置在 `HydrokarbsApi` 扁平面:`switchMode(String sessionId, MatchModeSelection, Handoff)`、`joinSession(String sessionId, UUID)`、`leaveSession(UUID, LeaveReason)` 三法以**字符串 sessionId 传参**(leave 甚至不收 id——按玩家全局移出);查询侧三法(`sessionOf/session/sessions`)返回 `SessionInfo` 快照。调用方因此被迫两步走:「查快照拿 id → 拿 id 调控制法」,id 在两步之间只是一段字符串:

1. **错 id 编译期不可查**:错传/过期 id 直到运行期才显形,且 `switchMode` 是异步法——显形要等 future 完成 `Rejected(MODE_NOT_FOUND)`。
2. **守卫形状在每个调用方重复**:「先查现属再移出」「先验存在再入局」这类解析-守卫-委托形状(§13.4 hub 编排、examples/TheirPlugin join 命令)逐家复制。
3. **`leaveSession` 的全局移出语义有跨会话误伤面**:玩家已被外部迁移到他会话后,持旧会话 id 的调用方再调 `leaveSession` 仍会把人移出——操作域没有锚在「哪个会话」上。
4. 深模块视角:`SessionInfo` 是只读快照(不可变 record,「现取不缓存」14.31),控制是副作用——两者之间缺一个把「定位 + 操作权」合在一起的接口;字符串 id 是浅接口(调用方必须知道的不多,但每次都用错得起)。

## 决策

1. **新增 api 包公共接口 `SessionHandle`(5 法),语义 = 不可变定位器**:只持 `SessionId` 与委托(`SessionService` + 主线程执行器),不持活 `Session`/名册/实例引用,每次操作现解析——「现取不缓存」纪律(14.31)从查询侧延伸到控制侧。五法:`sessionId()`(纯值,消亡后仍可读)/`Optional<SessionInfo> info()`(现取快照,消亡 → empty)/`join(UUID)`(引擎裁决原样透传)/`leave(UUID, LeaveReason)`(会话域守卫后委托)/`switchMode(MatchModeSelection, Handoff)`(任意线程发起、Impl 回主线程、future 在主线程完成;异常经 future、永不悬挂——逻辑自 `HydrokarbsApiImpl` 原样迁入,行为不变)。实现体 `SessionHandleImpl` 包私有 final,构造注入,快照转换 `snapshot` 静态单点居实现类(一份不写两处,`HydrokarbsApiImpl` 只产句柄不再转快照)。
2. **裁决一(用户):`info()` 组合式**——`SessionInfo` record 原样保留(五分量冻结行不动),句柄上唯一信息口 `info()`,不把快照分量平铺成句柄方法。快照字段口径单点住在 record;快照演进不动句柄冻结面;`Optional` 返回天然承载消亡态。
3. **裁决二(用户):`leave` 会话域收窄**——仅当玩家现属会话 == 本句柄会话才委托 `SessionService#leave`,否则 no-op(主线程内查后调,无竞态);**外部迁移代价不作为考量**。旧「按玩家全局移出」语义的跨会话误伤面随之消灭;按玩家解析走 `sessionOf`(与按 id 解析走 `session` 对称)。
4. **`HydrokarbsApi` 冻结面 17→14 法,「id 不存在」语义前移**:删三法(迁句柄);`sessionOf/session/sessions/createSession` 四法改产句柄(响亮失败语义不变:抛时句柄不存在);其余 10 法一字不动。原 `switchMode` 对不存在 id 的 future 内 `Rejected(MODE_NOT_FOUND)` 前移为解析点 `Optional.empty()`(同步早失败——错误更近错误源,调用方 `ifPresent` 链即刻承接);future 内 `Rejected(MODE_NOT_FOUND)` 只剩「句柄在手后会话消亡」一态(实签保留)。
5. **按破坏性变更口径处理,经用户裁决不设双轨(第 6 次)**:删 3 公共方法 + 4 法返回类型改(含泛型实参)= 跨 jar 破坏,与 ★v1.4 删两参 createSession、★v1.7 删能力两法同构。无版本常量可升(★v1.14/32 号终结数字轴)——本裁决是数字轴终结后**首个**以修正案 + ADR + 版本链文档 + maven 坐标为唯一公告载体的破坏性变更。★19 FROZEN 17→14 行 + 新增句柄冻结集 5 行(双清单)、★44 WHITELIST/BASELINE 合法重录(背书 = 33 号/本 ADR)。
6. **引擎(`engine/**`)与 `SessionService` 公开面零改动**:八法签名一字不动,命令/监听走 `SessionService` 不受影响;受控暴露单点从 `HydrokarbsApiImpl` 平移到 `SessionHandleImpl`,委托链「句柄 → `SessionService` → 引擎」与原「api Impl → `SessionService` → 引擎」同深,引擎对「谁在调」零感知。摘除回退编排仍引擎内直调 `sessionService.switchMode`(「服务不经 HydrokarbsApi」14.37 原样)。
7. **验收处置**:新增 ★71(句柄冻结面:主面 14 + 句柄 5、`SessionInfo` 原样、总冻结公开方法 17→19)与 ★72(句柄操作语义:域守卫/失效三态/线程纪律/语义前移);计数 70→72(框架 63→65);行为测试迁移 = 断言语义不变的调用形态迁移,修测理由登记于 33 号 §1-D6。

## 替代方案(已否)

- **在 `SessionInfo` 上加控制方法(快照变活门面)**:**否**——破坏快照不可变纪律与「不持活引用」(快照会被跨线程/跨周期持有,门面方法要现解析活会话,快照纪律全面失守);record 冻结面被迫承载行为。
- **句柄平铺快照分量(`modeId()`/`members()` 等直接上句柄)**:**否,裁决一**——同一份字段口径两处维护、两份冻结清单各自演进;每个平铺方法还要各自回答消亡语义(第三种错误策略);`info().orElseThrow()` 一行即得全部分量,平铺无新能力。
- **句柄返活 `Session` 引用(定位器直接定位到引擎对象)**:**否**——泄漏引擎内部可变结构进公共面(★44 白名单反面:`Session` 不在册);跨线程读非线程安全结构(§10.5)。
- **主面保留三法 + 句柄并行(双轨)**:**否**——One-Version Rule;同一能力两份口径,`HydrokarbsApiImpl` 与 `SessionHandleImpl` 各写一份「解析-守卫-委托」,与 ★v1.4「同一能力只留一份口径」推理形状冲突。
- **`leave` 保留全局移出语义(句柄只当 id 载体)**:**否,裁决二**——跨会话误伤面(玩家被外部迁移后旧句柄仍能踢人);「句柄定位 A 会话、操作落 B 会话」违背定位器语义。
- **`switchMode` 句柄化后同步化(直接返回 `SwitchResult`)**:**否**——切换是两阶段异步管线(A 段裁决 + B 段世界装配,§3.2),同步化要么吞掉管线语义、要么在调用线程阻塞等主线程——把异步纪律改坏来迁就调用形态,因果倒置。
- **`session(id)` 对不存在 id 抛异常(而非 empty)**:**否**——与既有查询语义不对称(`mode(String)` 缺失 → empty,`sessionOf` 未入会话 → empty);「查不到」是查询常态不是异常态,空值语义(21-R3)在查询侧一律 Optional。

## 权衡

- **得**:①控制面成为深模块——5 法承载「定位 + 信息 + 入/出/切」,「解析-守卫-委托」形状收进一处,调用方拿到句柄即拿到操作权,字符串 id 只在解析点出现一次;②「id 不存在」同步早失败(解析点 empty),错误更近错误源,异步 future 不再承载「从未存在」这种输入错误;③`leave` 域守卫消灭跨会话误伤;④主面 14 法职责单一(解析口 + 注册/订阅/目录),「与 `/sm` 同权」的控制面整体迁句柄;⑤快照 record 冻结不动(组合式),引擎零改动,切换管线/回退链零触碰;⑥句柄失效语义成体系(消亡后 info empty/leave no-op/join Rejected/switch Rejected)——「句柄在手后会话消亡」从各调用方的隐患变成契约的一部分。
- **失**:①**总冻结公开方法 17→19(主面 14 + 句柄 5,净 +2)**——冻结面扩大是本次的明确代价:换来的是主面收缩与控制面聚合;两个清单各自全等断言(★19 双清单),新增公共方法的门槛不降;②**破坏性重编译窗口**——消费过三迁出法或旧 `SessionInfo` 返回形态的旧编译物断编译(当前唯一消费者 examples/TheirPlugin 已随窗口迁移,build 绿实证;33 §6-Q3);③「从未存在」与「已消亡」在解析点同形 empty(操作语义等价,区分是无消费面信息,33 §6-Q2);④句柄无判等语义(定位器非值对象,判等锚点 = `sessionId()`,33 §6-Q1);⑤`sessions()` 逐会话分配句柄对象(会话量级个位~十位,可忽略;不做缓存池——缓存即引入失效面,33 §6-Q5)。
- **版本轴口径**:无运行期常量可升(★v1.14 终结数字轴);本裁决确立数字轴终结后的破坏性公告形态——修正案 + ADR + 版本链文档 + maven 坐标四件套,后续破坏性变更沿用。

## 后果

- 正:api 包新增公共接口 `SessionHandle`(5 法,入 ★44 白名单)与包私有 `SessionHandleImpl`(快照 `snapshot` 单点);`HydrokarbsApi` = 14 方法;`HydrokarbsApiImpl` 查询只解析产句柄;★19 双清单(FROZEN 14 + HANDLE_FROZEN 5)、★44 BASELINE 重录(主面 14 行 + 句柄 5 行,`SessionInfo` 行族不动);★71~★72 新增(计数 72 条);examples/TheirPlugin 三命令句柄化。
- 负:旧编译物需按 v1.15 面重编译;`HydrokarbsApiMembersTest`/`HydrokarbsApiImplTest`/`TheirPluginGuardsTest` 调用形态迁移(断言语义不变,修测理由 = 33 号 §1-D6);消费方纪律新增一条——不持句柄缓存会话状态、判等用 `sessionId()`。
- 文档修改点(本裁决同步落盘):`07-公共API.md`(§7.1 契约块/§7.2 冻结行·值对象行·白名单行/§7.5 权限面表与示例一);`03-会话与模式切换.md`(§3.2 受控暴露口径);`12-代码骨架.md`(§12.1 api/ 包清单、§12.2 历史骨架注、★v1.15 增量注);`13-扩展步骤.md`(§13.4 hub 编排与单次定制、§13.7/§13.11);`16-路线图与验收.md`(★71/★72、§16.2 头注、§16.4 速查);`CONTEXT.md`(新增「会话控制句柄」词条、历史术语表三行);`README.md`/`AGENTS.md`(版本链、API 面句、验收计数);`00-假设与体检.md`(v1.15 定位行、§0.5.20);`02-核心概念.md`(随 grep 对位)。
