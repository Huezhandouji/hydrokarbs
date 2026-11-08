package com.hydrokarbs.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.hydrokarbs.engine.AttributeBag;
import com.hydrokarbs.engine.component.MatchModeComponent;
import com.hydrokarbs.engine.event.SessionEvent;
import com.hydrokarbs.engine.mode.MatchMode;
import com.hydrokarbs.engine.mode.MatchModeMetadata;
import com.hydrokarbs.engine.session.DissolveReason;
import com.hydrokarbs.engine.session.Handoff;
import com.hydrokarbs.engine.session.MatchModeSelection;
import com.hydrokarbs.engine.session.Session;
import com.hydrokarbs.engine.session.SessionId;
import com.hydrokarbs.engine.session.SessionPolicy;
import com.hydrokarbs.engine.session.SessionSpec;
import com.hydrokarbs.engine.session.SessionState;
import com.hydrokarbs.engine.session.SwitchResult;
import com.hydrokarbs.testkit.ProbeComponent;

/**
 * 空值/缺省语义(★v1.3 首建;★v1.13/31 号 supersede 整类重立):
 * <ol>
 *   <li>初始模式空/未注册 → WARN + 泊回退模式;后补非 force 切换受最短驻留约束
 *       (推时钟过窗口或传 force 后可用);</li>
 *   <li>回退模式空串 → SEVERE + 即时 FALLBACK_DISSOLVE 解散 + 成员安置
 *       (默认配置下出厂空模式兜底,空串 = 配置错误);</li>
 *   <li>回退模式未注册 → 回退切换被拒 → SEVERE(带 detail)+ 解散。</li>
 * </ol>
 * 附:模式侧空 mapTags = 通配任意地图(31 号决策 B);模式元数据零键 = 中性缺省
 * (min=0/上限全开,经 MatchModeMetadata 读法)。引擎源码零模式字面量(§14.21)。
 */
@Timeout(30)
class NeutralDefaultsSemanticsTest {

    private static MatchModeSelection forceSel(String modeId) {
        return new MatchModeSelection(modeId, java.util.Optional.empty(), true);
    }

    // ── ① 初始模式空/未注册:WARN + 泊回退;后补受驻留约束(可 force/过窗口) ──

    @Test
    void blankInitialModeWarnsParksOnFallbackThenLaterSwitchWorksAfterDwell() throws Exception {
        SwitchHarness harness = new SwitchHarness("fb", SwitchHarness.mode("fb", 0, 100));
        Session session = harness.service.create(
                new SessionSpec(new SessionId("s1"), "", List.of(), List.of()));
        harness.ticker.tickAll();
        harness.ticker.tickAll();

        assertEquals(1, harness.service.all().size(), "空初始模式不炸启动:会话保留在册");
        assertEquals(SessionState.RUNNING, session.state(), "泊回退后回常态");
        assertEquals("fb", session.currentInstance().orElseThrow().modeId(),
                "首切失败泊回退模式(31 号:不再无实例驻留)");
        assertTrue(harness.logLines.stream().anyMatch(line ->
                        line.startsWith("WARNING") && line.contains("initial mode")),
                "空初始模式记 WARN: " + harness.logLines);
        assertTrue(harness.logLines.stream().noneMatch(line ->
                        line.startsWith("SEVERE") && line.contains("initial mode")),
                "首切失败仍不 SEVERE(已知问题#5 消除口径保留): " + harness.logLines);

        // 泊回退已记切换时刻:窗口内非 force 后补被防护①拒(31 号行为回退,登记)
        SwitchResult.Rejected rejected = assertInstanceOf(SwitchResult.Rejected.class,
                harness.service.switchMode(session.id(), MatchModeSelection.of("fb"), Handoff.empty())
                        .get(2, TimeUnit.SECONDS));
        assertTrue(rejected.detail().contains("驻留不足"),
                "泊回退后最短驻留窗口内非 force 后补被拒: " + rejected.detail());

        // 推时钟过窗口 → 后补切换真实可用(「待后补切换」不是空话)
        for (int i = 0; i < SessionPolicy.DEFAULT_MIN_DWELL_TICKS + 2; i++) {
            session.idleTimers();
        }
        var future = harness.service.switchMode(session.id(), MatchModeSelection.of("fb"), Handoff.empty());
        harness.ticker.tickAll();
        harness.ticker.tickAll();
        assertInstanceOf(SwitchResult.Succeeded.class, future.get(2, TimeUnit.SECONDS));
        assertEquals("fb", session.currentInstance().orElseThrow().modeId(), "后补切换后实例就位");
    }

