package com.hydrokarbs.engine.mode;

import com.hydrokarbs.engine.AttributeBag;

/**
 * 模式元数据约定键:recordsHistory/minParticipants/maxParticipants 三键的
 * {@link AttributeBag} 词料约定 + 中性缺省读法(02-核心概念 §2.3)。
 *
 * <p><b>中性缺省语义</b>:缺 {@code records-history} = {@code false}(不落史);
 * 缺 {@code min-participants} = 0(空名册可泊);缺 {@code max-participants} =
 * {@code Integer.MAX_VALUE}(上限全开——force 只豁免下限、上限永不豁免,A4/防护③)。
 * 写只发生在模式声明装配期({@link #addTo} 喂 {@link AttributeBag.Builder});
 * 本类零状态,只持约定键与读帮助方法,引擎不认识任何具体模式 id。
 */
public final class MatchModeMetadata {

    /** 是否写对局历史/统计(缺省 = false 不落史)。 */
    public static final AttributeBag.Key<Boolean> RECORDS_HISTORY = AttributeBag.Key.of("records-history", Boolean.class);

    /** 人数窗下限(缺省 = 0)。 */
    public static final AttributeBag.Key<Integer> MIN_PARTICIPANTS = AttributeBag.Key.of("min-participants", Integer.class);

    /** 人数窗上限(缺省 = MAX_VALUE 全开,force 只豁免下限)。 */
    public static final AttributeBag.Key<Integer> MAX_PARTICIPANTS = AttributeBag.Key.of("max-participants", Integer.class);

    /** 下限中性缺省。 */
    public static final int DEFAULT_MIN = 0;

    /** 上限中性缺省(全开)。 */
    public static final int DEFAULT_MAX = Integer.MAX_VALUE;

    private MatchModeMetadata() {
    }

    /** 是否落史(缺键 = false)。 */
    public static boolean recordsHistory(AttributeBag meta) {
        return meta.getOrDefault(RECORDS_HISTORY, false);
    }

    /** 人数窗下限(缺键 = 0)。 */
    public static int minParticipants(AttributeBag meta) {
        return meta.getOrDefault(MIN_PARTICIPANTS, DEFAULT_MIN);
    }

    /** 人数窗上限(缺键 = MAX_VALUE 全开)。 */
    public static int maxParticipants(AttributeBag meta) {
        return meta.getOrDefault(MAX_PARTICIPANTS, DEFAULT_MAX);
    }

    /** 声明装配期三键全量入表(返回入参 b,便于链式拼装)。 */
    public static AttributeBag.Builder addTo(AttributeBag.Builder b, boolean recordsHistory, int min, int max) {
        b.put(RECORDS_HISTORY, recordsHistory);
        b.put(MIN_PARTICIPANTS, min);
        b.put(MAX_PARTICIPANTS, max);
        return b;
    }
}
