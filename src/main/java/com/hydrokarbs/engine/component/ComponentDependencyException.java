package com.hydrokarbs.engine.component;

/**
 * 装配期依赖校验失败(§2.6)。
 *
 * <p>消息点名「模式 id / 组件 id / 缺的类型」。注册期失败 → 该模式跳过注册并记严重日志;
 * 切换期失败 → 切换被拒(detail 带异常消息),旧实例无损。
 * 运行时异常(非装配期),不要求调用方捕获——属「坏模板」的响亮暴露。
 */
public class ComponentDependencyException extends RuntimeException {

    public ComponentDependencyException(String message) {
        super(message);
    }

    public ComponentDependencyException(String message, Throwable cause) {
        super(message, cause);
    }
}