    @Test
    void unregisteredInitialModeWarnsAndParksOnFallback() throws Exception {
        SwitchHarness harness = new SwitchHarness("fb", SwitchHarness.mode("fb", 0, 100));
        Session session = harness.service.create(
                new SessionSpec(new SessionId("s1"), "ghost-mode", List.of(), List.of()));
        harness.ticker.tickAll();
        harness.ticker.tickAll();

        assertEquals(1, harness.service.all().size(), "未注册初始模式:会话保留(与空串同语义)");
        assertEquals(SessionState.RUNNING, session.state());
        assertEquals("fb", session.currentInstance().orElseThrow().modeId(), "泊回退模式");
        assertTrue(harness.logLines.stream().anyMatch(line ->
                        line.startsWith("WARNING") && line.contains("initial mode")),
                "未注册初始模式同样记 WARN: " + harness.logLines);
        assertTrue(harness.logLines.stream().noneMatch(line ->
                        line.startsWith("SEVERE") && line.contains("initial mode")),
                "未注册初始模式不再 SEVERE: " + harness.logLines);
    }

    // ── ② 回退模式空串:SEVERE(31 号跳级)+ 即时解散 + 成员安置 ────────────

    @Test
    void blankFallbackSevereDissolvesImmediatelyWithRelocation() throws Exception {
        List<String> boomLog = new ArrayList<>();
        LinkedHashMap<String, MatchModeComponent.Specification<?>> boomSpecs = new LinkedHashMap<>();
        boomSpecs.put("boom", new ProbeComponent.Spec(boomLog).failOnStart("start-boom"));
        SwitchHarness harness = new SwitchHarness("",
                SwitchHarness.mode("base", 0, 100),
                SwitchHarness.mode("boommode", 0, 100, boomSpecs));
        Session session = harness.createSession("s1", "base");
        List<SessionEvent> events = SwitchHarness.recordEvents(session);
        for (int i = 0; i < 3; i++) {
            harness.service.join(session.id(), UUID.randomUUID());
        }

        var future = harness.service.switchMode(session.id(), forceSel("boommode"), Handoff.empty());
        for (int i = 0; i < 3; i++) {
            harness.ticker.tickAll();
        }

        assertInstanceOf(SwitchResult.Rejected.class, future.get(2, TimeUnit.SECONDS));
        assertTrue(harness.service.all().isEmpty(), "空 fallback:即时二级解散兜底(31 号)");
        assertTrue(events.stream().anyMatch(e -> e instanceof SessionEvent.SessionDissolved dissolved
                        && dissolved.reason() == DissolveReason.FALLBACK_DISSOLVE),
                "发解散事件(原因 FALLBACK_DISSOLVE)");
        assertTrue(harness.logLines.stream().anyMatch(line ->
                        line.startsWith("SEVERE") && line.contains("no valid fallback mode")),
                "空串回退记 SEVERE(31 号,原 WARN 跳级): " + harness.logLines);
        assertEquals(1, harness.relocations.size(), "成员安置函数被调用(二级兜底)");
    }

    // ── ③ 回退模式未注册:回退切换被拒 → SEVERE(带 detail)+ 解散 ──────────

