package com.hydrokarbs.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.hydrokarbs.engine.component.MatchModeComponent;
import com.hydrokarbs.engine.event.SessionEvent;
import com.hydrokarbs.engine.instance.MatchModeInstance;
import com.hydrokarbs.engine.mode.MatchMode;
import com.hydrokarbs.engine.mode.MatchModeRegistry;
import com.hydrokarbs.engine.port.ModeLookup;
import com.hydrokarbs.engine.session.DissolveReason;
import com.hydrokarbs.engine.session.Handoff;
import com.hydrokarbs.engine.session.LeaveReason;
import com.hydrokarbs.engine.session.MatchModeSelection;
import com.hydrokarbs.engine.session.Session;
import com.hydrokarbs.engine.session.SessionId;
import com.hydrokarbs.engine.session.SessionSpec;
import com.hydrokarbs.engine.session.SessionState;
import com.hydrokarbs.engine.session.SwitchResult;
import com.hydrokarbs.mode.empty.EmptyMatchModeFactory;
import com.hydrokarbs.testkit.FakeWorldPort;
import com.hydrokarbs.testkit.ProbeComponent;
import com.hydrokarbs.testkit.TestComponent;

/**
 * ★14「切换原子性」首建(§16.2):①阶段 A 任一步失败 → 旧实例继续运行、future 拒绝;
 * ②造件/唤醒/生效三抛点 → 都落到回退模式且成员仍有实例;③生效抛先全停再回退;
 * ④任何路径结束后当前实例非空或会话已解散,绝无中间态残留。附 ★6/★7 在 B2 重放。
 * ★v1.9(27 号):新增清场断言族(★65)——B3.5/B5 与解散接缝的世界清场时序;
 * 离线以 FakeWorldPort.sticky + seamLog 时序录为前提,平台正确性归 runServer 实测。
 */
@Timeout(30)
class ModeSwitchAtomicityTest {

    private static MatchModeSelection sel(String modeId) {
        return MatchModeSelection.of(modeId);
    }

    /** 豁免标记版选区(外部强切口径):B 段路径测试用它绕过最短驻留,聚焦执行段行为。 */
    private static MatchModeSelection forceSel(String modeId) {
        return new MatchModeSelection(modeId, Optional.empty(), true);
    }

    /** 三组件模式(★7 逆序断言用)。 */
    private static MatchMode threeComponentMode(String id, List<String> log) {
        LinkedHashMap<String, MatchModeComponent.Specification<?>> specs = new LinkedHashMap<>();
        specs.put("c1", new ProbeComponent.Spec(log));
        specs.put("c2", new ProbeComponent.Spec(log));
        specs.put("c3", new ProbeComponent.Spec(log));
        return SwitchHarness.mode(id, 0, 100, specs);
    }

    /** 标准夹具:回退模式 fb + 基础模式 base,会话在 base 上、三人在册。 */
    private static final class Fixture {
        final SwitchHarness harness;
        final Session session;
        final MatchModeInstance original;
        final List<SessionEvent> events;
        final List<String> componentLog;

        static Fixture standard() {
            List<String> baseLog = new ArrayList<>();
            return new Fixture(new SwitchHarness("fb",
                    threeComponentMode("fb", new ArrayList<>()),
                    threeComponentMode("base", baseLog)), baseLog);
        }

        Fixture(SwitchHarness harness) {
            this(harness, new ArrayList<>());
        }

        Fixture(SwitchHarness harness, List<String> componentLog) {
            this.harness = harness;
            this.componentLog = componentLog;
            this.session = harness.createSession("s1", "base");
            for (int i = 0; i < 3; i++) {
                harness.service.join(session.id(), UUID.randomUUID());
            }
            this.original = session.currentInstance().orElseThrow();
            this.events = SwitchHarness.recordEvents(session);
        }

        SwitchResult.Rejected awaitRejected(CompletableFuture<SwitchResult> future) throws Exception {
            SwitchResult result = future.get(2, TimeUnit.SECONDS);
            return assertInstanceOf(SwitchResult.Rejected.class, result);
        }

