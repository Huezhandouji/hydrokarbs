package com.hydrokarbs.engine.component;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.hydrokarbs.engine.event.MatchModeInstanceEvent;
import com.hydrokarbs.engine.port.MatchModeInstanceServices;
import com.hydrokarbs.engine.session.Handoff;
import com.hydrokarbs.engine.session.MatchModeConfig;
import com.hydrokarbs.map.MapDefinition;

/**
 * 模式组件基类:行为单元(§2.4)。只允许出现 svc 字段 + 钩子方法 +
 * {@code getComponent}——「顺手加个 helper」属越界。
 *
 * <p>★「基类不认识子类」:不为任何具体领域设通用视图,要协作就
 * {@code getComponent} 按类型取对方组件的强类型读口。svc 构造期注入四条件:
 * ①注入先于一切钩子 ②{@code final} 只注一次 ③{@link #svc()} 未注入即抛
 * ④构造点唯一(id 与服务集成对产生)。
 *
 * <p>所有玩法状态住在组件里,组件是状态唯一主人并提供强类型读口;
 * 跨组件读写走状态主人的读口,不开放裸写。
 */
public abstract class MatchModeComponent {

    private final String id;
    private final MatchModeInstanceServices svc;

    protected MatchModeComponent(String id, MatchModeInstanceServices services) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Component id cannot be null or empty.");
        }
        if (services == null) {
            throw new NullPointerException("MatchModeInstanceServices");
        }
        this.id = id;
        this.svc = services;
    }

    public final String id() {
        return id;
    }

    /** 服务集访问口(防御检查:未注入即抛,绝不静默 null——四条件之③)。 */
    protected final MatchModeInstanceServices svc() {
        MatchModeInstanceServices services = svc;
        if (services == null) {
            throw new IllegalStateException("MatchModeInstanceServices 未注入(组件 " + id + ")");
        }
        return services;
    }

    /** 按类型取同实例内的另一组件(未注册 → null;要全部符合者用 {@code svc().components().getAll(type)})。 */
    protected final <T extends MatchModeComponent> T getComponent(Class<T> type) {
        return svc().components().get(type);
    }

    // ── 生命周期:框架按声明序无条件广播 ─────────────────────────────

    /** 装配阶段:只解析跨组件依赖并缓存引用。幂等;不得改动任何玩家可见状态。 */
    public void awake() {
    }

    /** 开始生效:初始化数据、传送、发装备、登记显示。玩家可见副作用最早发生在这里。 */
    public void start() {
    }

    /** 每 tick(由节拍器按实例周期驱动)。禁止阻塞、禁止登记不回收的资源。 */
    public void update() {
    }

    /**
     * 实例事件钩子(中央总线;订阅发生在构造/awake 期,随 stop 自动回收)。
     * 收到的是 {@link MatchModeInstanceEvent} 根(开放接口)——基类不认识任何具体事件类型。
     */
    public void onEvent(MatchModeInstanceEvent event) {
    }

    /** 切换出局的交接钩子:向 out 写本组件想带走的载荷(状态尚完整,但不得再改玩家)。 */
    public void onHandoff(Handoff.Builder out) {
    }

    /** 停止生效:与 start 严格对称。返回后框架自动回收本组件登记的资源(订阅/延时任务/显示)。 */
    public void stop() {
    }

    // ── 装配期描述符 ────────────────────────────────────────────

    /**
     * 装配描述符:声明「这个组件怎么造」——工厂、依赖类型、供给面、地图适配(§2.4)。
     *
     * <p>{@link #freeze()} 产出不可变 {@link Snapshot},此后声明方法拒改——
     * 同一描述符实例被两个模式共享互不串改(结构保证,不靠共享者自觉);
     * {@code bindId} 可重复绑(每个装配方各自绑入,id 不进快照)。
     * 子类自检在各自构造器完成(freeze 只做前置检查与快照)。
     */
    public abstract static class Specification<T extends MatchModeComponent> {

        private final String descriptorLabel;
        private final List<Class<? extends MatchModeComponent>> requiredTypes = new ArrayList<>();
        private final List<Class<? extends MatchModeComponent>> optionalTypes = new ArrayList<>();
        private volatile boolean frozen;
        private volatile String boundId;

        /** 诊断标签(只进装配期异常文案,无行为分支读它)。 */
        protected Specification(String descriptorLabel) {
            this.descriptorLabel = Objects.requireNonNull(descriptorLabel, "descriptorLabel");
        }

        /** 声明必需依赖类型:装配期缺任一则该模式注册失败(响亮失败)。同一类型不得既必需又可选。 */
        public final Specification<T> requires(Class<? extends MatchModeComponent> type) {
            Objects.requireNonNull(type, "type");
            assertMutable();
            if (optionalTypes.contains(type)) {
                throw new IllegalArgumentException(descriptorLabel + ": 类型 " + type.getName() + " 已声明为可选依赖,不得再声明为必需");
            }
            requiredTypes.add(type);
            return this;
        }

        /** 声明可选依赖类型:缺失不报错,组件运行期自查。同一类型不得既必需又可选。 */
        public final Specification<T> requiresOptional(Class<? extends MatchModeComponent> type) {
            Objects.requireNonNull(type, "type");
            assertMutable();
            if (requiredTypes.contains(type)) {
                throw new IllegalArgumentException(descriptorLabel + ": 类型 " + type.getName() + " 已声明为必需依赖,不得再声明为可选");
            }
            optionalTypes.add(type);
            return this;
        }

        /**
         * 本组件提供的类型(供给面):默认由泛型实参推导(解析继承链上
         * {@code Specification<T>} 的实际类型参数),家族描述符可覆写为具体类。
         * 推导不出(链上是类型变量)时响亮抛,提示覆写。
         */
        public Class<? extends MatchModeComponent> providedType() {
            Class<?> current = getClass();
            while (current != null && current != Object.class) {
                Type superType = current.getGenericSuperclass();
                if (superType instanceof ParameterizedType parameterized
                        && parameterized.getRawType() == Specification.class) {
                    Type argument = parameterized.getActualTypeArguments()[0];
                    if (argument instanceof Class<?> cls) {
                        return cls.asSubclass(MatchModeComponent.class);
                    }
                    break;  // 类型变量:推导不出
                }
                current = current.getSuperclass();
            }
            throw new IllegalStateException(
                    "无法从泛型实参推导 providedType(" + getClass().getName() + ");家族描述符请覆写 providedType()");
        }

        /** 地图适配声明(默认通过):切换准备阶段对全表调用,任何一条非空 → 切换被拒并带问题清单。 */
        public List<String> checkMap(MapDefinition map, MatchModeConfig cfg) {
            return List.of();
        }

        /**
         * 绑组件 id(模式装配器在 {@code addComponent} 内调用,bindId → freeze 序)。
         * 可重复绑:同一描述符跨模式共享时各装配方各自绑入;id 不进快照、不影响已产出的快照。
         */
        public final void bindId(String id) {
            if (id == null || id.trim().isEmpty()) {
                throw new IllegalArgumentException("Component id cannot be null or empty.");
            }
            this.boundId = id;
        }

        /** 内部读口:最近绑入的组件 id(仅供诊断文案)。 */
        final String boundId() {
            return boundId;
        }

        /**
         * 冻结:id 必须在 freeze 前绑入;产出不可变 {@link Snapshot},
         * 此后声明方法({@code requires}/{@code requiresOptional})拒改。
         */
        public final Snapshot freeze() {
            if (boundId == null) {
                throw new IllegalStateException(descriptorLabel + ": id 必须在 freeze 前经 bindId 绑入");
            }
            frozen = true;
            Factory<T> factory = this::create;   // 经显式类型的局部变量适配快照的通配符工厂
            return new Snapshot(descriptorLabel, factory, providedType(),
                    List.copyOf(requiredTypes), List.copyOf(optionalTypes));
        }

        /** 抽象创建:由具体描述符决定造哪个组件类。容器把 id 与 svc 成对交给它。 */
        public abstract T create(String id, MatchModeInstanceServices svc);

        private void assertMutable() {
            if (frozen) {
                throw new IllegalStateException(descriptorLabel + ": 描述符已冻结(freeze 后拒改)");
            }
        }

        /** 不可变快照:标签 + 工厂 + 供给面 + 依赖声明。装配表只留快照,不持描述符对象的可变状态。 */
        public record Snapshot(String descriptorLabel, Factory<?> factory,
                               Class<? extends MatchModeComponent> providedType,
                               List<Class<? extends MatchModeComponent>> requiredTypes,
                               List<Class<? extends MatchModeComponent>> optionalTypes) {
        }
    }

    /** 组件工厂:容器把 id 与 svc 成对交给它(构造点唯一)。 */
    @FunctionalInterface
    public interface Factory<T extends MatchModeComponent> {
        T create(String id, MatchModeInstanceServices svc);
    }
}
