package com.hydrokarbs.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import com.hydrokarbs.engine.AttributeBag;
import com.hydrokarbs.engine.component.MatchModeComponent;
import com.hydrokarbs.engine.component.SessionComponent;
import com.hydrokarbs.engine.event.MatchModeInstanceEvent;
import com.hydrokarbs.engine.event.MatchModeInstanceEventBus;
import com.hydrokarbs.engine.event.MatchModeInstanceEventPort;
import com.hydrokarbs.engine.event.SessionEvent;
import com.hydrokarbs.engine.event.SessionEventBus;
import com.hydrokarbs.engine.instance.MatchModeInstanceView;
import com.hydrokarbs.engine.instance.Participant;
import com.hydrokarbs.engine.mode.MatchMode;
import com.hydrokarbs.engine.mode.MatchModeMetadata;
import com.hydrokarbs.engine.mode.MatchModeProvider;
import com.hydrokarbs.engine.port.JoinAdmission;
import com.hydrokarbs.engine.port.JoinContext;
import com.hydrokarbs.engine.port.JoinDecision;
import com.hydrokarbs.engine.port.MatchModeInstanceAccess;
import com.hydrokarbs.engine.port.MatchModeInstancePort;
import com.hydrokarbs.engine.port.MatchModeInstanceServices;
import com.hydrokarbs.engine.port.SessionEventPort;
import com.hydrokarbs.engine.port.SessionPort;
import com.hydrokarbs.engine.port.SessionServices;
import com.hydrokarbs.engine.port.SessionView;
import com.hydrokarbs.engine.session.DissolveReason;
import com.hydrokarbs.engine.session.EndResult;
import com.hydrokarbs.engine.session.Handoff;
import com.hydrokarbs.engine.session.HandoffView;
import com.hydrokarbs.engine.session.JoinResult;
import com.hydrokarbs.engine.session.LeaveReason;
import com.hydrokarbs.engine.session.MatchModeConfig;
import com.hydrokarbs.engine.session.MatchModeSelection;
import com.hydrokarbs.engine.session.SessionId;
import com.hydrokarbs.engine.session.SwitchResult;
import com.hydrokarbs.engine.session.TransitionIntent;
import com.hydrokarbs.component.endgate.EndCondition;
import com.hydrokarbs.component.endgate.EndGateComponent;
import com.hydrokarbs.component.endgate.GateVerdict;
import com.hydrokarbs.component.endgate.MatchOutcome;
import com.hydrokarbs.component.phase.PhaseChanged;
import com.hydrokarbs.component.phase.PhaseComponent;
import com.hydrokarbs.component.phase.PhaseSpec;
import com.hydrokarbs.component.rule.Rule;
import com.hydrokarbs.component.rule.RuleBookComponent;
import com.hydrokarbs.component.rule.RuleContext;
import com.hydrokarbs.component.session.EmptyArenaComponent;
import com.hydrokarbs.map.MapDefinition;
import com.hydrokarbs.api.view.MapInfo;
import com.hydrokarbs.api.view.MatchModeInfo;
import com.hydrokarbs.api.view.SessionInfo;

/**
 * API 编译面封闭(★44;07 §7.2):双断言——①{@code MiniGameApi} 签名只引用
 * 白名单类型 + JDK + Bukkit/Adventure 平台原语;②白名单公开签名<b>只增不改</b>
 * (反射快照与 {@link #BASELINE} 全等;删/改即红,新增须追加基线行并说明)。
 *
 * <p>白名单逐包清单与出处见 {@link #WHITELIST} 行内注释(07 §7.2/19-D6 口径,
 * 新增条目须同步注释出处)。永不导出的反面清单:SessionService 族、EngineTicker、
 * WorldPort、两 Registry 族、bridge 包、SessionSpec(19-D14)、各 Impl——由断言①
 * 间接守卫,直改公开面归 review 条款(★49)。编译器生成面(equals/hashCode/
 * toString/values/valueOf)一并入册。
 */
class ApiSurfaceTest {

    /** 白名单逐包清单(出处见类注;顺序 = 注释块序,无语义)。 */
    private static final List<Class<?>> WHITELIST = List.of(
            // ── api/**(07 §7.1 全部公开类型;★v1.6 起 SessionComponentProvider 出册) ──
            MiniGameApi.class, EventSubscription.class, SessionRecipe.class,
            SessionEventView.class,
            SessionEventView.Created.class, SessionEventView.MemberJoined.class,
            SessionEventView.MemberLeft.class, SessionEventView.MemberReconnected.class,
            SessionEventView.ModeSwitchStarted.class, SessionEventView.ModeSwitchCompleted.class,
            SessionEventView.Dissolved.class, SessionEventView.ComponentQuarantined.class,
            SessionInfo.class, MatchModeInfo.class, MapInfo.class,
            // ── engine.mode/**(19-D6;★v1.13/31 号:DefaultTransition 出册) ──
            MatchMode.class, MatchMode.Builder.class, MatchMode.ComponentEntry.class,
            MatchModeMetadata.class, MatchModeProvider.class,
            // ── 两级组件基类 + Specification/Snapshot/Factory(不含 Registry,19-D6) ──
            MatchModeComponent.class, MatchModeComponent.Specification.class,
            MatchModeComponent.Specification.Snapshot.class, MatchModeComponent.Factory.class,
            SessionComponent.class, SessionComponent.Specification.class,
            SessionComponent.Specification.Snapshot.class, SessionComponent.Factory.class,
            // ── engine.session 值类型(D6 九件 + HandoffView/MatchModeConfig 签名闭包;SessionSpec 不在册,19-D14) ──
            Handoff.class, Handoff.Key.class, Handoff.Builder.class, HandoffView.class,
            EndResult.class, TransitionIntent.class, MatchModeSelection.class,
            SwitchResult.class, SwitchResult.Succeeded.class, SwitchResult.Rejected.class,
            SwitchResult.Reason.class, JoinResult.class, JoinResult.Joined.class,
            JoinResult.Rejected.class, LeaveReason.class, DissolveReason.class,
            SessionId.class, MatchModeConfig.class,
            // ── engine.port 契约(19-D6 + JoinContext/JoinDecision 签名闭包;
            //    AttributeBag 实为 engine 根,声明词料容器) ──
            MatchModeInstanceServices.class, MatchModeInstancePort.class,
            SessionServices.class, SessionPort.class, MatchModeInstanceAccess.class,
            SessionView.class, JoinAdmission.class, JoinContext.class,
            JoinDecision.class, JoinDecision.Kind.class, AttributeBag.class,
            AttributeBag.Key.class, AttributeBag.Builder.class,
            // ── engine.event/**(19-D6 整包口径) ──
            SessionEvent.class, SessionEvent.SessionCreated.class,
            SessionEvent.MemberJoined.class, SessionEvent.MemberLeft.class,
            SessionEvent.MemberReconnected.class, SessionEvent.ModeSwitchStarted.class,
            SessionEvent.ModeSwitchCompleted.class, SessionEvent.SessionDissolved.class,
            SessionEvent.SessionComponentQuarantined.class, SessionEventPort.class,
            SessionEventBus.class, MatchModeInstanceEvent.class,
            MatchModeInstanceEvent.Structural.class, MatchModeInstanceEvent.MatchModeInstanceStarted.class,
            MatchModeInstanceEvent.MatchModeInstanceEnded.class, MatchModeInstanceEvent.ParticipantJoined.class,
            MatchModeInstanceEvent.ParticipantLeft.class, MatchModeInstanceEvent.ParticipantSpawned.class,
            MatchModeInstanceEvent.ParticipantKilled.class, MatchModeInstanceEvent.Custom.class,
            MatchModeInstanceEventPort.class, MatchModeInstanceEventBus.class,
            // ── engine.instance 只读视图(component/ 数据件签名闭包补充) ──
            MatchModeInstanceView.class, Participant.class,
            // ── component/** 公开数据件与事件类型(19-D6) ──
            EndCondition.class, GateVerdict.class, MatchOutcome.class, MatchOutcome.Kind.class,
            EndGateComponent.class, EndGateComponent.Spec.class, PhaseChanged.class,
            PhaseSpec.class, PhaseSpec.Kind.class, PhaseComponent.class, PhaseComponent.Spec.class,
            PhaseComponent.PhaseCountdown.class, Rule.class, RuleContext.class,
            RuleBookComponent.class, RuleBookComponent.Spec.class,
            EmptyArenaComponent.class, EmptyArenaComponent.Spec.class,
            // ── map/ 读面(19-D6;registerMap 签名必需;★v1.8 瘦身后
            //    Bounds/RegionSet/NamedRegion/SpawnPoint/InteractPoint 随瘦身出册;
            //    metadata 词料容器归 engine 根 AttributeBag) ──
            MapDefinition.class);

    // ── 断言 ①:MiniGameApi 签名零非白名单类型(★44 前半) ────────────────

    @Test
    void apiMethodSignaturesReferenceOnlyWhitelistTypes() {
        for (Method method : MiniGameApi.class.getDeclaredMethods()) {
            assertReferencedTypesAllowed(method.getName() + " 返回", method.getGenericReturnType());
            for (Type parameter : method.getGenericParameterTypes()) {
                assertReferencedTypesAllowed(method.getName() + " 参数", parameter);
            }
        }
    }