        /** 旧实例仍在位且继续派发(实例时钟推进)。 */
        void oldInstanceStillTicking() {
            long before = original.view().tick();
            assertSame(original, session.currentInstance().orElse(null), "旧实例引用不变(零副作用)");
            harness.ticker.tickAll();
            harness.ticker.tickAll();
            assertTrue(original.view().tick() > before, "旧实例继续 tick(A 段失败零副作用)");
        }
    }

    // ── ① 阶段 A 六条失败路径 ─────────────────────────────────────

    @Test
    void a1UnknownModeIdIsRejectedAndOldInstanceUntouched() throws Exception {
        Fixture fx = Fixture.standard();
        SwitchResult.Rejected rejected = fx.awaitRejected(
                fx.harness.service.switchMode(fx.session.id(), sel("nope"), Handoff.empty()));
        assertEquals(SwitchResult.Reason.MODE_NOT_FOUND, rejected.code());
        assertTrue(rejected.detail().contains("nope"), "detail 点名未知 id: " + rejected.detail());
        fx.oldInstanceStillTicking();
        assertEquals(SessionState.RUNNING, fx.session.state());
    }

    @Test
    void a3MapCheckProblemsAreRejectedWithIssueList() throws Exception {
        List<String> log = new ArrayList<>();
        LinkedHashMap<String, MatchModeComponent.Specification<?>> specs = new LinkedHashMap<>();
        specs.put("picky", new ProbeComponent.Spec(log).mapCheckIssue("缺红方出生点"));
        SwitchHarness harness = new SwitchHarness("fb",
                SwitchHarness.mode("fb", 0, 100),
                SwitchHarness.mode("base", 0, 100),
                SwitchHarness.mode("pickymode", 0, 100, specs));
        Fixture fx = new Fixture(harness);
        SwitchResult.Rejected rejected = fx.awaitRejected(
                fx.harness.service.switchMode(fx.session.id(), sel("pickymode"), Handoff.empty()));
        assertEquals(SwitchResult.Reason.REJECTED_BY_TARGET_MODE, rejected.code());
        assertTrue(rejected.detail().contains("picky") && rejected.detail().contains("缺红方出生点"),
                "detail 带问题清单: " + rejected.detail());
        fx.oldInstanceStillTicking();
    }

    @Test
    void a4ParticipantWindowIsRejectedAndForceWaivesMinimumOnly() throws Exception {
        SwitchHarness harness = new SwitchHarness("fb",
                SwitchHarness.mode("fb", 0, 100),
                SwitchHarness.mode("base", 0, 100),
                SwitchHarness.mode("needfive", 5, 100),
                SwitchHarness.mode("maxtwo", 0, 2));
        Fixture fx = new Fixture(harness);        // 3 人在册

        SwitchResult.Rejected tooFew = fx.awaitRejected(
                fx.harness.service.switchMode(fx.session.id(), sel("needfive"), Handoff.empty()));
        assertEquals(SwitchResult.Reason.REJECTED_BY_TARGET_MODE, tooFew.code());
        assertTrue(tooFew.detail().contains("人数窗"), "detail 注明人数窗: " + tooFew.detail());

        SwitchResult.Rejected tooMany = fx.awaitRejected(
                fx.harness.service.switchMode(fx.session.id(),
                        new MatchModeSelection("maxtwo", Optional.empty(), true), Handoff.empty()));
        assertEquals(SwitchResult.Reason.REJECTED_BY_TARGET_MODE, tooMany.code(),
                "人数上限不因豁免标记放开");
        fx.oldInstanceStillTicking();
    }

