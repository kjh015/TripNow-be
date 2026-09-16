package com.traveler.useractivity.domain.rule.process.dto.event;

import com.traveler.useractivity.global.cache.constant.CacheConstants;
import com.traveler.useractivity.global.cache.event.CacheEvictEvent;

public final class LogProcessEvent {

    private LogProcessEvent() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    // 코드 캐시는 이름이 키라서 수정 전 이름을 알아야 지울 수 있으므로, 프로세스 수가 적은 점을 이용해 통째로 비움
    public record EvictByCode() implements CacheEvictEvent {

        @Override
        public String cacheName() {
            return CacheConstants.LOG_PROCESS_BY_CODE;
        }

        @Override
        public Object cacheKey() {
            return null;
        }

        @Override
        public boolean allEntries() {
            return true;
        }
    }
}
