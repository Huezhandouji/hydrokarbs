package com.hydrokarbs.engine.component;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.hydrokarbs.engine.event.SessionEvent;
import com.hydrokarbs.engine.port.SessionServices;

/**
 * 会话级组件基类(★v4;§2.5.1;§3.8):<b>每会话一份</b>、跨模式切换存活、随 dissolve 全灭;
 * 由会话装配声明({@code SessionSpec}),模式无权注册/增删(D4)。纪律照抄
 * {@link MatchModeComponent}(§2.4):只许 svc 字段 + 钩子 + Specification、基类不认识子类、
 * svc 构造期注入四条件(注入先于钩子/final 只注一次/未注入即抛/构造点唯一)。
 *
 * <p><b>派发</b>:声明序广播、{@code stop} 逆序且由框架<b>无条件</b>调用(无单例豁免,
 * §2.8-决断一);{@code update} 每 tick 必调,与实例有无无关(§3.8)。<b>故障隔离</b>(D9):
 * update/onEvent 抛 → 隔离该组件(<b>会话不解散</b>);装配期 awake/start 抛 → 会话创建失败
 * ——两种失败语义不可混淆。<b>跨级只读</b>(D5):<b>现取不缓存</b>,不得缓存实例组件引用
 * 越过切换(14.27);跨级干预只走端口(requestSwitch/requestDissolve)。
 * ★ 待裁决(D2 落盘注,ADR-0004):「订阅实例事件」在本版契约骨架无接口落点,已提请上游裁决。
 */
public abstract class SessionComponent {

    private final String id;
    private final SessionServices svc;

    protected SessionComponent(String id, SessionServices services) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Component id cannot be null or empty.");
        }
        if (services == null) {
            throw new NullPointerException("SessionServices");
        }
        this.id = id;
        this.svc = services;
    }

    public final String id() {
        return id;
    }

    /** 服务集访问口(防御检查:未注入即抛,绝不静默 null——四条件之③)。 */
    protected final SessionServices svc() {
        SessionServices services = svc;
        if (services == null) {
            throw new IllegalStateException("SessionServices 未注入(组件 " + id + ")");
        }
        return services;
    }

    // ── 生命周期:会话装配期走向,框架按声明序无条件广播(stop 逆序) ──────

    /** 会话装配期(create 之后、首个实例之前):只解析会话级依赖并缓存引用;幂等;无可见副作用。 */
    public void awake() {
    }

    /** 会话创建完成、首个实例装配之前生效:订阅会话事件、登记显示、初始化会话级数据。 */
    public void start() {
    }

    /** 每 tick 由引擎派发,<b>与「当前是否有实例」无关</b>(切换间隙照常);禁止阻塞、禁止 IO。 */
    public void update() {
    }

    /**
     * 会话事件钩子(中央总线广播;与显式订阅二选一皆可,订阅随 stop 自动回收)。
     * 收到的是 {@link SessionEvent} 根——基类不认识任何具体事件类型;
     * 抛异常按 D9 隔离(本组件停用,会话不解散)。
     */
    public void onEvent(SessionEvent event) {
    }

    /** dissolve(或隔离)时:与 start 严格对称;返回后框架自动回收本组件登记的资源。 */
    public void stop() {
    }

    // ── 装配期描述符(照抄 MatchModeComponent.Specification) ──

    /**
     * 会话级装配描述符:声明「这个组件怎么造」——工厂、依赖类型、供给面(§2.5.1;§3.8)。
     * {@link #freeze()} 产出不可变 {@link Snapshot},此后声明方法拒改——同一 SPEC 常量
     * 跨会话共享互不串改(结构保证)。组件 id 不在描述符上:由会话装配方在 bind 时显式携带。
     */
    public abstract static class Specification<T extends SessionComponent> {

        private final String descriptorLabel;
        private final List<Class<? extends SessionComponent>> requiredTypes = new ArrayList<>();
        private final List<Class<? extends SessionComponent>> optionalTypes = new ArrayList<>();
        private volatile boolean frozen;

        /** 诊断标签(只进装配期异常文案,无行为分支读它)。 */
        protected Specification(String descriptorLabel) {
            this.descriptorLabel = Objects.requireNonNull(descriptorLabel, "descriptorLabel");
        }

        /** 声明必需依赖类型:装配期缺任一则<b>会话创建失败</b>(响亮失败)。同一类型不得既必需又可选。 */
        public final Specification<T> requires(Class<? extends SessionComponent> type) {
            Objects.requireNonNull(type, "type");
            assertMutable();
            if (optionalTypes.contains(type)) {
                throw new IllegalArgumentException(descriptorLabel + ": 类型 " + type.getName() + " 已声明为可选依赖,不得再声明为必需");
            }
            requiredTypes.add(type);
            return this;
        }

        /** 声明可选依赖类型:缺失不报错,组件运行期自查。同一类型不得既必需又可选。 */
        public final Specification<T> requiresOptional(Class<? extends SessionComponent> type) {
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
        public Class<? extends SessionComponent> providedType() {
            Class<?> current = getClass();
            while (current != null && current != Object.class) {
                Type superType = current.getGenericSuperclass();
                if (superType instanceof ParameterizedType parameterized
                        && parameterized.getRawType() == Specification.class) {
                    Type argument = parameterized.getActualTypeArguments()[0];
                    if (argument instanceof Class<?> cls) {
                        return cls.asSubclass(SessionComponent.class);
                    }
                    break;  // 类型变量:推导不出
                }
                current = current.getSuperclass();
            }
            throw new IllegalStateException(
                    "无法从泛型实参推导 providedType(" + getClass().getName() + ");家族描述符请覆写 providedType()");
        }

        /** 冻结:子类自检 → 返回不可变 {@link Snapshot},此后声明方法拒改(共享实例防串改)。 */
        public final Snapshot freeze() {
            frozen = true;
            Factory<T> factory = this::create;   // 经显式类型的局部变量适配快照的通配符工厂
            return new Snapshot(descriptorLabel, factory, providedType(),
                    List.copyOf(requiredTypes), List.copyOf(optionalTypes));
        }

        /** 抽象创建:唯一创建点(会话装配期由注册表调用,id 与 svc 成对交给它)。 */
        public abstract T create(String id, SessionServices svc);

        private void assertMutable() {
            if (frozen) {
                throw new IllegalStateException(descriptorLabel + ": 描述符已冻结(freeze 后拒改)");
            }
        }

        /** 不可变快照:标签 + 工厂 + 供给面 + 依赖声明。注册表只留快照,不持描述符对象的可变状态。 */
        public record Snapshot(String descriptorLabel, Factory<?> factory,
                               Class<? extends SessionComponent> providedType,
                               List<Class<? extends SessionComponent>> requiredTypes,
                               List<Class<? extends SessionComponent>> optionalTypes) {
        }
    }

    /** 组件工厂:注册表把 id 与 svc 成对交给它(构造点唯一)。 */
    @FunctionalInterface
    public interface Factory<T extends SessionComponent> {
        T create(String id, SessionServices svc);
    }
}