    /** 递归展开泛型(ParemterizedType/数组/通配符/类型变量),逐叶类型判定允许集。 */
    private static void assertReferencedTypesAllowed(String where, Type type) {
        if (type instanceof Class<?> raw) {
            assertTrue(isAllowed(raw), where + " 引用非白名单类型: " + raw.getName()
                    + "(★44;白名单见本测试类注,新增面 = 同步扩白名单 + PR 说明)");
            return;
        }
        if (type instanceof ParameterizedType parameterized) {
            assertReferencedTypesAllowed(where, parameterized.getRawType());
            for (Type argument : parameterized.getActualTypeArguments()) {
                assertReferencedTypesAllowed(where, argument);
            }
            return;
        }
        if (type instanceof java.lang.reflect.GenericArrayType array) {
            assertReferencedTypesAllowed(where, array.getGenericComponentType());
            return;
        }
        // ★v1.6 技术修正(命题不变、非放宽):通配符/类型变量无「类型名」可判,
        // 递归其上界——上界引用类型仍须过白名单(registerSessionComponents 的
        // List<SessionComponent.Specification<?>> 是首个带通配符实参的签名)。
        if (type instanceof java.lang.reflect.WildcardType wildcard) {
            for (Type bound : wildcard.getUpperBounds()) {
                assertReferencedTypesAllowed(where, bound);
            }
            for (Type bound : wildcard.getLowerBounds()) {
                assertReferencedTypesAllowed(where, bound);
            }
            return;
        }
        if (type instanceof java.lang.reflect.TypeVariable<?> variable) {
            for (Type bound : variable.getBounds()) {
                assertReferencedTypesAllowed(where, bound);
            }
            return;
        }
        throw new AssertionError(where + " 出现未支持的反射形态: " + type);
    }

    /** 允许集:白名单 ∪ JDK(java./javax.)∪ Bukkit/Adventure 平台原语 ∪ 基本类型。 */
    private static boolean isAllowed(Class<?> type) {
        if (type.isPrimitive()) {
            return true;
        }
        String name = type.getName();
        if (name.startsWith("java.") || name.startsWith("javax.")) {
            return true;                            // JDK
        }
        if (name.startsWith("org.bukkit.") || name.startsWith("net.kyori.")
                || name.startsWith("io.papermc.")) {
            return true;                            // Paper 平台原语(Plugin/Material/Component)
        }
        return WHITELIST.contains(type);
    }

    // ── 断言 ②:白名单公开签名快照只增不改(★44 后半;首建基线 2026-10-25) ──

