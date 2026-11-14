package com.example.their;

import java.util.ArrayList;
import java.util.List;

import com.hydrokarbs.engine.component.SessionComponent;
import com.hydrokarbs.engine.event.SessionEvent;
import com.hydrokarbs.engine.port.SessionServices;

/**
 * 第三方会话组件贡献(★v1.1-D14 通道一的贡献物样例;7.5 施工文档步骤 10;
 * ★v1.6 起经 {@code HydrokarbsApi#registerSessionComponents} 显式注册):
 * 宿主级会话策略的最小占位——真实策略组件在此实现自定义重连/宽限/准入记账等
 * (07 §7.3「适用」行);本样例只演示贡献声明形状与纪律。
 *
 * <p>纪律要点(镜像 {@link TheirBannerComponent} 的基类纪律,§2.5.1):
 * {@code create} 是唯一装配点(构造私有);svc 构造期注入先于一切钩子;
 * 异常只隔离自己(D9 quarantine,会话不解散);随 dissolve 全灭(无运行期拆卸)。
 * 贡献面 = 本 Spec 经 {@code HydrokarbsApi#registerSessionComponents} 进
 * 框架收录闸(干跑校验:组件 id = 供给面类型名,与既有贡献撞名 → 响亮拒)。
 */
public final class TheirSessionComponent extends SessionComponent {

    /** 装配描述符:泛型实参声明本组件类型;组件 id 由供给面类型名推导(框架口径)。 */
    public static final class Spec extends SessionComponent.Specification<TheirSessionComponent> {

        public Spec() {
            super("their_session_notice");
        }

        @Override
        public TheirSessionComponent create(String id, SessionServices svc) {
            return new TheirSessionComponent(id, svc);
        }
    }

    /** 收到的事件类型名留档(演示观察口;真实组件在此实现策略)。 */
    private final List<String> receivedEvents = new ArrayList<>();

    private TheirSessionComponent(String id, SessionServices svc) {
        super(id, svc);
    }

    /** 每拍推进:零副作用占位(禁止阻塞/IO,§2.5.1)。 */
    @Override
    public void update() {
    }

    /** 会话事件观察(隔离语义由框架兜底;计数留档供消费方自查)。 */
    @Override
    public void onEvent(SessionEvent event) {
        receivedEvents.add(event.getClass().getSimpleName());
    }

    /** 收到的事件类型名快照(演示读口)。 */
    public List<String> receivedEvents() {
        return List.copyOf(receivedEvents);
    }
}
