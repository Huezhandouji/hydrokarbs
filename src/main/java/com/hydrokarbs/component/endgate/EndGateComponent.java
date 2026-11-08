package com.hydrokarbs.component.endgate;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.hydrokarbs.engine.component.MatchModeComponent;
import com.hydrokarbs.engine.instance.MatchModeInstanceView;
import com.hydrokarbs.engine.port.MatchModeInstanceServices;
import com.hydrokarbs.engine.session.EndResult;
import com.hydrokarbs.engine.session.Handoff;
import com.hydrokarbs.engine.session.MatchModeConfig;
import com.hydrokarbs.engine.port.SessionView;
import com.hydrokarbs.map.MapDefinition;

/**
 * 结束判定门组件:持有判定器列表,update 里按优先级降序求值,
 * 命中即 {@code svc().self().end(...)}(03-会话与模式切换 §3.4)。
 *
 * <p>结束是组件的主动声明,不被框架轮询;引擎不认识判定器,
 * 不想要结束判定的模式就不挂本组件(要就挂、不要不挂)。
 *
 * <p>★ 不自建防抖/冷却:风暴防护归会话层最短驻留(§3.5),
 * 重复 end 由容器的结束请求幂等护栏挡掉。
 */
public final class EndGateComponent extends MatchModeComponent {

    /** 交接键:把结局写进自己命名空间,供下一实例展示(键随主人,§3.3)。 */
    public static final Handoff.Key<MatchOutcome> HANDOFF_OUTCOME =
            Handoff.Key.of("endgate", "outcome", MatchOutcome.class);

    private final List<EndCondition> conditions;
    private final InstanceView view = new InstanceView();   // 无状态适配器,构造期建一次

    private MatchOutcome lastOutcome;   // 状态唯一:命中后保存,供统计/展示读取

    /** 装配描述符:priority 在 EndCondition 上,这里只收列表。 */
    public static final class Spec extends MatchModeComponent.Specification<EndGateComponent> {

        private final List<EndCondition> conditions;

        public Spec(List<EndCondition> conditions) {
            super("endgate");
            if (conditions == null || conditions.isEmpty()) {
                throw new IllegalArgumentException("判定器列表为空:不需要结束判定的模式不应挂本组件(要就挂、不要不挂)");
            }
            this.conditions = List.copyOf(conditions);
        }

        @Override
        public EndGateComponent create(String id, MatchModeInstanceServices svc) {
            return new EndGateComponent(id, svc, conditions);
        }
    }

    EndGateComponent(String id, MatchModeInstanceServices svc, List<EndCondition> conditions) {
        super(id, svc);
        this.conditions = conditions;
    }

    /**
     * §3.4 算法逐字翻译:优先级降序 → 求值 → findFirst → 记结局 + 交去向。
     * 一行不多、一行不少。
     */
    @Override
    public void update() {
        conditions.stream()
                .sorted(Comparator.comparingInt(EndCondition::priority).reversed())
                .map(c -> c.evaluate(view()))
                .flatMap(Optional::stream)
                .findFirst()
                .ifPresent(v -> {
                    lastOutcome = v.outcome();                       // 结局留在内容层
                    svc().self().end(new EndResult(v.intent()));     // 只有去向交给引擎
                });
    }

    /** 强类型读口:统计/展示组件从这里取结局(引擎不知道它存在)。 */
    public Optional<MatchOutcome> outcome() {
        return Optional.ofNullable(lastOutcome);
    }

    /** 把结局写进自己命名空间的交接键(供下一实例展示;未命中时不写)。 */
    @Override
    public void onHandoff(Handoff.Builder out) {
        if (lastOutcome != null) {
            out.put(HANDOFF_OUTCOME, lastOutcome);
        }
    }

    /** 实例只读视图(嵌套适配器,委托服务集读口)。 */
    private MatchModeInstanceView view() {
        return view;
    }

    /** 服务集背后的只读视图(判定器求值的实参来源)。 */
    private final class InstanceView implements MatchModeInstanceView {

        @Override
        public <T extends MatchModeComponent> T component(Class<T> type) {
            return svc().components().get(type);
        }

        @Override
        public MatchModeConfig cfg() {
            return svc().self().cfg();
        }

        @Override
        public MapDefinition map() {
            return svc().self().map();
        }

        @Override
        public long tick() {
            return svc().self().tick();
        }

        @Override
        public SessionView session() {
            return svc().self().session();   // 经服务集自面取所属会话的只读视图
        }
    }
}