    @Test
    void a5DependencyFailureIsRejectedWithExceptionMessage() throws Exception {
        List<String> log = new ArrayList<>();
        final MatchModeRegistry registryLookups = new MatchModeRegistry(id -> com.hydrokarbs.testkit.OfflineAssembly.cfg());
        registryLookups.register(SwitchHarness.fixedProvider(SwitchHarness.mode("fb", 0, 100)));
        registryLookups.register(SwitchHarness.fixedProvider(SwitchHarness.mode("base", 0, 100)));
        LinkedHashMap<String, MatchModeComponent.Specification<?>> broken = new LinkedHashMap<>();
        broken.put("needful", TestComponent.requiring(log, ProbeComponent.class));
        MatchMode badMode = SwitchHarness.mode("baddeps", 0, 100, broken);
        // 坏依赖模式进不了注册表(注册闸拦截)——包一层「其余转发真注册表」的查询口,覆盖 A5 的防御性重验路径
        ModeLookup leaking = new ModeLookup() {
            @Override
            public MatchMode require(String modeId) {
                return modeId.equals("baddeps") ? badMode : registryLookups.require(modeId);
            }

            @Override
            public Collection<MatchMode> all() {
                return registryLookups.all();
            }
        };
        SwitchHarness harness = new SwitchHarness("fb", leaking,
                SwitchHarness.mode("fb", 0, 100), SwitchHarness.mode("base", 0, 100));
        Fixture fx = new Fixture(harness);
        SwitchResult.Rejected rejected = fx.awaitRejected(
                fx.harness.service.switchMode(fx.session.id(), sel("baddeps"), Handoff.empty()));
        assertEquals(SwitchResult.Reason.REJECTED_BY_TARGET_MODE, rejected.code());
        assertTrue(rejected.detail().contains("依赖校验失败") && rejected.detail().contains("needful"),
                "detail 带异常消息: " + rejected.detail());
        fx.oldInstanceStillTicking();
    }

    @Test
    void a6WorldPreparationFailureIsRejectedAndOldInstanceUntouched() throws Exception {
        FakeWorldPort worlds = FakeWorldPort.immediate();
        SwitchHarness harness = new SwitchHarness(worlds,
                "fb", threeComponentMode("fb", new ArrayList<>()), threeComponentMode("base", new ArrayList<>()));
        Fixture fx = new Fixture(harness);                       // 首切实例就位(世界成功)
        worlds.failFromNowOn(new TimeoutException("world-prep-30s"));   // 目标切换的世界准备注入失败/超时
        SwitchResult.Rejected rejected = fx.awaitRejected(
                fx.harness.service.switchMode(fx.session.id(), forceSel("fb"), Handoff.empty()));
        assertEquals(SwitchResult.Reason.REJECTED_BY_TARGET_MODE, rejected.code());
        assertTrue(rejected.detail().contains("世界副本准备失败"),
                "detail 注明世界准备失败(超时注入共用本分支): " + rejected.detail());
        assertEquals(SessionState.RUNNING, fx.session.state());
        fx.oldInstanceStillTicking();
    }

    /** 防护③(准备期人数漂移):A 段过了、执行前人走了 → 中止、状态回常态、旧实例未动。 */
    @Test
    void guardParticipantDriftBetweenPhasesAbortsBeforeB1() throws Exception {
        SwitchHarness harness = new SwitchHarness("fb",
                SwitchHarness.mode("fb", 0, 100),
                SwitchHarness.mode("base", 0, 100),
                SwitchHarness.mode("duo", 2, 100));            // 下限 2
        com.hydrokarbs.engine.session.Session session = harness.createSession("drift", "base");
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();
        harness.service.join(session.id(), u1);
        harness.service.join(session.id(), u2);
        MatchModeInstance original = session.currentInstance().orElseThrow();
        for (int i = 0; i < 101; i++) {
            session.idleTimers();                               // 推进最短驻留(非豁免请求)
        }
        var future = harness.service.switchMode(session.id(), sel("duo"), Handoff.empty());
        assertEquals(SessionState.SWITCHING, session.state(), "A 段已过(人数 2 ∈ 窗口),挂起待执行");

        harness.service.leave(u2, LeaveReason.KICK);            // 执行前人走了:窗口失守
        harness.ticker.tickAll();                               // 执行拍:前置重校验 → 中止

        SwitchResult.Rejected rejected = assertInstanceOf(SwitchResult.Rejected.class,
                future.get(2, TimeUnit.SECONDS));
        assertEquals(SwitchResult.Reason.REJECTED_BY_TARGET_MODE, rejected.code());
        assertTrue(rejected.detail().contains("重校验"), "detail 注明重校验失败: " + rejected.detail());
        assertEquals(SessionState.RUNNING, session.state(), "中止:状态回常态");
        assertSame(original, session.currentInstance().orElse(null), "旧实例未动(B1 前中止)");
        assertTrue(original.view().tick() >= 0, "旧实例可继续驱动");
    }

