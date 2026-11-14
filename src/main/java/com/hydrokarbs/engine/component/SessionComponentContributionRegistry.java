package com.hydrokarbs.engine.component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.hydrokarbs.engine.event.SessionEventBus;
import com.hydrokarbs.engine.session.SessionId;

/**
 * 会话组件贡献收录表(D14 通道一;★v1.6 唯一调用方 = {@code HydrokarbsApiImpl#registerSessionComponents})。
 *
 * <p><b>职责两件</b>:①收录——干跑合并校验(经 scratch {@link SessionComponentRegistry}
 * 走与真实装配同一套 bind 查重 + 依赖校验)通过才落地;②按提供方身份摘除
 * (插件 disable 兜底,后续会话不再合并;已装配会话不受影响——无运行期拆卸)。
 * 校验失败响亮上抛(显式注册可捕获,拒载信号);<b>失败不留半收录态</b>
 * (先摘旧条目再干跑,失败时新旧均不入表)。会话编排不在此,归 api 层。
 *
 * <p>提供方键 = {@code Object} 身份(生产 = 插件实例,disable 兜底按同一实例摘除);
 * 本类零 Bukkit 依赖。线程契约:收录/摘除/合并全部主线程,普通集合不加锁。
 */
public final class SessionComponentContributionRegistry {

    /** 收录条目:提供方身份键 + 其贡献的描述符列表(注册序保持)。 */
    private record Entry(Object providerKey, List<SessionComponent.Specification<?>> specs) {
    }

    /** 收录表(注册序;摘除按身份扫描——提供方数量小,O(n) 摘除无虞)。 */
    private final List<Entry> accepted = new ArrayList<>();

    /**
     * 收录一个提供方的贡献:先按身份摘除既有条目(重注册幂等)→ 全量干跑合并校验
     * (scratch 注册表:id 查重 + 必需依赖可满足)→ 通过才落地;失败响亮上抛。
     * 干跑经 {@code spec.freeze()} 产快照——与真实装配同款;对被拒贡献的描述符
     * 仅「声明方法拒改」,无行为影响。
     */
    public void register(Object providerKey, List<SessionComponent.Specification<?>> specs) {
        Objects.requireNonNull(providerKey, "providerKey");
        Objects.requireNonNull(specs, "specs");
        for (SessionComponent.Specification<?> spec : specs) {
            Objects.requireNonNull(spec, "spec");
        }
        unregister(providerKey);                    // 重注册幂等:同身份旧条目先摘(合并语义见类注)
        List<SessionComponent.Specification<?>> merged = merged();
        merged.addAll(specs);
        dryRun(merged);                             // 查重(bind 撞 id 抛)+ 依赖校验(缺必需依赖抛)
        accepted.add(new Entry(providerKey, List.copyOf(specs)));
    }

    /**
     * 摘除一个提供方的全部贡献(身份键;幂等——未收录返回 false,静默)。
     * provider 插件 disable → 后续会话不再合并(D14;已装配会话不受影响)。
     */
    public boolean unregister(Object providerKey) {
        Objects.requireNonNull(providerKey, "providerKey");
        return accepted.removeIf(entry -> entry.providerKey() == providerKey);
    }

    /**
     * 已收录贡献的合并视图(注册序拼接;每次调用新拷贝,不外露内部列表)。
     * {@code createSession(SessionRecipe)} 的合并序 = 本表(注册序)→ recipe.extra。
     */
    public List<SessionComponent.Specification<?>> merged() {
        List<SessionComponent.Specification<?>> merged = new ArrayList<>();
        for (Entry entry : accepted) {
            merged.addAll(entry.specs());
        }
        return merged;
    }

    /** 是否无任何收录(诊断与测试断言口)。 */
    public boolean isEmpty() {
        return accepted.isEmpty();
    }

    /**
     * 干跑合并校验:scratch {@link SessionComponentRegistry}(每会话一份的机制件
     * 在此以「不落地的影子会话」复用——装配算法只有一份,不写第二套)逐条
     * {@code bind}(组件 id 查重,同 id 抛 {@code IllegalStateException})+
     * {@code verifyDependencies}(缺必需依赖抛 {@link ComponentDependencyException};
     * 算法逐行复用:自己不算自己的提供者、可选缺失不报错、环不检查)。
     */
    private static void dryRun(List<SessionComponent.Specification<?>> specs) {
        SessionComponentRegistry scratch = new SessionComponentRegistry(
                new SessionId("contribution-dry-run"), new SessionEventBus());
        for (SessionComponent.Specification<?> spec : specs) {
            scratch.bind(deriveComponentId(spec), spec.freeze());
        }
        scratch.verifyDependencies();
    }

    /** 组件 id 推导(供给面类型简单名,镜像 {@code SessionServiceImpl#deriveComponentId}):干跑与真实装配必须同源,否则查重口径漂移;两处任一改动须同步。 */
    private static String deriveComponentId(SessionComponent.Specification<?> spec) {
        return spec.providedType().getSimpleName();
    }
}
