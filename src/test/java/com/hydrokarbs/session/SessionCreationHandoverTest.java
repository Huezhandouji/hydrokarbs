package com.hydrokarbs.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.hydrokarbs.engine.session.MatchModeSelection;
import com.hydrokarbs.engine.session.Session;
import com.hydrokarbs.engine.session.SessionId;
import com.hydrokarbs.engine.session.SessionSpec;
import com.hydrokarbs.engine.session.SwitchResult;

/**
 * ★v1.3(D5)会话创建权移交宿主(21 号修正案)后的最小语义锁定:
 * <ol>
 *   <li>会话唯一创建路径 = 外部调用 {@code SessionService.create(SessionSpec)}(§3.2);
 *       装配基座只构造服务、零会话;</li>
 *   <li>零会话状态下节拍器空转合法:all() 空 + tickAll 多拍,不炸、零日志;</li>
 *   <li>switchMode 对不存在会话查无即短路拒绝,不触碰模式注册面。</li>
 * </ol>
 * 零 Bukkit import(testkit 离线装配,同 SwitchHarness 基座)。
 */
@Timeout(30)
class SessionCreationHandoverTest {

    // ── ① create 由外部调用驱动:装配零会话 → 外部 create 才有会话 ──────────

    @Test
    void sessionsOnlyExistAfterExternalCreateCall() {
        // 装配基座 = D5 后的框架 onEnable 镜像:服务就绪、零会话
        SwitchHarness harness = new SwitchHarness("fb", SwitchHarness.mode("fb", 0, 100));
        assertTrue(harness.service.all().isEmpty(),
                "装配完成(无外部调用)= 零会话:D5 后框架 onEnable 零会话");

        // 宿主在编排点调 create(§3.2 冻结签名)——唯一创建路径
        Session session = harness.service.create(
                new SessionSpec(new SessionId("host-made"), "", java.util.List.of()));
        assertEquals(1, harness.service.all().size(), "外部 create 后会话入册");
        assertTrue(harness.service.session(new SessionId("host-made")).isPresent(),
                "按 id 可取到宿主建的会话");
        assertEquals(session.id(), harness.service.session(new SessionId("host-made")).orElseThrow().id());
    }

    // ── ② 零会话状态下节拍器空转:合法状态,不炸、零日志 ────────────────────

    @Test
    void zeroSessionsTickerIdlesSilently() {
        SwitchHarness harness = new SwitchHarness("fb");
        for (int i = 0; i < 5; i++) {
            harness.ticker.tickAll();      // 空会话集遍历(对应 EngineTicker 对 all() 空转)
        }
        assertTrue(harness.service.all().isEmpty(), "空转后仍零会话");
        assertTrue(harness.logLines.isEmpty(),
                "零会话空转零日志输出(无 WARN/SEVERE 噪音): " + harness.logLines);
    }

    // ── ③ switchMode 对不存在会话:短路拒绝,不触碰模式注册面 ────────────────

    @Test
    void switchModeRejectsUnknownSession() throws Exception {
        // 零模式注册:证明拒绝发生在会话查询处(先于 A1 模式在册校验)
        SwitchHarness harness = new SwitchHarness("fb");
        var future = harness.service.switchMode(
                new SessionId("no-such-session"), MatchModeSelection.of("any-mode"), com.hydrokarbs.engine.session.Handoff.empty());

        SwitchResult.Rejected rejected = assertInstanceOf(SwitchResult.Rejected.class,
                future.get(2, TimeUnit.SECONDS), "不存在会话 → 拒绝完结(永不悬挂)");
        assertEquals(SwitchResult.Reason.REJECTED_BY_TARGET_MODE, rejected.code(),
                "拒绝码 = REJECTED_BY_TARGET_MODE(既有 §3.2-A1 路径前的会话查询短路)");
        assertTrue(rejected.detail().contains("会话不存在"),
                "detail 点名会话不存在: " + rejected.detail());
        assertTrue(harness.service.all().isEmpty(), "拒绝后仍零会话(无状态分叉)");
    }
}