    // ── ② B 段三抛点:都落回退模式且成员仍有实例 ──────────────────────

    @Test
    void bCreateThrowsFallsBackAndMembersKeepAnInstance() throws Exception {
        assertFailurePathFallsBack(spec -> spec.failOnCreate("create-boom"));
    }

    @Test
    void bAwakeThrowsFallsBackAndMembersKeepAnInstance() throws Exception {
        assertFailurePathFallsBack(spec -> spec.failOnAwake("awake-boom"));
    }

    @Test
    void bStartThrowsClosesAllThenFallsBack() throws Exception {
        List<String> boomLog = new ArrayList<>();
        LinkedHashMap<String, MatchModeComponent.Specification<?>> specs = new LinkedHashMap<>();
        specs.put("first", new ProbeComponent.Spec(boomLog));
        specs.put("second", new ProbeComponent.Spec(boomLog).failOnStart("start-boom"));
        SwitchHarness harness = new SwitchHarness("fb",
                threeComponentMode("fb", new ArrayList<>()),
                threeComponentMode("base", new ArrayList<>()),
                SwitchHarness.mode("boomstart", 0, 100, specs));
        Fixture fx = new Fixture(harness);

        CompletableFuture<SwitchResult> future = harness.service.switchMode(
                fx.session.id(), forceSel("boomstart"), Handoff.empty());
        harness.ticker.tickAll();
        harness.ticker.tickAll();
        harness.ticker.tickAll();

        SwitchResult.Rejected rejected = assertInstanceOf(SwitchResult.Rejected.class, future.get(2, TimeUnit.SECONDS));
        assertEquals(SwitchResult.Reason.REJECTED_BY_TARGET_MODE, rejected.code());
        assertTrue(rejected.detail().contains("目标模式装配失败"), rejected.detail());
        // ③ 生效抛 → 先全停再回退:已生效的 first 组件被停(stop 在回退落位前)
        int firstStart = boomLog.indexOf("first:start");
        int secondStart = boomLog.indexOf("second:start");
        int firstStop = boomLog.indexOf("first:stop");
        assertTrue(firstStart >= 0 && secondStart >= 0, "两组件都进入生效段");
        assertTrue(firstStop > secondStart, "生效抛后已生效组件被全停(★先 closeAll 再回退)");
        assertLandedOnFallback(fx);
    }

    /** ② 共用断言:装配/唤醒抛点路径都落回退模式、成员有实例、绝无中间态。 */
    private void assertFailurePathFallsBack(Consumer<ProbeComponent.Spec> injector) throws Exception {
        List<String> boomLog = new ArrayList<>();
        LinkedHashMap<String, MatchModeComponent.Specification<?>> specs = new LinkedHashMap<>();
        ProbeComponent.Spec boomSpec = new ProbeComponent.Spec(boomLog);
        injector.accept(boomSpec);
        specs.put("boom", boomSpec);
        SwitchHarness harness = new SwitchHarness("fb",
                threeComponentMode("fb", new ArrayList<>()),
                threeComponentMode("base", new ArrayList<>()),
                SwitchHarness.mode("boommode", 0, 100, specs));
        Fixture fx = new Fixture(harness);

        CompletableFuture<SwitchResult> future = harness.service.switchMode(
                fx.session.id(), forceSel("boommode"), Handoff.empty());
        harness.ticker.tickAll();
        harness.ticker.tickAll();
        harness.ticker.tickAll();

        SwitchResult.Rejected rejected = assertInstanceOf(SwitchResult.Rejected.class, future.get(2, TimeUnit.SECONDS));
        assertEquals(SwitchResult.Reason.REJECTED_BY_TARGET_MODE, rejected.code(),
                "原请求以拒绝完结(最终去向经事件告知)");
        assertLandedOnFallback(fx);
    }

