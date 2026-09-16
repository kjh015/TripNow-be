package com.traveler.useractivity.global.cache.constant;

public final class CacheConstants {

    private CacheConstants() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static final String LOG_PROCESS_BY_CODE = "logProcessByCodeCache";
    public static final String ACTIVE_FORMAT_RULES = "activeFormatRulesCache";
    public static final String ACTIVE_FILTER_RULES = "activeFilterRulesCache";
    public static final String ACTIVE_DEDUP_RULES = "activeDedupRulesCache";
}
