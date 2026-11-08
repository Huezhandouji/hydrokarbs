package com.hydrokarbs.engine.session;

import java.util.Locale;

/**
 * 会话 id(值对象;§12.1 {@code engine/session/} 值类型)。
 *
 * <p>取值约定:id 全由创建方(宿主)传入,引擎不写死任何语义;
 * null/空白构造即抛 {@link IllegalArgumentException}(响亮失败)。
 */
public record SessionId(String value) {

    public SessionId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("SessionId 的 value 不得为 null 或空白: " + value);
        }
    }

    /**
     * 短码:小写、仅 {@code [a-z0-9_-]} 的安全文件名形式——世界副本目录名
     * {@code mapId + "-" + 短码}(§3.7)。
     *
     * <p>规则:整体小写化;不安全字符逐个替换为 {@code '-'}(保位替换,
     * 避免不同 id 剔除后撞名)。
     */
    public String shortCode() {
        String lowered = value.toLowerCase(Locale.ROOT);
        StringBuilder code = new StringBuilder(lowered.length());
        for (char c : lowered.toCharArray()) {
            code.append(isSafe(c) ? c : '-');
        }
        return code.toString();
    }

    private static boolean isSafe(char c) {
        return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-';
    }
}
