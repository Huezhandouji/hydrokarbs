package com.hydrokarbs.engine.instance;

/**
 * 实例时钟:存活 tick 计数与事件时间戳的唯一来源(§12.1/§12.2)。
 *
 * <p>事件时间戳不在事件里各自取系统时钟——发布方一律经
 * {@link #occurredAtMillis()} 取值。由节拍器在主线程驱动,无需并发防护。
 */
public final class MatchModeInstanceClock {

    private int ticks;
    private volatile long lastMillis = System.currentTimeMillis();

    /** 推进一拍:返回推进后的存活 tick 数,并刷新时间戳。 */
    public int tick() {
        lastMillis = System.currentTimeMillis();
        return ++ticks;
    }

    /** 当前存活 tick 数(只读,不推进)。 */
    public int currentTick() {
        return ticks;
    }

    /** 最近一次推进时刻的毫秒时间戳(事件时间戳的唯一来源)。 */
    public long occurredAtMillis() {
        return lastMillis;
    }
}