    private void assertLandedOnFallback(Fixture fx) {
        MatchModeInstance current = fx.session.currentInstance().orElse(null);
        assertTrue(current != null, "成员仍有实例(绝不驻留无实例中间态)");
        assertEquals("fb", current.modeId(), "落到回退模式");
        assertEquals(SessionState.RUNNING, fx.session.state());
        Set<UUID> rosterUuids = SwitchHarness.rosterUuids(fx.session);
        assertEquals(rosterUuids,
                current.participants().stream().map(p -> p.uuid()).collect(Collectors.toSet()),
                "回退实例的参赛者与名册一致");
        assertTrue(fx.events.stream().anyMatch(e ->
                        e instanceof SessionEvent.ModeSwitchCompleted completed && "fb".equals(completed.toMode())),
                "回退落位经完成事件告知");
    }

    /** 回退出厂空模式成功(★v1.13/31 号新例):真工厂(SPI 同款)入册作回退目标——
     *  零组件 + 空 mapTags 通配选图,执行失败后回退落位全路径成功,成员全员成参赛者。 */
    @Test
    void factoryShippedEmptyModeServesAsFallbackLanding() throws Exception {
        List<String> boomLog = new ArrayList<>();
        LinkedHashMap<String, MatchModeComponent.Specification<?>> boomSpecs = new LinkedHashMap<>();
        boomSpecs.put("boom", new ProbeComponent.Spec(boomLog).failOnStart("start-boom"));
        SwitchHarness harness = new SwitchHarness(EmptyMatchModeFactory.MODE_ID,
                threeComponentMode("base", new ArrayList<>()),
                SwitchHarness.mode("boommode", 0, 100, boomSpecs));
        harness.registry.register(new EmptyMatchModeFactory());   // 出厂工厂入册(SPI 半边的离线等价)
        Session session = harness.createSession("s1", "base");
        List<SessionEvent> events = SwitchHarness.recordEvents(session);
        for (int i = 0; i < 3; i++) {
            harness.service.join(session.id(), UUID.randomUUID());
        }

        CompletableFuture<SwitchResult> future = harness.service.switchMode(
                session.id(), forceSel("boommode"), Handoff.empty());
        harness.ticker.tickAll();
        harness.ticker.tickAll();
        harness.ticker.tickAll();

        assertInstanceOf(SwitchResult.Rejected.class, future.get(2, TimeUnit.SECONDS),
                "原请求以拒绝完结(boommode 生效抛)");
        MatchModeInstance landed = session.currentInstance().orElseThrow();
        assertEquals(EmptyMatchModeFactory.MODE_ID, landed.modeId(),
                "回退落点 = 出厂空模式(零组件实例,31 号)");
        assertEquals(SessionState.RUNNING, session.state());
        Set<UUID> rosterUuids = SwitchHarness.rosterUuids(session);
        assertEquals(rosterUuids,
                landed.participants().stream().map(p -> p.uuid()).collect(Collectors.toSet()),
                "出厂空模式实例的参赛者与名册一致(零组件 joinGate 默认 ADMIT)");
        assertTrue(events.stream().anyMatch(e -> e instanceof SessionEvent.ModeSwitchCompleted completed
                        && completed.toMode().equals(EmptyMatchModeFactory.MODE_ID)),
                "回退落位经完成事件告知");
    }

    /** 首切实例的 B 段失败(★31 号复审 S1 回归):create 挂接的首切回退与 rollback 自身
     *  的回退不得双重触发——双重时第二发被 beginSwitch 重入守卫拒,误走「回退被拒 →
     *  SEVERE + 强制解散」(fallback 有效却解散,违需求 1「仅 fallback 无效才解散」)。
     *  经 create() 发起(首切 future 带回调),与上方 service.switchMode 发起的 B 段
     *  失败用例互补(彼处 future 无回调,恰绕开本路径——评审指出的测试缺口)。 */
    @Test
    void firstSwitchBFailureParksOnFallbackInsteadOfDissolving() throws Exception {
        List<String> boomLog = new ArrayList<>();
        LinkedHashMap<String, MatchModeComponent.Specification<?>> boomSpecs = new LinkedHashMap<>();
        boomSpecs.put("boom", new ProbeComponent.Spec(boomLog).failOnStart("start-boom"));
        SwitchHarness harness = new SwitchHarness("fb",
                threeComponentMode("fb", new ArrayList<>()),
                SwitchHarness.mode("broken-init", 0, 100, boomSpecs));
        Session session = harness.service.create(
                new SessionSpec(new SessionId("s1"), "broken-init", List.of(), List.of()));
        List<SessionEvent> events = SwitchHarness.recordEvents(session);
        for (int i = 0; i < 3; i++) {
            harness.service.join(session.id(), UUID.randomUUID());
        }

        harness.ticker.tickAll();       // 首切执行段:生效抛 → rollback(complete 时仍在 SWITCHING,回调让位)
        harness.ticker.tickAll();       // rollback 自身的回退落位

        assertEquals(1, harness.service.all().size(), "fallback 有效:会话不解散(S1 曾被误解散)");
        assertEquals("fb", session.currentInstance().orElseThrow().modeId(), "泊回退模式");
        assertEquals(SessionState.RUNNING, session.state());
        assertTrue(events.stream().noneMatch(e -> e instanceof SessionEvent.SessionDissolved),
                "无解散事件(fallback 有效时不得解散)");
        assertFalse(harness.logLines.stream().anyMatch(line ->
                        line.startsWith("SEVERE") && line.contains("fallback switch rejected")),
                "无误报的「回退切换被拒」SEVERE(detail 曾为『切换进行中,重入被拒』): " + harness.logLines);
    }

