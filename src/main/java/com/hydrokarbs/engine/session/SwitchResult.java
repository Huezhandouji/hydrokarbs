package com.hydrokarbs.engine.session;

/**
 * 模式切换的结果货币(§3.2;一种结果格式贯穿,错误语义统一)。
 *
 * <p><b>拒绝码恰 3 个</b>(★v3.1 冻结契约,沿革见 §0.5.3):{@link Reason#MODE_NOT_FOUND}
 * (id 不在注册表)、{@link Reason#SWITCH_IN_PROGRESS}(切换中重入被拒)、
 * {@link Reason#REJECTED_BY_TARGET_MODE}(其余一切拒绝)。码表刻意收缩:细目是
 * 声明侧检查管线的内部词汇,不是切换契约的公共词汇;诊断信息一律走
 * {@link Rejected#detail()}(人读清单,只进日志与提示,不是可编程契约)。
 *
 * <p><b>{@link Succeeded#cause} 的语义</b>:携带触发本次切换的 {@link EndResult}——
 * 外部调用(含 force 强切)为 {@link EndResult#nowhere()},自动切换(结束请求驱动)
 * 为结束请求原样透传,调用方据此区分「主动切」与「打完自动切」。
 */
public sealed interface SwitchResult {

    /** 切换成功:会话 id + 来源/目标模式 id + 触发原因(§3.2 记录签名)。 */
    record Succeeded(SessionId session, String fromMode, String toMode, EndResult cause)
            implements SwitchResult {
    }

    /** 切换被拒:拒绝码(恰 3 个)+ 人读诊断清单。 */
    record Rejected(Reason code, String detail) implements SwitchResult {
    }

    /** 拒绝码枚举(冻结契约,新增值须过设计评审;论证见本接口 javadoc)。 */
    enum Reason {
        /** 要的模式 id 不在注册表(调用方写错了 id)。 */
        MODE_NOT_FOUND,
        /** 会话正处于切换中,重入被拒(时序冲突,稍后重试)。 */
        SWITCH_IN_PROGRESS,
        /** 其余一切拒绝:目标侧(人数窗/依赖校验/地图适配/世界副本准备)或
         *  切换防护(最短驻留)不满足;detail 携带人读原因清单(★v1.7 起无能力校验)。 */
        REJECTED_BY_TARGET_MODE
    }
}