    @Test
    void unregisteredFallbackSevereDissolvesWithDetail() throws Exception {
        List<String> boomLog = new ArrayList<>();
        LinkedHashMap<String, MatchModeComponent.Specification<?>> boomSpecs = new LinkedHashMap<>();
        boomSpecs.put("boom", new ProbeComponent.Spec(boomLog).failOnStart("start-boom"));
        SwitchHarness harness = new SwitchHarness("ghost-fb",
                SwitchHarness.mode("base", 0, 100),
                SwitchHarness.mode("boommode", 0, 100, boomSpecs));
        Session session = harness.createSession("s1", "base");
        List<SessionEvent> events = SwitchHarness.recordEvents(session);
        for (int i = 0; i < 3; i++) {
            harness.service.join(session.id(), UUID.randomUUID());
        }

        var future = harness.service.switchMode(session.id(), forceSel("boommode"), Handoff.empty());
        for (int i = 0; i < 3; i++) {
            harness.ticker.tickAll();
        }

        assertInstanceOf(SwitchResult.Rejected.class, future.get(2, TimeUnit.SECONDS));
        assertTrue(harness.service.all().isEmpty(), "未注册 fallback:回退被拒 → 二级解散(31 号单点判据)");
        assertTrue(events.stream().anyMatch(e -> e instanceof SessionEvent.SessionDissolved dissolved
                        && dissolved.reason() == DissolveReason.FALLBACK_DISSOLVE),
                "发解散事件(原因 FALLBACK_DISSOLVE)");
        assertTrue(harness.logLines.stream().anyMatch(line ->
                        line.startsWith("SEVERE") && line.contains("fallback switch rejected")),
                "回退被拒记 SEVERE(带 detail): " + harness.logLines);
        assertTrue(harness.logLines.stream().anyMatch(line ->
                        line.startsWith("SEVERE") && line.contains("fallback mode also failed")),
                "二级兜底 SEVERE 日志原样(dissolveNow 终态行): " + harness.logLines);
    }

    // ── ④ 模式侧空 mapTags = 通配任意地图(31 号决策 B):无 mapId 直切成功 ──

    @Test
    void emptyMapTagsModeResolvesMapByWildcardAndSwitches() throws Exception {
        // 空 mapTags 模式(harness 的便捷口固定带 TAG,这里显式 Build 以落空集)
        MatchMode wild = new MatchMode.Builder("wild")
                .metadata(MatchModeMetadata.addTo(new AttributeBag.Builder(), false, 0, 100).build())
                .build();
        SwitchHarness harness = new SwitchHarness("fb",
                SwitchHarness.mode("fb", 0, 100),
                wild);
        Session session = harness.createSession("s1", "fb");

        var future = harness.service.switchMode(session.id(), forceSel("wild"), Handoff.empty());
        harness.ticker.tickAll();
        harness.ticker.tickAll();

        assertInstanceOf(SwitchResult.Succeeded.class, future.get(2, TimeUnit.SECONDS),
                "空 mapTags 通配:装载序第一张池内图可解析(31 号决策 B)");
        assertEquals("wild", session.currentInstance().orElseThrow().modeId(), "直切实例就位");
    }

    // ── ⑤ 模式元数据零键 = 中性缺省:非 force 人数窗仍按约定键读法裁决 ──────

    @Test
    void noKeyMetadataModeDefaultsToNeutralWindowAndSwitches() throws Exception {
        // 零键模式:.metadata(AttributeBag.empty())——不写任何约定键(min/max 缺席)
        MatchMode noKeys = new MatchMode.Builder("nokeys")
                .mapTags(SwitchHarness.TAG)
                .metadata(AttributeBag.empty())
                .build();
        SwitchHarness harness = new SwitchHarness("fb",
                SwitchHarness.mode("fb", 0, 100),
                SwitchHarness.mode("tight", 5, 100),
                noKeys);
        Session session = harness.createSession("s1", "fb");
        for (int i = 0; i < 3; i++) {
            harness.service.join(session.id(), UUID.randomUUID());
        }
        for (int i = 0; i < SessionPolicy.DEFAULT_MIN_DWELL_TICKS + 2; i++) {
            session.idleTimers();
        }

        // 3 人在册、非 force 切入 min=5 模式:A4 人数窗外拒绝(缺省读法仍在岗)
        SwitchResult.Rejected rejected = assertInstanceOf(SwitchResult.Rejected.class,
                harness.service.switchMode(session.id(), MatchModeSelection.of("tight"), Handoff.empty())
                        .get(2, TimeUnit.SECONDS));
        assertTrue(rejected.detail().contains("人数窗"), "3 人 < min=5 非豁免被拒: " + rejected.detail());

        // 切入零键模式本身成功:min 缺省 = 0、max 缺省 = 全开(中性缺省语义没坏)
        var future = harness.service.switchMode(session.id(), MatchModeSelection.of("nokeys"), Handoff.empty());
        harness.ticker.tickAll();
        harness.ticker.tickAll();
        assertInstanceOf(SwitchResult.Succeeded.class, future.get(2, TimeUnit.SECONDS),
                "零键元数据 = 中性缺省:3 人在册非 force 可切入");
        assertEquals("nokeys", session.currentInstance().orElseThrow().modeId(), "零键模式实例就位");
    }
}