    // ── 二级回退:回退模式也装配失败 → 解散 ─────────────────────────

    @Test
    void fallbackAlsoFailsDissolvesSessionWithEventAndRelocation() throws Exception {
        List<String> fbLog = new ArrayList<>();
        LinkedHashMap<String, MatchModeComponent.Specification<?>> brokenFb = new LinkedHashMap<>();
        brokenFb.put("fbbad", new ProbeComponent.Spec(fbLog).failOnCreate("fallback-boom"));
        LinkedHashMap<String, MatchModeComponent.Specification<?>> boomSpecs = new LinkedHashMap<>();
        boomSpecs.put("boom", new ProbeComponent.Spec(new ArrayList<>()).failOnStart("start-boom"));
        SwitchHarness harness = new SwitchHarness("fb",
                SwitchHarness.mode("fb", 0, 100, brokenFb),
                threeComponentMode("base", new ArrayList<>()),
                SwitchHarness.mode("boommode", 0, 100, boomSpecs));
        Fixture fx = new Fixture(harness);

        CompletableFuture<SwitchResult> future = harness.service.switchMode(
                fx.session.id(), forceSel("boommode"), Handoff.empty());
        harness.ticker.tickAll();
        harness.ticker.tickAll();
        harness.ticker.tickAll();
        harness.ticker.tickAll();

        assertInstanceOf(SwitchResult.Rejected.class, future.get(2, TimeUnit.SECONDS));
        assertTrue(fx.harness.service.all().isEmpty(), "会话已从注册表摘除(二级回退=解散)");
        assertTrue(fx.events.stream().anyMatch(e -> e instanceof SessionEvent.SessionDissolved dissolved
                        && dissolved.reason() == DissolveReason.FALLBACK_DISSOLVE),
                "发解散事件(原因 FALLBACK_DISSOLVE)");
        assertEquals(1, fx.harness.relocations.size(), "全员传送主城的函数被调用(三级接缝)");
        assertEquals(3, fx.harness.relocations.get(0).size(), "三人都被安置");
        // ★v1.4 解散销毁世界副本:dissolveNow 的 finally 发起 discardSession(幂等解散恰一次)
        assertEquals(1, fx.harness.worlds.discardCount, "解散应触发 discardSession 恰一次");
        assertEquals(fx.session.id(), fx.harness.worlds.discardedSessions.get(0),
                "销毁目标 = 被解散的会话");
        assertTrue(fx.harness.logLines.stream().anyMatch(line -> line.startsWith("SEVERE")),
                "二级回退记 SEVERE 日志");
    }

    // ── ★6/★7 复测:切换路径(B2)中 close 幂等且清场逆序 ────────────────

