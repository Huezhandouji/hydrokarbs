package com.hydrokarbs.util;

import java.util.logging.Logger;

/**
 * 启动横幅:HYDROKARBS 字符画(纯静态展示,无状态)。
 *
 * <p>调用契约:仅在 {@code HydrokarbsAssembly.assemble} 成功返回后输出——
 * 装配失败自 assemble 上抛,横幅不出现,即「看到横幅 = 插件启动成功」。
 *
 * <p>输出走插件 {@link Logger} 而非 {@code System.out}——保留日志前缀与
 * 日志管道(面板/过滤器可见);行尾空白剥除,行首空格保留(字形对齐依赖)。
 */
public final class StartupBanner {

    /** 字符画*/
    private static final String[] ART = {
            "==========================================================================",
            "█  #   #  #   #  ####   #####  #####  #   #   ###   #####  ####    ####  █",
            "█  #   #   # #   #   #  #   #  #   #  #  #   #   #  #   #  #   #  #      █",
            "█  #####    #    #   #  ###    #   #  ###    #####  ####   ####    ###   █",
            "█  #   #    #    #   #  #  #   #   #  #  #   #   #  #  #   #   #      #  █",
            "█  #   #    #    ####   #   #  #####  #   #  #   #  #   #  ####   ####   █",
            "================ M I N I G A M E  F R A M E W O R K & L I B =============="
    };

    private StartupBanner() {
    }

    /** 逐行输出横幅(INFO 级;onEnable 成功路径调用一次)。 */
    public static void print(Logger logger) {
        for (String line : ART) {
            logger.info(line.stripTrailing());
        }
    }
}
