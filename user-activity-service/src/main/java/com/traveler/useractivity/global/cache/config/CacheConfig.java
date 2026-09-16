package com.traveler.useractivity.global.cache.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.traveler.useractivity.global.cache.constant.CacheConstants;
import java.util.concurrent.TimeUnit;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();

        cacheManager.registerCustomCache(CacheConstants.LOG_PROCESS_BY_CODE, buildNativeCache(10, 10000));
        cacheManager.registerCustomCache(CacheConstants.ACTIVE_FORMAT_RULES, buildNativeCache(30, 1000));
        cacheManager.registerCustomCache(CacheConstants.ACTIVE_FILTER_RULES, buildNativeCache(30, 1000));
        cacheManager.registerCustomCache(CacheConstants.ACTIVE_DEDUP_RULES, buildNativeCache(5, 5000));

        return cacheManager;
    }

    private Cache<Object, Object> buildNativeCache(int ttlMinutes, int maxSize) {
        return Caffeine.newBuilder()
                .expireAfterWrite(ttlMinutes, TimeUnit.MINUTES)
                .maximumSize(maxSize)
                .build();
    }
}