    @Test
    void b2CloseIsIdempotentAndStopsInReverseOrderOnSwitchPath() throws Exception {
        Fixture fx = Fixture.standard();        // base = 三组件 c1/c2/c3
        fx.componentLog.clear();
        fx.harness.service.switchMode(fx.session.id(), forceSel("fb"), Handoff.empty());
        fx.harness.ticker.tickAll();
        fx.harness.ticker.tickAll();

        List<String> stops = fx.componentLog.stream()
                .filter(line -> line.endsWith(":stop")).toList();
        assertEquals(List.of("c3:stop", "c2:stop", "c1:stop"), stops,
                "★7 清场逆序(B2 路径重放)");

        int stopCount = stops.size();
        fx.original.close();               // ★6 幂等复测:切换后再次 close 零新增 stop
        fx.original.close();
        assertEquals(stopCount, (int) fx.componentLog.stream().filter(l -> l.endsWith(":stop")).count(),
                "★6 close 幂等(连调多次只清一次)");
        assertEquals("fb", fx.session.currentInstance().orElseThrow().modeId());
    }

    // ── ★65 世界清场(★v1.9/27 号):切换与解散接缝的清场断言族 ──────────
    // 全部建在 FakeWorldPort.sticky(同 (map, session) 幂等同句柄,镜像真端口表命中)
    // 与 seamLog 时序录之上;immediate() 每次新句柄 + 新代理 → 同世界判据恒 false,
    // B3.5 同世界路径离线不可达(评审 blocker④ 增补)。

    /** 异图切换(m1→m2→m1 往返,显式 mapId 选区):B5 殿后清场每次恰一次、
     *  from/to 为对应代理世界、时序先于 release。 */
    @Test
    void crossMapSwitchClearsOldWorldTowardNewWorldBeforeRelease() throws Exception {
        List<String> seam = new ArrayList<>();
        SwitchHarness harness = new SwitchHarness(seam, "fb",
                threeComponentMode("fb", new ArrayList<>()),
                threeComponentMode("base", new ArrayList<>()));
        Fixture fx = new Fixture(harness);
        // 异图 = 显式 mapId 选区:无 mapId 时 resolveMap 取装载序第一张(m1),与本用例同图
        fx.harness.service.switchMode(fx.session.id(),
                new MatchModeSelection("fb", Optional.of("m2"), true), Handoff.empty());
        fx.harness.ticker.tickAll();
        fx.harness.ticker.tickAll();
        fx.harness.service.switchMode(fx.session.id(),
                new MatchModeSelection("fb", Optional.of("m1"), true), Handoff.empty());
        fx.harness.ticker.tickAll();
        fx.harness.ticker.tickAll();

        assertEquals(2, fx.harness.worldClears.size(), "异图切换每次恰一次清场(B5 殿后)");
        // from/to = 对应代理世界:往返两次的 to/from 互指同一代理引用(m1 世界 ↔ m2 世界)
        assertSame(fx.harness.worldClears.get(0).to(), fx.harness.worldClears.get(1).from(),
                "第二次清场的 from = 第一次的 to(m2 世界同一代理引用)");
        assertSame(fx.harness.worldClears.get(0).from(), fx.harness.worldClears.get(1).to(),
                "第二次清场的 to = 第一次的 from(m1 世界同一代理引用)");
        assertEquals(List.of("worldClear", "release", "worldClear", "release"), fx.harness.seamLog,
                "时序:清场(完成事件之后)先于 release(B5 finally 同缝,§3.7 释放时机)");
    }

    /** 同图重开(sticky 表命中同句柄):B3.5 清场先行于 B4、from == to(同一代理引用)、
     *  finally 门闩不重复清(恰 1 非恰 2)。 */
    @Test
    void sameWorldReopenClearsBeforeComponentStartExactlyOnce() throws Exception {
        List<String> seam = new ArrayList<>();
        LinkedHashMap<String, MatchModeComponent.Specification<?>> specs = new LinkedHashMap<>();
        specs.put("probe", new ProbeComponent.Spec(new ArrayList<>())
                .onStart(p -> seam.add("probe:start")));    // 探针生效钩子同写时序表(B4 观测点)
        SwitchHarness harness = new SwitchHarness(seam, "fb",
                threeComponentMode("fb", new ArrayList<>()),
                SwitchHarness.mode("base", 0, 100, specs));
        Fixture fx = new Fixture(harness);
        int windowStart = fx.harness.seamLog.size();        // 本切换窗口(首切实例的钩子已在前)
        fx.harness.service.switchMode(fx.session.id(), forceSel("base"), Handoff.empty());
        fx.harness.ticker.tickAll();
        fx.harness.ticker.tickAll();

        assertEquals(1, fx.harness.worldClears.size(),
                "同图重开清场恰一次(B3.5 先行;finally !sameWorldReuse 门闩不再清)");
        assertSame(fx.harness.worldClears.get(0).from(), fx.harness.worldClears.get(0).to(),
                "同世界复用:from == to(sticky 表命中同句柄,同一代理引用)");
        assertEquals(List.of("worldClear", "probe:start", "release"),
                fx.harness.seamLog.subList(windowStart, fx.harness.seamLog.size()),
                "时序:清场(B3.5)先行于 B4 组件生效(probe:start),release 殿后");
    }

