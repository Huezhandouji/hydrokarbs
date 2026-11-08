package com.hydrokarbs.engine.session;

/**
 * 会话状态机的四态(§3.1)。封闭枚举:新增值须过设计评审——它会进事件载荷与日志,
 * 是事实上的对外词汇。
 *
 * <p>合法迁移(§3.1 状态机图、§3.2 阶段 B;迁移写点收敛在 Session 的包级方法):
 * RUNNING→ENDING(组件经 end() 端口请求,置位不立即切);RUNNING/ENDING→SWITCHING
 * (外部 switchMode 就绪后挂起,或节拍器在结束后下一 tick 驱动);SWITCHING→RUNNING
 * (B 段 attach 成功,失败走三级回退);RUNNING/ENDING/SWITCHING→DISSOLVING(空场
 * 超时/管理命令/onDisable)。DISSOLVING 是终态无出边;ENDING 与 SWITCHING 是显式
 * 中间态(清场与切换由节拍器下一 tick 驱动,切换不发生在事件派发栈内)。
 */
public enum SessionState {

    /** 常态:当前实例 tick,成员自由进出。 */
    RUNNING,
    /** 结束已请求、未切:等节拍器下一 tick 驱动(显式中间态)。 */
    ENDING,
    /** 切换执行中:旧实例 close → 名册原地位移 → 新实例 attach(主线程单 tick 原子)。 */
    SWITCHING,
    /** 解散中(终态):清场与摘除完成后会话终结。 */
    DISSOLVING;

    /** 是否可从本态迁往目标态(非法迁移由守卫拒绝;自迁移一律非法)。 */
    public boolean canTransitionTo(SessionState target) {
        return switch (this) {
            case RUNNING -> target == ENDING || target == SWITCHING || target == DISSOLVING;
            case ENDING -> target == SWITCHING || target == DISSOLVING;
            case SWITCHING -> target == RUNNING || target == DISSOLVING;
            case DISSOLVING -> false;
        };
    }
}
