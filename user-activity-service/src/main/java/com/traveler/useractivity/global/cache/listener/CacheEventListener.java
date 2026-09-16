package com.traveler.useractivity.global.cache.listener;

import com.traveler.useractivity.global.cache.event.CacheEvictEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class CacheEventListener {

    private final CacheManager cacheManager;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleGlobalCacheEvict(CacheEvictEvent event) {
        String cacheName = event.cacheName();
        Object cacheKey = event.cacheKey();

        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            log.warn("존재하지 않는 캐시 무효화 요청을 무시합니다. Cache Name: {}", cacheName);
            return;
        }

        if (event.allEntries()) {
            log.debug("DB 트랜잭션 커밋 확인. 글로벌 리스너가 캐시 전체를 무효화합니다. Cache: {}", cacheName);
            cache.clear();
            return;
        }

        log.debug("DB 트랜잭션 커밋 확인. 글로벌 리스너가 캐시를 동적 무효화합니다. Cache: {}, Key: {}", cacheName, cacheKey);
        cache.evict(cacheKey);
    }
}