    /** 首建快照文本(2026-10-25);各版本快照重录沿革见 24/25/26/30/31/32 号修正案;
     *  本次 = 删除 MatchModeMeta、元数据改 AttributeBag 承载(基线行同步重录)。
     *  由 {@code snapshotLines()} 同算法生成后粘贴;须先于 BASELINE 声明。 */
    private static final String BASELINE_TEXT = """
com.hydrokarbs.api.EventSubscription#close():void
com.hydrokarbs.api.MiniGameApi#createSession(com.hydrokarbs.api.SessionRecipe):com.hydrokarbs.api.view.SessionInfo
com.hydrokarbs.api.MiniGameApi#joinSession(java.lang.String,java.util.UUID):com.hydrokarbs.engine.session.JoinResult
com.hydrokarbs.api.MiniGameApi#leaveSession(java.util.UUID,com.hydrokarbs.engine.session.LeaveReason):void
com.hydrokarbs.api.MiniGameApi#maps():java.util.List
com.hydrokarbs.api.MiniGameApi#mode(java.lang.String):java.util.Optional
com.hydrokarbs.api.MiniGameApi#modes():java.util.List
com.hydrokarbs.api.MiniGameApi#registerMap(org.bukkit.plugin.Plugin,com.hydrokarbs.map.MapDefinition):com.hydrokarbs.api.view.MapInfo
com.hydrokarbs.api.MiniGameApi#registerMode(org.bukkit.plugin.Plugin,com.hydrokarbs.engine.mode.MatchModeProvider):com.hydrokarbs.api.view.MatchModeInfo
com.hydrokarbs.api.MiniGameApi#registerSessionComponents(org.bukkit.plugin.Plugin,java.util.List):void
com.hydrokarbs.api.MiniGameApi#session(java.lang.String):java.util.Optional
com.hydrokarbs.api.MiniGameApi#sessionOf(java.util.UUID):java.util.Optional
com.hydrokarbs.api.MiniGameApi#sessions():java.util.List
com.hydrokarbs.api.MiniGameApi#subscribeSessionEvents(java.util.function.Consumer):com.hydrokarbs.api.EventSubscription
com.hydrokarbs.api.MiniGameApi#switchMode(java.lang.String,com.hydrokarbs.engine.session.MatchModeSelection,com.hydrokarbs.engine.session.Handoff):java.util.concurrent.CompletableFuture
com.hydrokarbs.api.MiniGameApi#unregisterMap(org.bukkit.plugin.Plugin,java.lang.String):void
com.hydrokarbs.api.MiniGameApi#unregisterMode(org.bukkit.plugin.Plugin,java.lang.String):void
com.hydrokarbs.api.MiniGameApi#unregisterSessionComponents(org.bukkit.plugin.Plugin):void
com.hydrokarbs.api.SessionEventView#tick():long
com.hydrokarbs.api.SessionEventView$ComponentQuarantined#cause():java.lang.String
com.hydrokarbs.api.SessionEventView$ComponentQuarantined#componentId():java.lang.String
com.hydrokarbs.api.SessionEventView$ComponentQuarantined#equals(java.lang.Object):boolean
com.hydrokarbs.api.SessionEventView$ComponentQuarantined#hashCode():int
com.hydrokarbs.api.SessionEventView$ComponentQuarantined#session():com.hydrokarbs.engine.session.SessionId
com.hydrokarbs.api.SessionEventView$ComponentQuarantined#tick():long
com.hydrokarbs.api.SessionEventView$ComponentQuarantined#toString():java.lang.String
com.hydrokarbs.api.SessionEventView$Created#equals(java.lang.Object):boolean
com.hydrokarbs.api.SessionEventView$Created#hashCode():int
com.hydrokarbs.api.SessionEventView$Created#session():com.hydrokarbs.engine.session.SessionId
com.hydrokarbs.api.SessionEventView$Created#tick():long
com.hydrokarbs.api.SessionEventView$Created#toString():java.lang.String
com.hydrokarbs.api.SessionEventView$Dissolved#equals(java.lang.Object):boolean
com.hydrokarbs.api.SessionEventView$Dissolved#hashCode():int
com.hydrokarbs.api.SessionEventView$Dissolved#reason():java.lang.String
com.hydrokarbs.api.SessionEventView$Dissolved#session():com.hydrokarbs.engine.session.SessionId
com.hydrokarbs.api.SessionEventView$Dissolved#tick():long
com.hydrokarbs.api.SessionEventView$Dissolved#toString():java.lang.String
com.hydrokarbs.api.SessionEventView$MemberJoined#equals(java.lang.Object):boolean
com.hydrokarbs.api.SessionEventView$MemberJoined#hashCode():int
com.hydrokarbs.api.SessionEventView$MemberJoined#player():java.util.UUID
com.hydrokarbs.api.SessionEventView$MemberJoined#session():com.hydrokarbs.engine.session.SessionId
com.hydrokarbs.api.SessionEventView$MemberJoined#tick():long
com.hydrokarbs.api.SessionEventView$MemberJoined#toString():java.lang.String
com.hydrokarbs.api.SessionEventView$MemberLeft#equals(java.lang.Object):boolean
com.hydrokarbs.api.SessionEventView$MemberLeft#hashCode():int
com.hydrokarbs.api.SessionEventView$MemberLeft#player():java.util.UUID
com.hydrokarbs.api.SessionEventView$MemberLeft#reason():java.lang.String
com.hydrokarbs.api.SessionEventView$MemberLeft#session():com.hydrokarbs.engine.session.SessionId
com.hydrokarbs.api.SessionEventView$MemberLeft#tick():long
com.hydrokarbs.api.SessionEventView$MemberLeft#toString():java.lang.String
com.hydrokarbs.api.SessionEventView$MemberReconnected#equals(java.lang.Object):boolean
com.hydrokarbs.api.SessionEventView$MemberReconnected#hashCode():int
com.hydrokarbs.api.SessionEventView$MemberReconnected#player():java.util.UUID
com.hydrokarbs.api.SessionEventView$MemberReconnected#session():com.hydrokarbs.engine.session.SessionId
com.hydrokarbs.api.SessionEventView$MemberReconnected#tick():long
com.hydrokarbs.api.SessionEventView$MemberReconnected#toString():java.lang.String
com.hydrokarbs.api.SessionEventView$ModeSwitchCompleted#equals(java.lang.Object):boolean
com.hydrokarbs.api.SessionEventView$ModeSwitchCompleted#from():java.lang.String
com.hydrokarbs.api.SessionEventView$ModeSwitchCompleted#hashCode():int
com.hydrokarbs.api.SessionEventView$ModeSwitchCompleted#ok():boolean
com.hydrokarbs.api.SessionEventView$ModeSwitchCompleted#session():com.hydrokarbs.engine.session.SessionId
com.hydrokarbs.api.SessionEventView$ModeSwitchCompleted#tick():long
com.hydrokarbs.api.SessionEventView$ModeSwitchCompleted#to():java.lang.String
com.hydrokarbs.api.SessionEventView$ModeSwitchCompleted#toString():java.lang.String
com.hydrokarbs.api.SessionEventView$ModeSwitchStarted#equals(java.lang.Object):boolean
com.hydrokarbs.api.SessionEventView$ModeSwitchStarted#from():java.lang.String
com.hydrokarbs.api.SessionEventView$ModeSwitchStarted#hashCode():int
com.hydrokarbs.api.SessionEventView$ModeSwitchStarted#session():com.hydrokarbs.engine.session.SessionId
com.hydrokarbs.api.SessionEventView$ModeSwitchStarted#tick():long
com.hydrokarbs.api.SessionEventView$ModeSwitchStarted#to():java.lang.String
com.hydrokarbs.api.SessionEventView$ModeSwitchStarted#toString():java.lang.String
com.hydrokarbs.api.SessionRecipe#equals(java.lang.Object):boolean
com.hydrokarbs.api.SessionRecipe#extraComponents():java.util.List
com.hydrokarbs.api.SessionRecipe#hashCode():int
com.hydrokarbs.api.SessionRecipe#initialMapId():java.util.Optional
com.hydrokarbs.api.SessionRecipe#initialModeId():java.lang.String
com.hydrokarbs.api.SessionRecipe#mapPool():java.util.List
com.hydrokarbs.api.SessionRecipe#of(java.lang.String,java.lang.String):com.hydrokarbs.api.SessionRecipe
com.hydrokarbs.api.SessionRecipe#toString():java.lang.String
com.hydrokarbs.api.view.MapInfo#equals(java.lang.Object):boolean
com.hydrokarbs.api.view.MapInfo#hashCode():int
com.hydrokarbs.api.view.MapInfo#id():java.lang.String
com.hydrokarbs.api.view.MapInfo#resetStrategy():java.lang.String
com.hydrokarbs.api.view.MapInfo#tags():java.util.Set
com.hydrokarbs.api.view.MapInfo#toString():java.lang.String
com.hydrokarbs.api.view.MatchModeInfo#description():java.util.List
com.hydrokarbs.api.view.MatchModeInfo#displayName():java.lang.String
com.hydrokarbs.api.view.MatchModeInfo#equals(java.lang.Object):boolean
com.hydrokarbs.api.view.MatchModeInfo#hashCode():int
com.hydrokarbs.api.view.MatchModeInfo#icon():org.bukkit.Material
com.hydrokarbs.api.view.MatchModeInfo#id():java.lang.String
com.hydrokarbs.api.view.MatchModeInfo#mapTags():java.util.Set
com.hydrokarbs.api.view.MatchModeInfo#maxParticipants():int
com.hydrokarbs.api.view.MatchModeInfo#minParticipants():int
com.hydrokarbs.api.view.MatchModeInfo#recordsHistory():boolean
com.hydrokarbs.api.view.MatchModeInfo#toString():java.lang.String
com.hydrokarbs.api.view.SessionInfo#equals(java.lang.Object):boolean
com.hydrokarbs.api.view.SessionInfo#hashCode():int
com.hydrokarbs.api.view.SessionInfo#mapId():java.lang.String
com.hydrokarbs.api.view.SessionInfo#members():java.util.List
com.hydrokarbs.api.view.SessionInfo#modeId():java.lang.String
com.hydrokarbs.api.view.SessionInfo#sessionId():java.lang.String
com.hydrokarbs.api.view.SessionInfo#state():java.lang.String
com.hydrokarbs.api.view.SessionInfo#toString():java.lang.String
com.hydrokarbs.component.endgate.EndCondition#evaluate(com.hydrokarbs.engine.instance.MatchModeInstanceView):java.util.Optional
com.hydrokarbs.component.endgate.EndCondition#priority():int
com.hydrokarbs.component.endgate.EndGateComponent#HANDOFF_OUTCOME:com.hydrokarbs.engine.session.Handoff$Key
com.hydrokarbs.component.endgate.EndGateComponent#onHandoff(com.hydrokarbs.engine.session.Handoff$Builder):void
com.hydrokarbs.component.endgate.EndGateComponent#outcome():java.util.Optional
com.hydrokarbs.component.endgate.EndGateComponent#update():void
com.hydrokarbs.component.endgate.EndGateComponent$Spec#create(java.lang.String,com.hydrokarbs.engine.port.MatchModeInstanceServices):com.hydrokarbs.component.endgate.EndGateComponent
com.hydrokarbs.component.endgate.GateVerdict#equals(java.lang.Object):boolean
com.hydrokarbs.component.endgate.GateVerdict#hashCode():int
com.hydrokarbs.component.endgate.GateVerdict#intent():java.util.Optional
com.hydrokarbs.component.endgate.GateVerdict#outcome():com.hydrokarbs.component.endgate.MatchOutcome
com.hydrokarbs.component.endgate.GateVerdict#toString():java.lang.String
com.hydrokarbs.component.endgate.MatchOutcome#equals(java.lang.Object):boolean
com.hydrokarbs.component.endgate.MatchOutcome#hashCode():int
com.hydrokarbs.component.endgate.MatchOutcome#kind():com.hydrokarbs.component.endgate.MatchOutcome$Kind
com.hydrokarbs.component.endgate.MatchOutcome#reason():java.lang.String
com.hydrokarbs.component.endgate.MatchOutcome#toString():java.lang.String
com.hydrokarbs.component.endgate.MatchOutcome#winnerId():java.lang.String
com.hydrokarbs.component.endgate.MatchOutcome$Kind#ABORTED:com.hydrokarbs.component.endgate.MatchOutcome$Kind
com.hydrokarbs.component.endgate.MatchOutcome$Kind#DRAW:com.hydrokarbs.component.endgate.MatchOutcome$Kind
com.hydrokarbs.component.endgate.MatchOutcome$Kind#NEXT:com.hydrokarbs.component.endgate.MatchOutcome$Kind
com.hydrokarbs.component.endgate.MatchOutcome$Kind#WIN:com.hydrokarbs.component.endgate.MatchOutcome$Kind
com.hydrokarbs.component.endgate.MatchOutcome$Kind#valueOf(java.lang.String):com.hydrokarbs.component.endgate.MatchOutcome$Kind
com.hydrokarbs.component.endgate.MatchOutcome$Kind#values():[Lcom.hydrokarbs.component.endgate.MatchOutcome$Kind;
com.hydrokarbs.component.phase.PhaseChanged#elapsedTicks():long
com.hydrokarbs.component.phase.PhaseChanged#equals(java.lang.Object):boolean
com.hydrokarbs.component.phase.PhaseChanged#fromPhaseId():java.lang.String
com.hydrokarbs.component.phase.PhaseChanged#hashCode():int
com.hydrokarbs.component.phase.PhaseChanged#occurredAtMillis():long
com.hydrokarbs.component.phase.PhaseChanged#tick():int
com.hydrokarbs.component.phase.PhaseChanged#toPhaseId():java.lang.String
com.hydrokarbs.component.phase.PhaseChanged#toString():java.lang.String
com.hydrokarbs.component.phase.PhaseComponent#countdown():java.util.Optional
com.hydrokarbs.component.phase.PhaseComponent#currentExpired():boolean
com.hydrokarbs.component.phase.PhaseComponent#currentId():java.lang.String
com.hydrokarbs.component.phase.PhaseComponent#jump(java.lang.String):void
com.hydrokarbs.component.phase.PhaseComponent#start():void
com.hydrokarbs.component.phase.PhaseComponent#update():void
com.hydrokarbs.component.phase.PhaseComponent$PhaseCountdown#equals(java.lang.Object):boolean
com.hydrokarbs.component.phase.PhaseComponent$PhaseCountdown#hashCode():int
com.hydrokarbs.component.phase.PhaseComponent$PhaseCountdown#remainingTicks():int
com.hydrokarbs.component.phase.PhaseComponent$PhaseCountdown#toString():java.lang.String
com.hydrokarbs.component.phase.PhaseComponent$PhaseCountdown#totalTicks():int
com.hydrokarbs.component.phase.PhaseComponent$Spec#create(java.lang.String,com.hydrokarbs.engine.port.MatchModeInstanceServices):com.hydrokarbs.component.phase.PhaseComponent
com.hydrokarbs.component.phase.PhaseSpec#durationTicks():java.lang.Integer
com.hydrokarbs.component.phase.PhaseSpec#entryWhen():java.util.function.Predicate
com.hydrokarbs.component.phase.PhaseSpec#equals(java.lang.Object):boolean
com.hydrokarbs.component.phase.PhaseSpec#hashCode():int
com.hydrokarbs.component.phase.PhaseSpec#id():java.lang.String
com.hydrokarbs.component.phase.PhaseSpec#kind():com.hydrokarbs.component.phase.PhaseSpec$Kind
com.hydrokarbs.component.phase.PhaseSpec#toString():java.lang.String
com.hydrokarbs.component.phase.PhaseSpec$Kind#AWAIT:com.hydrokarbs.component.phase.PhaseSpec$Kind
com.hydrokarbs.component.phase.PhaseSpec$Kind#TIMED:com.hydrokarbs.component.phase.PhaseSpec$Kind
com.hydrokarbs.component.phase.PhaseSpec$Kind#valueOf(java.lang.String):com.hydrokarbs.component.phase.PhaseSpec$Kind
com.hydrokarbs.component.phase.PhaseSpec$Kind#values():[Lcom.hydrokarbs.component.phase.PhaseSpec$Kind;
com.hydrokarbs.component.rule.Rule#equals(java.lang.Object):boolean
com.hydrokarbs.component.rule.Rule#hashCode():int
com.hydrokarbs.component.rule.Rule#id():java.lang.String
com.hydrokarbs.component.rule.Rule#then():java.util.function.Consumer
com.hydrokarbs.component.rule.Rule#toString():java.lang.String
com.hydrokarbs.component.rule.Rule#trigger():java.util.function.Predicate
com.hydrokarbs.component.rule.Rule#when():java.util.function.Predicate
com.hydrokarbs.component.rule.RuleBookComponent#onEvent(com.hydrokarbs.engine.event.MatchModeInstanceEvent):void
com.hydrokarbs.component.rule.RuleBookComponent$Spec#create(java.lang.String,com.hydrokarbs.engine.port.MatchModeInstanceServices):com.hydrokarbs.component.rule.RuleBookComponent
com.hydrokarbs.component.rule.RuleContext#event():com.hydrokarbs.engine.event.MatchModeInstanceEvent
com.hydrokarbs.component.rule.RuleContext#eventAs(java.lang.Class):com.hydrokarbs.engine.event.MatchModeInstanceEvent
com.hydrokarbs.component.session.EmptyArenaComponent#dissolveRequested():boolean
com.hydrokarbs.component.session.EmptyArenaComponent#emptyTicks():long
com.hydrokarbs.component.session.EmptyArenaComponent#start():void
com.hydrokarbs.component.session.EmptyArenaComponent#stop():void
com.hydrokarbs.component.session.EmptyArenaComponent#update():void
com.hydrokarbs.component.session.EmptyArenaComponent$Spec#afterTicks():long
com.hydrokarbs.component.session.EmptyArenaComponent$Spec#create(java.lang.String,com.hydrokarbs.engine.port.SessionServices):com.hydrokarbs.component.session.EmptyArenaComponent
com.hydrokarbs.engine.AttributeBag#empty():com.hydrokarbs.engine.AttributeBag
com.hydrokarbs.engine.AttributeBag#get(com.hydrokarbs.engine.AttributeBag$Key):java.util.Optional
com.hydrokarbs.engine.AttributeBag#getOrDefault(com.hydrokarbs.engine.AttributeBag$Key,java.lang.Object):java.lang.Object
com.hydrokarbs.engine.AttributeBag#has(com.hydrokarbs.engine.AttributeBag$Key):boolean
com.hydrokarbs.engine.AttributeBag$Builder#build():com.hydrokarbs.engine.AttributeBag
com.hydrokarbs.engine.AttributeBag$Builder#put(com.hydrokarbs.engine.AttributeBag$Key,java.lang.Object):com.hydrokarbs.engine.AttributeBag$Builder
com.hydrokarbs.engine.AttributeBag$Key#equals(java.lang.Object):boolean
com.hydrokarbs.engine.AttributeBag$Key#hashCode():int
com.hydrokarbs.engine.AttributeBag$Key#name():java.lang.String
com.hydrokarbs.engine.AttributeBag$Key#of(java.lang.String,java.lang.Class):com.hydrokarbs.engine.AttributeBag$Key
com.hydrokarbs.engine.AttributeBag$Key#toString():java.lang.String
com.hydrokarbs.engine.AttributeBag$Key#type():java.lang.Class
com.hydrokarbs.engine.component.MatchModeComponent#awake():void
com.hydrokarbs.engine.component.MatchModeComponent#id():java.lang.String
com.hydrokarbs.engine.component.MatchModeComponent#onEvent(com.hydrokarbs.engine.event.MatchModeInstanceEvent):void
com.hydrokarbs.engine.component.MatchModeComponent#onHandoff(com.hydrokarbs.engine.session.Handoff$Builder):void
com.hydrokarbs.engine.component.MatchModeComponent#start():void
com.hydrokarbs.engine.component.MatchModeComponent#stop():void
com.hydrokarbs.engine.component.MatchModeComponent#update():void
com.hydrokarbs.engine.component.MatchModeComponent$Factory#create(java.lang.String,com.hydrokarbs.engine.port.MatchModeInstanceServices):com.hydrokarbs.engine.component.MatchModeComponent
com.hydrokarbs.engine.component.MatchModeComponent$Specification#bindId(java.lang.String):void
com.hydrokarbs.engine.component.MatchModeComponent$Specification#checkMap(com.hydrokarbs.map.MapDefinition,com.hydrokarbs.engine.session.MatchModeConfig):java.util.List
com.hydrokarbs.engine.component.MatchModeComponent$Specification#create(java.lang.String,com.hydrokarbs.engine.port.MatchModeInstanceServices):com.hydrokarbs.engine.component.MatchModeComponent
com.hydrokarbs.engine.component.MatchModeComponent$Specification#freeze():com.hydrokarbs.engine.component.MatchModeComponent$Specification$Snapshot
com.hydrokarbs.engine.component.MatchModeComponent$Specification#providedType():java.lang.Class
com.hydrokarbs.engine.component.MatchModeComponent$Specification#requires(java.lang.Class):com.hydrokarbs.engine.component.MatchModeComponent$Specification
com.hydrokarbs.engine.component.MatchModeComponent$Specification#requiresOptional(java.lang.Class):com.hydrokarbs.engine.component.MatchModeComponent$Specification
com.hydrokarbs.engine.component.MatchModeComponent$Specification$Snapshot#descriptorLabel():java.lang.String
com.hydrokarbs.engine.component.MatchModeComponent$Specification$Snapshot#equals(java.lang.Object):boolean
com.hydrokarbs.engine.component.MatchModeComponent$Specification$Snapshot#factory():com.hydrokarbs.engine.component.MatchModeComponent$Factory
com.hydrokarbs.engine.component.MatchModeComponent$Specification$Snapshot#hashCode():int
com.hydrokarbs.engine.component.MatchModeComponent$Specification$Snapshot#optionalTypes():java.util.List
com.hydrokarbs.engine.component.MatchModeComponent$Specification$Snapshot#providedType():java.lang.Class
com.hydrokarbs.engine.component.MatchModeComponent$Specification$Snapshot#requiredTypes():java.util.List
com.hydrokarbs.engine.component.MatchModeComponent$Specification$Snapshot#toString():java.lang.String
com.hydrokarbs.engine.component.SessionComponent#awake():void
com.hydrokarbs.engine.component.SessionComponent#id():java.lang.String
com.hydrokarbs.engine.component.SessionComponent#onEvent(com.hydrokarbs.engine.event.SessionEvent):void
com.hydrokarbs.engine.component.SessionComponent#start():void
com.hydrokarbs.engine.component.SessionComponent#stop():void
com.hydrokarbs.engine.component.SessionComponent#update():void
com.hydrokarbs.engine.component.SessionComponent$Factory#create(java.lang.String,com.hydrokarbs.engine.port.SessionServices):com.hydrokarbs.engine.component.SessionComponent
com.hydrokarbs.engine.component.SessionComponent$Specification#create(java.lang.String,com.hydrokarbs.engine.port.SessionServices):com.hydrokarbs.engine.component.SessionComponent
com.hydrokarbs.engine.component.SessionComponent$Specification#freeze():com.hydrokarbs.engine.component.SessionComponent$Specification$Snapshot
com.hydrokarbs.engine.component.SessionComponent$Specification#providedType():java.lang.Class
com.hydrokarbs.engine.component.SessionComponent$Specification#requires(java.lang.Class):com.hydrokarbs.engine.component.SessionComponent$Specification
com.hydrokarbs.engine.component.SessionComponent$Specification#requiresOptional(java.lang.Class):com.hydrokarbs.engine.component.SessionComponent$Specification
com.hydrokarbs.engine.component.SessionComponent$Specification$Snapshot#descriptorLabel():java.lang.String
com.hydrokarbs.engine.component.SessionComponent$Specification$Snapshot#equals(java.lang.Object):boolean
com.hydrokarbs.engine.component.SessionComponent$Specification$Snapshot#factory():com.hydrokarbs.engine.component.SessionComponent$Factory
com.hydrokarbs.engine.component.SessionComponent$Specification$Snapshot#hashCode():int
com.hydrokarbs.engine.component.SessionComponent$Specification$Snapshot#optionalTypes():java.util.List
com.hydrokarbs.engine.component.SessionComponent$Specification$Snapshot#providedType():java.lang.Class
com.hydrokarbs.engine.component.SessionComponent$Specification$Snapshot#requiredTypes():java.util.List
com.hydrokarbs.engine.component.SessionComponent$Specification$Snapshot#toString():java.lang.String
com.hydrokarbs.engine.event.MatchModeInstanceEvent#is(java.lang.Class):boolean
com.hydrokarbs.engine.event.MatchModeInstanceEvent#occurredAtMillis():long
com.hydrokarbs.engine.event.MatchModeInstanceEvent#tick():int
com.hydrokarbs.engine.event.MatchModeInstanceEvent$Custom#equals(java.lang.Object):boolean
com.hydrokarbs.engine.event.MatchModeInstanceEvent$Custom#hashCode():int
com.hydrokarbs.engine.event.MatchModeInstanceEvent$Custom#occurredAtMillis():long
com.hydrokarbs.engine.event.MatchModeInstanceEvent$Custom#of(java.lang.String):com.hydrokarbs.engine.event.MatchModeInstanceEvent$Custom
com.hydrokarbs.engine.event.MatchModeInstanceEvent$Custom#payload():java.util.Map
com.hydrokarbs.engine.event.MatchModeInstanceEvent$Custom#tick():int
com.hydrokarbs.engine.event.MatchModeInstanceEvent$Custom#toString():java.lang.String
com.hydrokarbs.engine.event.MatchModeInstanceEvent$Custom#type():java.lang.String
com.hydrokarbs.engine.event.MatchModeInstanceEvent$MatchModeInstanceEnded#equals(java.lang.Object):boolean
com.hydrokarbs.engine.event.MatchModeInstanceEvent$MatchModeInstanceEnded#hashCode():int
com.hydrokarbs.engine.event.MatchModeInstanceEvent$MatchModeInstanceEnded#occurredAtMillis():long
com.hydrokarbs.engine.event.MatchModeInstanceEvent$MatchModeInstanceEnded#tick():int
com.hydrokarbs.engine.event.MatchModeInstanceEvent$MatchModeInstanceEnded#toString():java.lang.String
com.hydrokarbs.engine.event.MatchModeInstanceEvent$MatchModeInstanceStarted#equals(java.lang.Object):boolean
com.hydrokarbs.engine.event.MatchModeInstanceEvent$MatchModeInstanceStarted#hashCode():int
com.hydrokarbs.engine.event.MatchModeInstanceEvent$MatchModeInstanceStarted#occurredAtMillis():long
com.hydrokarbs.engine.event.MatchModeInstanceEvent$MatchModeInstanceStarted#tick():int
com.hydrokarbs.engine.event.MatchModeInstanceEvent$MatchModeInstanceStarted#toString():java.lang.String
com.hydrokarbs.engine.event.MatchModeInstanceEvent$ParticipantJoined#equals(java.lang.Object):boolean
com.hydrokarbs.engine.event.MatchModeInstanceEvent$ParticipantJoined#hashCode():int
com.hydrokarbs.engine.event.MatchModeInstanceEvent$ParticipantJoined#occurredAtMillis():long
com.hydrokarbs.engine.event.MatchModeInstanceEvent$ParticipantJoined#player():java.util.UUID
com.hydrokarbs.engine.event.MatchModeInstanceEvent$ParticipantJoined#tick():int
com.hydrokarbs.engine.event.MatchModeInstanceEvent$ParticipantJoined#toString():java.lang.String
com.hydrokarbs.engine.event.MatchModeInstanceEvent$ParticipantKilled#cause():org.bukkit.event.entity.EntityDamageEvent$DamageCause
com.hydrokarbs.engine.event.MatchModeInstanceEvent$ParticipantKilled#equals(java.lang.Object):boolean
com.hydrokarbs.engine.event.MatchModeInstanceEvent$ParticipantKilled#hashCode():int
com.hydrokarbs.engine.event.MatchModeInstanceEvent$ParticipantKilled#killer():java.util.UUID
com.hydrokarbs.engine.event.MatchModeInstanceEvent$ParticipantKilled#occurredAtMillis():long
com.hydrokarbs.engine.event.MatchModeInstanceEvent$ParticipantKilled#tick():int
com.hydrokarbs.engine.event.MatchModeInstanceEvent$ParticipantKilled#toString():java.lang.String
com.hydrokarbs.engine.event.MatchModeInstanceEvent$ParticipantKilled#victim():java.util.UUID
com.hydrokarbs.engine.event.MatchModeInstanceEvent$ParticipantLeft#equals(java.lang.Object):boolean
com.hydrokarbs.engine.event.MatchModeInstanceEvent$ParticipantLeft#hashCode():int
com.hydrokarbs.engine.event.MatchModeInstanceEvent$ParticipantLeft#occurredAtMillis():long
com.hydrokarbs.engine.event.MatchModeInstanceEvent$ParticipantLeft#player():java.util.UUID
com.hydrokarbs.engine.event.MatchModeInstanceEvent$ParticipantLeft#tick():int
com.hydrokarbs.engine.event.MatchModeInstanceEvent$ParticipantLeft#toString():java.lang.String
com.hydrokarbs.engine.event.MatchModeInstanceEvent$ParticipantSpawned#equals(java.lang.Object):boolean
com.hydrokarbs.engine.event.MatchModeInstanceEvent$ParticipantSpawned#hashCode():int
com.hydrokarbs.engine.event.MatchModeInstanceEvent$ParticipantSpawned#occurredAtMillis():long
com.hydrokarbs.engine.event.MatchModeInstanceEvent$ParticipantSpawned#player():java.util.UUID
com.hydrokarbs.engine.event.MatchModeInstanceEvent$ParticipantSpawned#tick():int
com.hydrokarbs.engine.event.MatchModeInstanceEvent$ParticipantSpawned#toString():java.lang.String
com.hydrokarbs.engine.event.MatchModeInstanceEventBus#DEFAULT_PRIORITY:int
com.hydrokarbs.engine.event.MatchModeInstanceEventBus#clear():void
com.hydrokarbs.engine.event.MatchModeInstanceEventBus#publish(com.hydrokarbs.engine.event.MatchModeInstanceEvent):void
com.hydrokarbs.engine.event.MatchModeInstanceEventBus#subscribe(java.lang.Class,int,java.util.function.Consumer):void
com.hydrokarbs.engine.event.MatchModeInstanceEventBus#subscribe(java.lang.Class,java.util.function.Consumer):void
com.hydrokarbs.engine.event.MatchModeInstanceEventPort#publish(com.hydrokarbs.engine.event.MatchModeInstanceEvent):void
com.hydrokarbs.engine.event.MatchModeInstanceEventPort#subscribe(java.lang.Class,int,java.util.function.Consumer):void
com.hydrokarbs.engine.event.MatchModeInstanceEventPort#subscribe(java.lang.Class,java.util.function.Consumer):void
com.hydrokarbs.engine.event.SessionEvent#occurredAtMillis():long
com.hydrokarbs.engine.event.SessionEvent$MemberJoined#equals(java.lang.Object):boolean
com.hydrokarbs.engine.event.SessionEvent$MemberJoined#hashCode():int
com.hydrokarbs.engine.event.SessionEvent$MemberJoined#member():java.util.UUID
com.hydrokarbs.engine.event.SessionEvent$MemberJoined#occurredAtMillis():long
com.hydrokarbs.engine.event.SessionEvent$MemberJoined#of(com.hydrokarbs.engine.session.SessionId,java.util.UUID):com.hydrokarbs.engine.event.SessionEvent$MemberJoined
com.hydrokarbs.engine.event.SessionEvent$MemberJoined#session():com.hydrokarbs.engine.session.SessionId
com.hydrokarbs.engine.event.SessionEvent$MemberJoined#toString():java.lang.String
com.hydrokarbs.engine.event.SessionEvent$MemberLeft#equals(java.lang.Object):boolean
com.hydrokarbs.engine.event.SessionEvent$MemberLeft#hashCode():int
com.hydrokarbs.engine.event.SessionEvent$MemberLeft#member():java.util.UUID
com.hydrokarbs.engine.event.SessionEvent$MemberLeft#occurredAtMillis():long
com.hydrokarbs.engine.event.SessionEvent$MemberLeft#of(com.hydrokarbs.engine.session.SessionId,java.util.UUID,com.hydrokarbs.engine.session.LeaveReason):com.hydrokarbs.engine.event.SessionEvent$MemberLeft
com.hydrokarbs.engine.event.SessionEvent$MemberLeft#reason():com.hydrokarbs.engine.session.LeaveReason
com.hydrokarbs.engine.event.SessionEvent$MemberLeft#session():com.hydrokarbs.engine.session.SessionId
com.hydrokarbs.engine.event.SessionEvent$MemberLeft#toString():java.lang.String
com.hydrokarbs.engine.event.SessionEvent$MemberReconnected#equals(java.lang.Object):boolean
com.hydrokarbs.engine.event.SessionEvent$MemberReconnected#hashCode():int
com.hydrokarbs.engine.event.SessionEvent$MemberReconnected#member():java.util.UUID
com.hydrokarbs.engine.event.SessionEvent$MemberReconnected#occurredAtMillis():long
com.hydrokarbs.engine.event.SessionEvent$MemberReconnected#of(com.hydrokarbs.engine.session.SessionId,java.util.UUID):com.hydrokarbs.engine.event.SessionEvent$MemberReconnected
com.hydrokarbs.engine.event.SessionEvent$MemberReconnected#session():com.hydrokarbs.engine.session.SessionId
com.hydrokarbs.engine.event.SessionEvent$MemberReconnected#toString():java.lang.String
com.hydrokarbs.engine.event.SessionEvent$ModeSwitchCompleted#equals(java.lang.Object):boolean
com.hydrokarbs.engine.event.SessionEvent$ModeSwitchCompleted#fromMode():java.lang.String
com.hydrokarbs.engine.event.SessionEvent$ModeSwitchCompleted#hashCode():int
com.hydrokarbs.engine.event.SessionEvent$ModeSwitchCompleted#occurredAtMillis():long
com.hydrokarbs.engine.event.SessionEvent$ModeSwitchCompleted#of(com.hydrokarbs.engine.session.SessionId,java.lang.String,java.lang.String):com.hydrokarbs.engine.event.SessionEvent$ModeSwitchCompleted
com.hydrokarbs.engine.event.SessionEvent$ModeSwitchCompleted#session():com.hydrokarbs.engine.session.SessionId
com.hydrokarbs.engine.event.SessionEvent$ModeSwitchCompleted#toMode():java.lang.String
com.hydrokarbs.engine.event.SessionEvent$ModeSwitchCompleted#toString():java.lang.String
com.hydrokarbs.engine.event.SessionEvent$ModeSwitchStarted#equals(java.lang.Object):boolean
com.hydrokarbs.engine.event.SessionEvent$ModeSwitchStarted#fromMode():java.lang.String
com.hydrokarbs.engine.event.SessionEvent$ModeSwitchStarted#hashCode():int
com.hydrokarbs.engine.event.SessionEvent$ModeSwitchStarted#occurredAtMillis():long
com.hydrokarbs.engine.event.SessionEvent$ModeSwitchStarted#of(com.hydrokarbs.engine.session.SessionId,java.lang.String,java.lang.String):com.hydrokarbs.engine.event.SessionEvent$ModeSwitchStarted
com.hydrokarbs.engine.event.SessionEvent$ModeSwitchStarted#session():com.hydrokarbs.engine.session.SessionId
com.hydrokarbs.engine.event.SessionEvent$ModeSwitchStarted#toMode():java.lang.String
com.hydrokarbs.engine.event.SessionEvent$ModeSwitchStarted#toString():java.lang.String
com.hydrokarbs.engine.event.SessionEvent$SessionComponentQuarantined#componentId():java.lang.String
com.hydrokarbs.engine.event.SessionEvent$SessionComponentQuarantined#equals(java.lang.Object):boolean
com.hydrokarbs.engine.event.SessionEvent$SessionComponentQuarantined#hashCode():int
com.hydrokarbs.engine.event.SessionEvent$SessionComponentQuarantined#occurredAtMillis():long
com.hydrokarbs.engine.event.SessionEvent$SessionComponentQuarantined#of(com.hydrokarbs.engine.session.SessionId,java.lang.String,java.lang.String):com.hydrokarbs.engine.event.SessionEvent$SessionComponentQuarantined
com.hydrokarbs.engine.event.SessionEvent$SessionComponentQuarantined#reason():java.lang.String
com.hydrokarbs.engine.event.SessionEvent$SessionComponentQuarantined#session():com.hydrokarbs.engine.session.SessionId
com.hydrokarbs.engine.event.SessionEvent$SessionComponentQuarantined#toString():java.lang.String
com.hydrokarbs.engine.event.SessionEvent$SessionCreated#equals(java.lang.Object):boolean
com.hydrokarbs.engine.event.SessionEvent$SessionCreated#hashCode():int
com.hydrokarbs.engine.event.SessionEvent$SessionCreated#initialModeId():java.lang.String
com.hydrokarbs.engine.event.SessionEvent$SessionCreated#occurredAtMillis():long
com.hydrokarbs.engine.event.SessionEvent$SessionCreated#of(com.hydrokarbs.engine.session.SessionId,java.lang.String):com.hydrokarbs.engine.event.SessionEvent$SessionCreated
com.hydrokarbs.engine.event.SessionEvent$SessionCreated#session():com.hydrokarbs.engine.session.SessionId
com.hydrokarbs.engine.event.SessionEvent$SessionCreated#toString():java.lang.String
com.hydrokarbs.engine.event.SessionEvent$SessionDissolved#equals(java.lang.Object):boolean
com.hydrokarbs.engine.event.SessionEvent$SessionDissolved#hashCode():int
com.hydrokarbs.engine.event.SessionEvent$SessionDissolved#occurredAtMillis():long
com.hydrokarbs.engine.event.SessionEvent$SessionDissolved#of(com.hydrokarbs.engine.session.SessionId,com.hydrokarbs.engine.session.DissolveReason):com.hydrokarbs.engine.event.SessionEvent$SessionDissolved
com.hydrokarbs.engine.event.SessionEvent$SessionDissolved#reason():com.hydrokarbs.engine.session.DissolveReason
com.hydrokarbs.engine.event.SessionEvent$SessionDissolved#session():com.hydrokarbs.engine.session.SessionId
com.hydrokarbs.engine.event.SessionEvent$SessionDissolved#toString():java.lang.String
com.hydrokarbs.engine.event.SessionEventBus#DEFAULT_PRIORITY:int
com.hydrokarbs.engine.event.SessionEventBus#clear():void
com.hydrokarbs.engine.event.SessionEventBus#publish(com.hydrokarbs.engine.event.SessionEvent):void
com.hydrokarbs.engine.event.SessionEventBus#setMirror(java.util.function.Consumer):void
com.hydrokarbs.engine.event.SessionEventBus#subscribe(java.lang.Class,int,java.util.function.Consumer):com.hydrokarbs.engine.event.SessionEventBus$Subscription
com.hydrokarbs.engine.event.SessionEventBus#subscribe(java.lang.Class,java.util.function.Consumer):com.hydrokarbs.engine.event.SessionEventBus$Subscription
com.hydrokarbs.engine.instance.MatchModeInstanceView#cfg():com.hydrokarbs.engine.session.MatchModeConfig
com.hydrokarbs.engine.instance.MatchModeInstanceView#component(java.lang.Class):com.hydrokarbs.engine.component.MatchModeComponent
com.hydrokarbs.engine.instance.MatchModeInstanceView#map():com.hydrokarbs.map.MapDefinition
com.hydrokarbs.engine.instance.MatchModeInstanceView#session():com.hydrokarbs.engine.port.SessionView
com.hydrokarbs.engine.instance.MatchModeInstanceView#tick():long
com.hydrokarbs.engine.instance.Participant#state():com.hydrokarbs.engine.instance.Participant$ParticipantState
com.hydrokarbs.engine.instance.Participant#uuid():java.util.UUID
com.hydrokarbs.engine.mode.MatchMode#components():java.util.Map
com.hydrokarbs.engine.mode.MatchMode#createInstance(com.hydrokarbs.engine.session.Session,com.hydrokarbs.map.MapDefinition,com.hydrokarbs.engine.session.MatchModeConfig,com.hydrokarbs.engine.port.WorldHandle,com.hydrokarbs.engine.session.Handoff,com.hydrokarbs.engine.port.EngineServices):com.hydrokarbs.engine.instance.MatchModeInstance
com.hydrokarbs.engine.mode.MatchMode#description():java.util.List
com.hydrokarbs.engine.mode.MatchMode#displayName():net.kyori.adventure.text.Component
com.hydrokarbs.engine.mode.MatchMode#icon():org.bukkit.Material
com.hydrokarbs.engine.mode.MatchMode#id():java.lang.String
com.hydrokarbs.engine.mode.MatchMode#mapTags():java.util.Set
com.hydrokarbs.engine.mode.MatchMode#meta():com.hydrokarbs.engine.AttributeBag
com.hydrokarbs.engine.mode.MatchMode#verifyDependencies():void
com.hydrokarbs.engine.mode.MatchMode$Builder#addComponent(java.lang.String,com.hydrokarbs.engine.component.MatchModeComponent$Specification):com.hydrokarbs.engine.mode.MatchMode$Builder
com.hydrokarbs.engine.mode.MatchMode$Builder#build():com.hydrokarbs.engine.mode.MatchMode
com.hydrokarbs.engine.mode.MatchMode$Builder#description(java.util.List):com.hydrokarbs.engine.mode.MatchMode$Builder
com.hydrokarbs.engine.mode.MatchMode$Builder#displayName(net.kyori.adventure.text.Component):com.hydrokarbs.engine.mode.MatchMode$Builder
com.hydrokarbs.engine.mode.MatchMode$Builder#icon(org.bukkit.Material):com.hydrokarbs.engine.mode.MatchMode$Builder
com.hydrokarbs.engine.mode.MatchMode$Builder#mapTags(java.util.Set):com.hydrokarbs.engine.mode.MatchMode$Builder
com.hydrokarbs.engine.mode.MatchMode$Builder#metadata(com.hydrokarbs.engine.AttributeBag):com.hydrokarbs.engine.mode.MatchMode$Builder
com.hydrokarbs.engine.mode.MatchMode$ComponentEntry#descriptorType():java.lang.Class
com.hydrokarbs.engine.mode.MatchMode$ComponentEntry#equals(java.lang.Object):boolean
com.hydrokarbs.engine.mode.MatchMode$ComponentEntry#factory():com.hydrokarbs.engine.component.MatchModeComponent$Factory
com.hydrokarbs.engine.mode.MatchMode$ComponentEntry#hashCode():int
com.hydrokarbs.engine.mode.MatchMode$ComponentEntry#mapCheck():java.util.function.BiFunction
com.hydrokarbs.engine.mode.MatchMode$ComponentEntry#optionalTypes():java.util.List
com.hydrokarbs.engine.mode.MatchMode$ComponentEntry#providedType():java.lang.Class
com.hydrokarbs.engine.mode.MatchMode$ComponentEntry#requiredTypes():java.util.List
com.hydrokarbs.engine.mode.MatchMode$ComponentEntry#toString():java.lang.String
com.hydrokarbs.engine.mode.MatchModeMetadata#DEFAULT_MAX:int
com.hydrokarbs.engine.mode.MatchModeMetadata#DEFAULT_MIN:int
com.hydrokarbs.engine.mode.MatchModeMetadata#MAX_PARTICIPANTS:com.hydrokarbs.engine.AttributeBag$Key
com.hydrokarbs.engine.mode.MatchModeMetadata#MIN_PARTICIPANTS:com.hydrokarbs.engine.AttributeBag$Key
com.hydrokarbs.engine.mode.MatchModeMetadata#RECORDS_HISTORY:com.hydrokarbs.engine.AttributeBag$Key
com.hydrokarbs.engine.mode.MatchModeMetadata#addTo(com.hydrokarbs.engine.AttributeBag$Builder,boolean,int,int):com.hydrokarbs.engine.AttributeBag$Builder
com.hydrokarbs.engine.mode.MatchModeMetadata#maxParticipants(com.hydrokarbs.engine.AttributeBag):int
com.hydrokarbs.engine.mode.MatchModeMetadata#minParticipants(com.hydrokarbs.engine.AttributeBag):int
com.hydrokarbs.engine.mode.MatchModeMetadata#recordsHistory(com.hydrokarbs.engine.AttributeBag):boolean
com.hydrokarbs.engine.mode.MatchModeProvider#build(com.hydrokarbs.engine.session.MatchModeConfig):com.hydrokarbs.engine.mode.MatchMode
com.hydrokarbs.engine.mode.MatchModeProvider#id():java.lang.String
com.hydrokarbs.engine.port.JoinAdmission#admit(java.util.UUID,com.hydrokarbs.engine.port.JoinContext):com.hydrokarbs.engine.port.JoinDecision
com.hydrokarbs.engine.port.JoinContext#equals(java.lang.Object):boolean
com.hydrokarbs.engine.port.JoinContext#hashCode():int
com.hydrokarbs.engine.port.JoinContext#session():com.hydrokarbs.engine.session.SessionId
com.hydrokarbs.engine.port.JoinContext#toString():java.lang.String
com.hydrokarbs.engine.port.JoinContext#trigger():com.hydrokarbs.engine.port.JoinContext$Trigger
com.hydrokarbs.engine.port.JoinDecision#equals(java.lang.Object):boolean
com.hydrokarbs.engine.port.JoinDecision#hashCode():int
com.hydrokarbs.engine.port.JoinDecision#kind():com.hydrokarbs.engine.port.JoinDecision$Kind
com.hydrokarbs.engine.port.JoinDecision#reason():java.lang.String
com.hydrokarbs.engine.port.JoinDecision#toString():java.lang.String
com.hydrokarbs.engine.port.JoinDecision$Kind#ADMIT:com.hydrokarbs.engine.port.JoinDecision$Kind
com.hydrokarbs.engine.port.JoinDecision$Kind#REJECT:com.hydrokarbs.engine.port.JoinDecision$Kind
com.hydrokarbs.engine.port.JoinDecision$Kind#valueOf(java.lang.String):com.hydrokarbs.engine.port.JoinDecision$Kind
com.hydrokarbs.engine.port.JoinDecision$Kind#values():[Lcom.hydrokarbs.engine.port.JoinDecision$Kind;
com.hydrokarbs.engine.port.MatchModeInstanceAccess#component(java.lang.Class):com.hydrokarbs.engine.component.MatchModeComponent
com.hydrokarbs.engine.port.MatchModeInstanceAccess#components(java.lang.Class):java.util.List
com.hydrokarbs.engine.port.MatchModeInstanceAccess#id():com.hydrokarbs.engine.instance.MatchModeInstanceId
com.hydrokarbs.engine.port.MatchModeInstanceAccess#modeId():java.lang.String
com.hydrokarbs.engine.port.MatchModeInstanceAccess#modeInfo():com.hydrokarbs.engine.port.MatchModeInfoPort
com.hydrokarbs.engine.port.MatchModeInstanceAccess#participants():java.util.List
com.hydrokarbs.engine.port.MatchModeInstanceAccess#tick():long
com.hydrokarbs.engine.port.MatchModeInstancePort#cfg():com.hydrokarbs.engine.session.MatchModeConfig
com.hydrokarbs.engine.port.MatchModeInstancePort#end(com.hydrokarbs.engine.session.EndResult):void
com.hydrokarbs.engine.port.MatchModeInstancePort#events():com.hydrokarbs.engine.event.MatchModeInstanceEventPort
com.hydrokarbs.engine.port.MatchModeInstancePort#handoff():com.hydrokarbs.engine.session.HandoffView
com.hydrokarbs.engine.port.MatchModeInstancePort#later(int,java.lang.Runnable):void
com.hydrokarbs.engine.port.MatchModeInstancePort#map():com.hydrokarbs.map.MapDefinition
com.hydrokarbs.engine.port.MatchModeInstancePort#participant(java.util.UUID):java.util.Optional
com.hydrokarbs.engine.port.MatchModeInstancePort#participants():java.util.List
com.hydrokarbs.engine.port.MatchModeInstancePort#session():com.hydrokarbs.engine.port.SessionView
com.hydrokarbs.engine.port.MatchModeInstancePort#switchTo(com.hydrokarbs.engine.session.MatchModeSelection,com.hydrokarbs.engine.session.Handoff):void
com.hydrokarbs.engine.port.MatchModeInstancePort#tick():long
com.hydrokarbs.engine.port.MatchModeInstanceServices#components():com.hydrokarbs.engine.port.ComponentLookupPort
com.hydrokarbs.engine.port.MatchModeInstanceServices#equals(java.lang.Object):boolean
com.hydrokarbs.engine.port.MatchModeInstanceServices#hashCode():int
com.hydrokarbs.engine.port.MatchModeInstanceServices#modeInfo():com.hydrokarbs.engine.port.MatchModeInfoPort
com.hydrokarbs.engine.port.MatchModeInstanceServices#self():com.hydrokarbs.engine.port.MatchModeInstancePort
com.hydrokarbs.engine.port.MatchModeInstanceServices#toString():java.lang.String
com.hydrokarbs.engine.port.SessionEventPort#publish(com.hydrokarbs.engine.event.SessionEvent):void
com.hydrokarbs.engine.port.SessionEventPort#subscribe(java.lang.Class,int,java.util.function.Consumer):java.lang.AutoCloseable
com.hydrokarbs.engine.port.SessionEventPort#subscribe(java.lang.Class,java.util.function.Consumer):java.lang.AutoCloseable
com.hydrokarbs.engine.port.SessionPort#currentInstance():java.util.Optional
com.hydrokarbs.engine.port.SessionPort#events():com.hydrokarbs.engine.port.SessionEventPort
com.hydrokarbs.engine.port.SessionPort#id():com.hydrokarbs.engine.session.SessionId
com.hydrokarbs.engine.port.SessionPort#later(int,java.lang.Runnable):void
com.hydrokarbs.engine.port.SessionPort#members():java.util.List
com.hydrokarbs.engine.port.SessionPort#requestDissolve(com.hydrokarbs.engine.session.DissolveReason):void
com.hydrokarbs.engine.port.SessionPort#requestSwitch(com.hydrokarbs.engine.session.MatchModeSelection,com.hydrokarbs.engine.session.Handoff):void
com.hydrokarbs.engine.port.SessionPort#tick():long
com.hydrokarbs.engine.port.SessionServices#components():com.hydrokarbs.engine.port.SessionComponentLookupPort
com.hydrokarbs.engine.port.SessionServices#equals(java.lang.Object):boolean
com.hydrokarbs.engine.port.SessionServices#hashCode():int
com.hydrokarbs.engine.port.SessionServices#self():com.hydrokarbs.engine.port.SessionPort
com.hydrokarbs.engine.port.SessionServices#toString():java.lang.String
com.hydrokarbs.engine.port.SessionView#component(java.lang.Class):com.hydrokarbs.engine.component.SessionComponent
com.hydrokarbs.engine.port.SessionView#components(java.lang.Class):java.util.List
com.hydrokarbs.engine.port.SessionView#id():com.hydrokarbs.engine.session.SessionId
com.hydrokarbs.engine.port.SessionView#members():java.util.List
com.hydrokarbs.engine.session.DissolveReason#ADMIN:com.hydrokarbs.engine.session.DissolveReason
com.hydrokarbs.engine.session.DissolveReason#EMPTY:com.hydrokarbs.engine.session.DissolveReason
com.hydrokarbs.engine.session.DissolveReason#FALLBACK_DISSOLVE:com.hydrokarbs.engine.session.DissolveReason
com.hydrokarbs.engine.session.DissolveReason#SHUTDOWN:com.hydrokarbs.engine.session.DissolveReason
com.hydrokarbs.engine.session.DissolveReason#valueOf(java.lang.String):com.hydrokarbs.engine.session.DissolveReason
com.hydrokarbs.engine.session.DissolveReason#values():[Lcom.hydrokarbs.engine.session.DissolveReason;
com.hydrokarbs.engine.session.EndResult#equals(java.lang.Object):boolean
com.hydrokarbs.engine.session.EndResult#hashCode():int
com.hydrokarbs.engine.session.EndResult#nowhere():com.hydrokarbs.engine.session.EndResult
com.hydrokarbs.engine.session.EndResult#toString():java.lang.String
com.hydrokarbs.engine.session.EndResult#transition():java.util.Optional
com.hydrokarbs.engine.session.Handoff#empty():com.hydrokarbs.engine.session.Handoff
com.hydrokarbs.engine.session.Handoff#get(com.hydrokarbs.engine.session.Handoff$Key):java.util.Optional
com.hydrokarbs.engine.session.Handoff#has(com.hydrokarbs.engine.session.Handoff$Key):boolean
com.hydrokarbs.engine.session.Handoff#merge(com.hydrokarbs.engine.session.Handoff,com.hydrokarbs.engine.session.Handoff):com.hydrokarbs.engine.session.Handoff
com.hydrokarbs.engine.session.Handoff#of(com.hydrokarbs.engine.session.Handoff$Key,java.lang.Object):com.hydrokarbs.engine.session.Handoff
com.hydrokarbs.engine.session.Handoff#put(com.hydrokarbs.engine.session.Handoff$Key,java.lang.Object):void
com.hydrokarbs.engine.session.Handoff$Builder#build():com.hydrokarbs.engine.session.Handoff
com.hydrokarbs.engine.session.Handoff$Builder#put(com.hydrokarbs.engine.session.Handoff$Key,java.lang.Object):com.hydrokarbs.engine.session.Handoff$Builder
com.hydrokarbs.engine.session.Handoff$Key#equals(java.lang.Object):boolean
com.hydrokarbs.engine.session.Handoff$Key#hashCode():int
com.hydrokarbs.engine.session.Handoff$Key#name():java.lang.String
com.hydrokarbs.engine.session.Handoff$Key#namespace():java.lang.String
com.hydrokarbs.engine.session.Handoff$Key#of(java.lang.String,java.lang.String,java.lang.Class):com.hydrokarbs.engine.session.Handoff$Key
com.hydrokarbs.engine.session.Handoff$Key#toString():java.lang.String
com.hydrokarbs.engine.session.Handoff$Key#type():java.lang.Class
com.hydrokarbs.engine.session.HandoffView#get(com.hydrokarbs.engine.session.Handoff$Key):java.util.Optional
com.hydrokarbs.engine.session.HandoffView#has(com.hydrokarbs.engine.session.Handoff$Key):boolean
com.hydrokarbs.engine.session.JoinResult$Joined#equals(java.lang.Object):boolean
com.hydrokarbs.engine.session.JoinResult$Joined#hashCode():int
com.hydrokarbs.engine.session.JoinResult$Joined#session():com.hydrokarbs.engine.session.SessionId
com.hydrokarbs.engine.session.JoinResult$Joined#toString():java.lang.String
com.hydrokarbs.engine.session.JoinResult$Rejected#detail():java.lang.String
com.hydrokarbs.engine.session.JoinResult$Rejected#equals(java.lang.Object):boolean
com.hydrokarbs.engine.session.JoinResult$Rejected#hashCode():int
com.hydrokarbs.engine.session.JoinResult$Rejected#reason():java.lang.String
com.hydrokarbs.engine.session.JoinResult$Rejected#toString():java.lang.String
com.hydrokarbs.engine.session.LeaveReason#KICK:com.hydrokarbs.engine.session.LeaveReason
com.hydrokarbs.engine.session.LeaveReason#QUIT:com.hydrokarbs.engine.session.LeaveReason
com.hydrokarbs.engine.session.LeaveReason#valueOf(java.lang.String):com.hydrokarbs.engine.session.LeaveReason
com.hydrokarbs.engine.session.LeaveReason#values():[Lcom.hydrokarbs.engine.session.LeaveReason;
com.hydrokarbs.engine.session.MatchModeConfig#getInt(java.lang.String,int):int
com.hydrokarbs.engine.session.MatchModeConfig#getString(java.lang.String,java.lang.String):java.lang.String
com.hydrokarbs.engine.session.MatchModeConfig#getStringList(java.lang.String,java.util.List):java.util.List
com.hydrokarbs.engine.session.MatchModeConfig#keys(java.lang.String):java.util.List
com.hydrokarbs.engine.session.MatchModeSelection#equals(java.lang.Object):boolean
com.hydrokarbs.engine.session.MatchModeSelection#force():boolean
com.hydrokarbs.engine.session.MatchModeSelection#hashCode():int
com.hydrokarbs.engine.session.MatchModeSelection#mapId():java.util.Optional
com.hydrokarbs.engine.session.MatchModeSelection#modeId():java.lang.String
com.hydrokarbs.engine.session.MatchModeSelection#of(java.lang.String):com.hydrokarbs.engine.session.MatchModeSelection
com.hydrokarbs.engine.session.MatchModeSelection#toString():java.lang.String
com.hydrokarbs.engine.session.SessionId#equals(java.lang.Object):boolean
com.hydrokarbs.engine.session.SessionId#hashCode():int
com.hydrokarbs.engine.session.SessionId#shortCode():java.lang.String
com.hydrokarbs.engine.session.SessionId#toString():java.lang.String
com.hydrokarbs.engine.session.SessionId#value():java.lang.String
com.hydrokarbs.engine.session.SwitchResult$Reason#MODE_NOT_FOUND:com.hydrokarbs.engine.session.SwitchResult$Reason
com.hydrokarbs.engine.session.SwitchResult$Reason#REJECTED_BY_TARGET_MODE:com.hydrokarbs.engine.session.SwitchResult$Reason
com.hydrokarbs.engine.session.SwitchResult$Reason#SWITCH_IN_PROGRESS:com.hydrokarbs.engine.session.SwitchResult$Reason
com.hydrokarbs.engine.session.SwitchResult$Reason#valueOf(java.lang.String):com.hydrokarbs.engine.session.SwitchResult$Reason
com.hydrokarbs.engine.session.SwitchResult$Reason#values():[Lcom.hydrokarbs.engine.session.SwitchResult$Reason;
com.hydrokarbs.engine.session.SwitchResult$Rejected#code():com.hydrokarbs.engine.session.SwitchResult$Reason
com.hydrokarbs.engine.session.SwitchResult$Rejected#detail():java.lang.String
com.hydrokarbs.engine.session.SwitchResult$Rejected#equals(java.lang.Object):boolean
com.hydrokarbs.engine.session.SwitchResult$Rejected#hashCode():int
com.hydrokarbs.engine.session.SwitchResult$Rejected#toString():java.lang.String
com.hydrokarbs.engine.session.SwitchResult$Succeeded#cause():com.hydrokarbs.engine.session.EndResult
com.hydrokarbs.engine.session.SwitchResult$Succeeded#equals(java.lang.Object):boolean
com.hydrokarbs.engine.session.SwitchResult$Succeeded#fromMode():java.lang.String
com.hydrokarbs.engine.session.SwitchResult$Succeeded#hashCode():int
com.hydrokarbs.engine.session.SwitchResult$Succeeded#session():com.hydrokarbs.engine.session.SessionId
com.hydrokarbs.engine.session.SwitchResult$Succeeded#toMode():java.lang.String
com.hydrokarbs.engine.session.SwitchResult$Succeeded#toString():java.lang.String
com.hydrokarbs.engine.session.TransitionIntent#equals(java.lang.Object):boolean
com.hydrokarbs.engine.session.TransitionIntent#hashCode():int
com.hydrokarbs.engine.session.TransitionIntent#payload():com.hydrokarbs.engine.session.Handoff
com.hydrokarbs.engine.session.TransitionIntent#target():com.hydrokarbs.engine.session.MatchModeSelection
com.hydrokarbs.engine.session.TransitionIntent#toString():java.lang.String
com.hydrokarbs.map.MapDefinition#TAGS:com.hydrokarbs.engine.AttributeBag$Key
com.hydrokarbs.map.MapDefinition#equals(java.lang.Object):boolean
com.hydrokarbs.map.MapDefinition#hashCode():int
com.hydrokarbs.map.MapDefinition#id():java.lang.String
com.hydrokarbs.map.MapDefinition#metadata():com.hydrokarbs.engine.AttributeBag
com.hydrokarbs.map.MapDefinition#resetStrategy():com.hydrokarbs.engine.port.ResetStrategy
com.hydrokarbs.map.MapDefinition#tags():java.util.Set
com.hydrokarbs.map.MapDefinition#toString():java.lang.String
com.hydrokarbs.map.MapDefinition#worldDirectoryPath():java.nio.file.Path""";

