package com.hydrokarbs.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import com.hydrokarbs.api.view.SessionInfo;
import com.hydrokarbs.engine.instance.MatchModeInstance;
import com.hydrokarbs.engine.session.Handoff;
import com.hydrokarbs.engine.session.JoinResult;
import com.hydrokarbs.engine.session.LeaveReason;
import com.hydrokarbs.engine.session.MatchModeSelection;
import com.hydrokarbs.engine.session.Session;
import com.hydrokarbs.engine.session.SessionId;
import com.hydrokarbs.engine.session.SessionMember;
import com.hydrokarbs.engine.session.SessionService;
import com.hydrokarbs.engine.session.SwitchResult;

/**
 * {@link SessionHandle} 实现体(★v1.15/33 号;ADR-0021,包私有):不可变定位器——
 * 持 {@code SessionId} + 委托({@code SessionService}/{@code mainExecutor}),
 * 不持活会话引用,每次操作现解析。{@code switchMode} 的任意线程 → 回主线程、
 * 异常经 future、永不悬挂逻辑自 {@code HydrokarbsApiImpl} 原样迁入(行为不变);
 * 会话快照转换 {@link #snapshot} 在本类静态单点(「一份不写两处」)。
 */
final class SessionHandleImpl implements SessionHandle {

    private final SessionId id;
    private final SessionService service;
    private final Executor mainExecutor;

    /** 依赖全量构造注入(null 即抛);句柄无状态迁移,消亡判定全靠现解析。 */
    SessionHandleImpl(SessionId id, SessionService service, Executor mainExecutor) {
        this.id = Objects.requireNonNull(id, "id");
        this.service = Objects.requireNonNull(service, "service");
        this.mainExecutor = Objects.requireNonNull(mainExecutor, "mainExecutor");
    }

    @Override
    public String sessionId() {
        return id.value();
    }

    @Override
    public Optional<SessionInfo> info() {
        return service.session(id).map(SessionHandleImpl::snapshot);
    }

    @Override
    public JoinResult join(UUID player) {
        // 引擎既有裁决原样透传:会话不存在(已消亡)/解散中/入场拒绝 → Rejected
        // (detail 人读,§3.2)——逻辑自 HydrokarbsApiImpl#joinSession 原样迁入。
        return service.join(id, player);
    }

    @Override
    public void leave(UUID player, LeaveReason reason) {
        // 会话域守卫(★v1.15/33 号裁决二):玩家现属会话 == 本句柄会话才移出,
        // 否则 no-op;主线程内查后调,无竞态。
        service.sessionOf(player)
                .filter(current -> current.id().equals(id))
                .ifPresent(current -> service.leave(player, reason));
    }

    @Override
    public CompletableFuture<SwitchResult> switchMode(MatchModeSelection target, Handoff payload) {
        Objects.requireNonNull(target, "target");
        CompletableFuture<SwitchResult> future = new CompletableFuture<>();
        // 任意线程发起 → 回主线程查表与发起(§3.2/§14.1);任何异常经 future
        // 传播——绝不让异常落进执行器、future 永不悬挂(§3.2)。
        // 逻辑自 HydrokarbsApiImpl#switchMode 原样迁入(★v1.15/33 号,行为不变)。
        mainExecutor.execute(() -> {
            try {
                Optional<Session> found = service.session(id);
                CompletableFuture<SwitchResult> delegated = found.isEmpty()
                        ? CompletableFuture.completedFuture(new SwitchResult.Rejected(
                                SwitchResult.Reason.MODE_NOT_FOUND, "session " + id.value() + " 不存在"))
                        : service.switchMode(found.get().id(), target, payload);
                delegated.whenComplete((result, err) -> {
                    if (err != null) {
                        future.completeExceptionally(err);
                    } else {
                        future.complete(result);
                    }
                });
            } catch (RuntimeException e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    // ── 转换(快照纪律:转换点一次性取值、集合拷贝、不持活引用——7.1 要点 1) ──

    /** 会话 → 快照(★v1.15 起单点居本类):无当前实例(含切换间隙)modeId/mapId 空串;members 含宽限期成员。 */
    static SessionInfo snapshot(Session session) {
        Optional<MatchModeInstance> current = session.currentInstance();
        String modeId = current.map(MatchModeInstance::modeId).orElse("");
        String mapId = current.map(instance -> instance.view().map().id()).orElse("");
        List<UUID> members = session.roster().all().stream().map(SessionMember::uuid).toList();
        return new SessionInfo(session.id().value(), modeId, mapId, members,
                session.state().name());
    }
}
