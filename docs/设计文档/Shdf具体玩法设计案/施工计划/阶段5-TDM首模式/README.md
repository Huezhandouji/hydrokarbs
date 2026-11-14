# 阶段5-首模式与持久化(施工指导)

> SHDF 玩法线;框架施工线见 ../../../施工计划/。**本目录仅含玩法半段 5.1/5.2(P4);持久化半段 5.3/5.4(P5)属框架线,见 `../../../施工计划/阶段5-首模式与持久化/`。**
> ★v1.1/v1.2 对齐注:本目录子阶段文档写定于 v1.0,旧包根(`com.shdf.match`)、旧主类(`SHDFMatchPlugin`)与单 jar 口径按 `../README.md` 头部对齐注重读(框架 `com.hydrokarbs`、宿主 `com.shdf.*`、API_VERSION=3)。
> 对应路线图:`../../../16-路线图与验收.md` §16.1 的 **P4 + P5**(本线执行 P4 半段);阶段定位与出口闸门总表见本线 `../README.md` §1 与框架线 `../../../施工计划/README.md` §1。
> 引用口径:「§N.M」= 设计篇目第 N.M 节——框架篇目(00~03/06/07/10~20,17~20 为变更指示)在 `../../../NN-*.md`,玩法篇目(04/05/08/09)在 `../../NN-*.md`(如 §10.4 = `../../../10-数据库.md` 第 10.4 节);「★N」= §16.2 验收编号。
> v4 影响:无新增子阶段;2.6 会话级组件层必须先于本阶段完成(交付顺序约束;2.6 属框架线阶段2,`../../../施工计划/阶段2-会话层与模式切换/2.6-会话级组件层.md`)。
> v4.1 影响:无(凡跨会话聚合诉求,先核对 1.6 已绿,见框架线总览 §8:`../../../施工计划/README.md` §8)。

## 1. 阶段目标

把框架从"能切换"推进到"**能玩、能记**":

1. **首模式**:团队死斗(TDM)以纯 Builder 声明落地——零生命周期方法、零引擎改动(§5.1/§5.2-B、§13.1);
2. **端到端**:一条 2 人实测链路跑通"大厅投票 → TDM → 击杀计分 → 分数阈值 → MatchOutcome(WIN)→ defaultTransition=FALLBACK → 结算回大厅(战报展示)"(§16.1-P4、§3.3);
3. **持久化**:数据库基建(HikariCP/虚拟线程/迁移/三态降级,§10.4/§10.3.2/§10.3.6/§14.2)与落库链路(仓储 + write-behind,§10.3.3~§10.3.5)接通。

**出口闸门(不通过不放行)**(施工计划总览 §1 阶段5 行——框架线 `../../../施工计划/README.md` §1、本线 `../README.md` §1;落库半句属框架线 5.3/5.4):
- 2 人实测:大厅投票进 TDM → 击杀计分 → 到阈值 → 结算回大厅(**战报展示**);
- 落库可查、停服不丢(★11)、库不可用降级内存(★10)、**lobby 零落库行**;
- `TdmDeclarationTest` 等新增测试全绿,既有架构测试(§16.2)无一放宽(施工计划总览 §3-4——框架线 `../../../施工计划/README.md` §3-4)。

## 2. 子阶段清单

| 编号 | 标题 | 核心产出 | 出口验收 |
|---|---|---|---|
| 5.1 | TDM模式工厂 | `mode/tdm/**`(TeamDeathmatchModes / KillScoreRule / SuddenDeathComponent / 三判定器)+ `modes/team_deathmatch.yml` + `maps/crossfire.yml` 适配 + `TdmDeclarationTest` | 声明测试全绿(组件表序/阶段表序/EndCondition 优先级/依赖校验,§13.1-8);起服注册表列出 team_deathmatch(§12.1) |
| 5.2 | 端到端验收-大厅到对局 | 2 人实测脚本(步骤化,内嵌 5.2 文档)+ 战报交接往返测试 | §16.1-P4 验收全文通过;★18 战报交接持续验证;热重载不影响进行中实例(§15.2) |
| 5.3 | 数据库基建 | `data/**`(Database / AsyncExecutor / MigrationRunner / DatabaseState / DbConfig)+ `sql/001_init.sql` + shade 打包 + secrets 方案 | 空库自动迁移、重跑幂等、加列演练通过(★12)、拔库起服仅 WARN(★10 机制面) |
| 5.4 | 仓储与write-behind落库 | `PersistencePortImpl` + 三仓储 + `StatsDelta`/UPSERT + `StatsFlusher` + 排行榜缓存 | ★11 停服不丢;★10 端到端;lobby 零落库行;best_score GREATEST 语义测试 |

依赖序:5.1 → 5.2(先有模式才可实测);5.3 → 5.4(先有基建才可落库)。**5.3/5.4 与 5.1/5.2 之间零依赖**(见 §3)。

