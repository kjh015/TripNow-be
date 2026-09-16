package com.traveler.useractivity.global.cache.event;

public interface CacheEvictEvent {
    String cacheName();

    Object cacheKey();

    // true면 cacheKey를 무시하고 캐시 전체를 비움
    default boolean allEntries() {
        return false;
    }
}