    /** 首建基线(7.5,2026-10-25;由 {@code snapshotLines()} 同算法生成后落盘常量)。 */
    private static final Set<String> BASELINE = Set.of(BASELINE_TEXT.split("\n"));

    @Test
    void whitelistPublicSignaturesOnlyGrow() {
        TreeSet<String> actual = new TreeSet<>(snapshotLines());
        TreeSet<String> expected = new TreeSet<>(BASELINE);
        assertEquals(expected, actual,
                "白名单内类型公开签名与首建快照不符——「只增不改」(★44;07 §7.2)。"
                        + "新增成员 = 追加 BASELINE 行 + PR 说明;删/改 = 破坏冻结面,驳回归位");
    }

    /** 快照算法:白名单每类的 public declared 方法与字段(桥接/合成排除)。 */
    private static List<String> snapshotLines() {
        List<String> lines = new ArrayList<>();
        for (Class<?> type : WHITELIST) {
            String head = type.getName() + "#";
            for (Method method : type.getDeclaredMethods()) {
                if (!Modifier.isPublic(method.getModifiers()) || method.isSynthetic() || method.isBridge()) {
                    continue;
                }
                lines.add(head + method.getName() + "(" + typeNames(method.getParameterTypes())
                        + "):" + method.getReturnType().getName());
            }
            for (Field field : type.getDeclaredFields()) {
                if (!Modifier.isPublic(field.getModifiers()) || field.isSynthetic()) {
                    continue;
                }
                lines.add(head + field.getName() + ":" + field.getType().getName());
            }
        }
        return lines;
    }

    private static String typeNames(Class<?>[] types) {
        StringBuilder joined = new StringBuilder();
        for (Class<?> type : types) {
            if (joined.length() > 0) {
                joined.append(',');
            }
            joined.append(type.getName());
        }
        return joined.toString();
    }

}
