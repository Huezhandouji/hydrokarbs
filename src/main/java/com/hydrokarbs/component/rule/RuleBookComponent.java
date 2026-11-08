package com.hydrokarbs.component.rule;

import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.hydrokarbs.engine.component.MatchModeComponent;
import com.hydrokarbs.engine.event.MatchModeInstanceEvent;
import com.hydrokarbs.engine.port.MatchModeInstanceServices;
import com.hydrokarbs.engine.session.MatchModeConfig;
import com.hydrokarbs.engine.port.SessionView;
import com.hydrokarbs.map.MapDefinition;

/**
 * 声明式规则簿组件:持有规则列表,事件到达时按声明序求值
 * (06-共享组件 §6.1-#6);可选件,不要规则簿的模式不挂。
 *
 * <p>求值链:trigger(事件)→ when(上下文)→ then(上下文)。逐条 try/catch
 * 隔离——把总线隔离下探一层:一个坏规则不会毁掉整局(WARN 后继续下一条)。
 */
public final class RuleBookComponent extends MatchModeComponent {

    private static final Logger LOG = Logger.getLogger(RuleBookComponent.class.getName());

    private final List<Rule> rules;

    /** 装配描述符:声明序 = 求值序。 */
    public static final class Spec extends MatchModeComponent.Specification<RuleBookComponent> {

        private final List<Rule> rules;

        public Spec(List<Rule> rules) {
            super("rulebook");
            if (rules == null || rules.isEmpty()) {
                throw new IllegalArgumentException("规则列表为空:不需要规则的模式不应挂本组件(可选件)");
            }
            this.rules = List.copyOf(rules);
        }

        @Override
        public RuleBookComponent create(String id, MatchModeInstanceServices svc) {
            return new RuleBookComponent(id, svc, rules);
        }
    }

    RuleBookComponent(String id, MatchModeInstanceServices svc, List<Rule> rules) {
        super(id, svc);
        this.rules = rules;
    }

    /** 事件到达(中央总线钩子):按声明序求值,逐条隔离。 */
    @Override
    public void onEvent(MatchModeInstanceEvent event) {
        Objects.requireNonNull(event, "event");
        RuleContext ctx = null;     // 惰性构造:trigger 不匹配的规则不付上下文构造成本
        for (Rule rule : rules) {
            try {
                if (!rule.trigger().test(event)) {
                    continue;
                }
                if (ctx == null) {
                    ctx = new EventContext(event);
                }
                if (rule.when().test(ctx)) {
                    rule.then().accept(ctx);
                }
            } catch (Throwable t) {
                LOG.log(Level.WARNING, "Rule evaluation threw (isolated, continuing): rule " + rule.id()
                        + ", event " + event.getClass().getName(), t);
            }
        }
    }

    /** 服务集背后的只读视图 + 当前事件(规则求值上下文)。 */
    private final class EventContext implements RuleContext {

        private final MatchModeInstanceEvent event;

        EventContext(MatchModeInstanceEvent event) {
            this.event = event;
        }

        @Override
        public MatchModeInstanceEvent event() {
            return event;
        }

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