    /** 清场注入抛异常 → 切换仍成功、release 照发、记 WARN(★v1.9 best-effort 不阻塞)。 */
    @Test
    void worldClearFailureDoesNotBlockSwitchOrRelease() throws Exception {
        SwitchHarness harness = SwitchHarness.throwingWorldClear("fb",
                threeComponentMode("fb", new ArrayList<>()),
                threeComponentMode("base", new ArrayList<>()));
        Fixture fx = new Fixture(harness);
        int releasesBefore = fx.harness.worlds.releaseCount;
        CompletableFuture<SwitchResult> future = fx.harness.service.switchMode(
                fx.session.id(), forceSel("fb"), Handoff.empty());
        fx.harness.ticker.tickAll();
        fx.harness.ticker.tickAll();

        assertInstanceOf(SwitchResult.Succeeded.class, future.get(2, TimeUnit.SECONDS),
                "清场抛异常不阻塞:切换仍成功完结");
        assertEquals("fb", fx.session.currentInstance().orElseThrow().modeId(), "落位不受清场失败影响");
        assertEquals(releasesBefore + 1, fx.harness.worlds.releaseCount, "清场抛异常 release 照发");
        assertTrue(fx.harness.logLines.stream().anyMatch(line ->
                        line.startsWith("WARNING") && line.contains("World clear on switch failed")),
                "清场失败记 WARN(best-effort,沿用三级兜底语气)");
    }

    /** 解散清场:heldWorld 非 null → hubWorldClearer 恰一次、先于 discardSession(finally 接缝)。 */
    @Test
    void dissolveClearsWorldToHubBeforeDiscardSession() throws Exception {
        List<String> seam = new ArrayList<>();
        SwitchHarness harness = new SwitchHarness(seam, "fb",
                threeComponentMode("fb", new ArrayList<>()),
                threeComponentMode("base", new ArrayList<>()));
        Session session = harness.createSession("s1", "base");

        harness.service.dissolve(session.id(), DissolveReason.ADMIN);

        assertEquals(1, harness.hubWorldClears.size(), "解散清场恰一次(先名册后世界)");
        assertTrue(harness.worldClears.isEmpty(), "解散不走切换清场口(两口分立)");
        assertEquals(1, harness.worlds.discardCount, "discardSession 恰一次(既有口径不变)");
        assertTrue(seam.indexOf("hubWorldClear") < seam.indexOf("discardSession"),
                "时序:世界清场(摘句柄后)先于 discardSession(dissolve finally 接缝)");
    }

    /** heldWorld 为 null(初始模式未注册 → 首切被拒、句柄从未发放)→ 清场口跳过、无害。 */
    @Test
    void dissolveWithoutWorldHandleSkipsClearHarmlessly() throws Exception {
        SwitchHarness harness = new SwitchHarness("fb",
                threeComponentMode("fb", new ArrayList<>()),
                threeComponentMode("base", new ArrayList<>()));
        Session session = harness.service.create(
                new SessionSpec(new SessionId("ghost"), "nope", List.of(), List.of()));

        harness.service.dissolve(session.id(), DissolveReason.ADMIN);

        assertTrue(harness.hubWorldClears.isEmpty(), "heldWorld 为 null → 清场口跳过、无害");
        assertEquals(1, harness.worlds.discardCount, "无句柄也照发 discardSession(既有口径)");
        assertTrue(harness.service.all().isEmpty(), "会话已从注册表摘除");
    }
}