## 3. 与其他阶段的接口

### 3.1 上游已提供什么

| 来自 | 提供物 | 被本阶段谁消费 |
|---|---|---|
| 阶段1 | `engine/port/PersistencePort` 接口、`util/Threads`、`EngineTicker` | **5.3/5.4 只依赖此**——可与阶段3/4 及 5.1/5.2 全程并行(施工计划总览 §2——框架线 `../../../施工计划/README.md` §2) |
| 阶段2 | lobby(Voting/Countdown/交接)、`switchMode` 两阶段与回退、`/sm` 命令面(Brigadier) | 5.2 的链路起点与切换引擎 |
| 阶段3 | `RoleComponent`(§9)、`RoleKillEvent` 合成(§8.4)、`"role"` 能力标签注册(§8.8) | 5.1 挂 role 组件;5.2 的击杀计分信号 |
| 阶段1(1.5) | `PhaseComponent`/`EndGateComponent`/`RuleBookComponent` 三迁组件(§16.1-P0.5) | 5.1 的 phases/rules/endgate 三行 |
| 阶段4 | Team/Score/Respawn/JoinControl(★v3.2 入场控制)/Objective/Region/RegionGuard/Display/Stats/Disconnect/Visibility 组件族及其 Spec(§6.1) | 5.1 的 Builder 挂表;5.4 的 StatsComponent 账本 |

### 3.2 下游承诺什么

| 承诺给 | 内容 | 消费点 |
|---|---|---|
| 阶段6(6.x) | 5.1 的 `TdmDeclarationTest` 即 §13.1-8 模板,bomb 工厂照抄;5.4 落库链路对新模式零代码(新指标才加列+迁移,§13.2) | 6.1~6.4 |
| 阶段7(7.x) | 追加式迁移纪律与 `schema_version`(★12 复验);战绩查询实现(`HydrokarbsApi.stats/top` 背后,§7.1);5.2 实测脚本作全量回归的复放件 | 7.1/7.4 |

### 3.3 并行性(明确声明)

5.3/5.4 只消费阶段1 冻结的 `PersistencePort` 接口定义,**不 import 任何 mode/component 类型**,可与阶段3/4 及 5.1/5.2 由不同人同时开工。唯一合流点是**阶段出口总验**:5.2 实测若先于 5.4 跑,战报链路走 Handoff 内存通道(§10.2"交接载荷"行),不依赖库;落库断言(★10/★11、lobby 零落库行)待 5.4 就位后补跑。

## 4. 风险与缓解

| 风险 | 缓解 |
|---|---|
| UPSERT `VALUES … AS 别名` 需 MySQL 8.0.19+(§10.3.4 注) | 启动期探测 `SELECT VERSION()`,低版本回退 java.sql 层拼装(只住 repo impl);迁移演练覆盖 |
| 虚拟线程被 `synchronized` 钉扎,呈现"不虚拟"(§10.4 末行) | 调试期统一加 `-Djdk.tracePinnedThreads=short`;JDBC 等待路径禁用 synchronized 锁 |
| 击杀归属边界:箭矢/弹射物不入账、死亡不清账(§14.4) | 计分只消费 `RoleKillEvent`(双源交叉验证在阶段3 合成器完成);5.2 实测含"摔死/自杀不计分"负例 |
| TDM 需 `"role"` 能力,角色插件缺席则开局被拒(§8.8) | 5.2 环境清单把角色插件列为必备;缺席分支作为回归项(§16.1-P2 复测) |
| HikariCP/驱动漏 shade → 运行期 `ClassNotFoundException`(§12.1 注) | shadow 打包 + `jar tf` 抽查 + 起服实测闭环兜底 |
| MATCHEND 阶段与 EndGate"命中即 end"的先后口径设计未明示(§5.1 阶段链 vs §3.3/§3.4) | 按 §3.3 权威交接流落地(命中即 end、战报经 LAST_SUMMARY 在大厅展示);差异点列入 5.1 与本文件「待裁决问题」 |
| 5.2 实测与 5.4 合流时序 | 见 §3.3;实测脚本第 14 步预留落库复验挂点 |

## 待裁决问题(汇总)

1. **MATCHEND 阶段语义**:§5.1 阶段链含 MatchEnd,而 §3.3 权威流与 §3.4 代码是"判定命中即 `end()`、下一 tick 切换"——MatchEnd 是被跳过还是先播报再结束?详见 5.1 文末。
2. **match_history 写入触发方**:§10.3.1 的 PersistencePortImpl 方法面只列 stats/top/accumulate/flushNow,`MatchHistoryRepository.record`(§10.3.3)无显式调用方。详见 5.4 文末。
3. **role_stats 写入路径**:§8.5 说角色战绩经 StatsComponent→StatsFlusher 落库,但 §10.3.4 的 StatsDelta 形状无 role 维度,表达不了 `(uuid, role_id, mode_id)` 主键。详见 5.4 文末。
