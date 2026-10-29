package com.traveler.web.domain.member.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 회원 ID → 닉네임 로컬 캐시. web-api 인스턴스마다 별도로 유지한다.
 *
 * <p>검색·상세 응답에 작성자 닉네임을 붙일 때 member-service를 반복 호출하지 않도록 60초 동안 캐싱한다.
 * web-api를 통한 닉네임 변경·탈퇴·관리자 삭제 시 해당 항목을 즉시 무효화한다. 다만 여러 인스턴스 중
 * 변경 요청을 처리하지 않은 인스턴스에는 변경 사항이 최대 60초 늦게 반영될 수 있다.
 *
 * <p>캐시 적중률 관련 지표: {@code cache_gets_total{cache="memberNickname",result="hit"|"miss"}}.
 */
@Component
public class MemberNicknameCache implements MeterBinder {

    static final String CACHE_NAME = "memberNickname";
    private static final Duration EXPIRE_AFTER_WRITE = Duration.ofSeconds(60);
    private static final long MAXIMUM_SIZE = 10_000;

    private final Cache<Long, String> cache = Caffeine.newBuilder()
            .expireAfterWrite(EXPIRE_AFTER_WRITE)
            .maximumSize(MAXIMUM_SIZE)
            .recordStats()
            .build();

    // Caffeine은 null 키를 받지 않는다. memberId가 null이면 캐시에 없는 것으로 본다
    public String get(Long memberId) {
        return memberId != null ? cache.getIfPresent(memberId) : null;
    }

    /** 캐시에 있는 회원만 담아 돌려준다. */
    public Map<Long, String> getAll(Collection<Long> memberIds) {
        return cache.getAllPresent(memberIds.stream().filter(Objects::nonNull).toList());
    }

    /** member-service에서 받은 실제 닉네임만 넣는다. 장애 시 기본 닉네임은 넣지 않는다. */
    public void put(Long memberId, String nickname) {
        if (memberId != null && nickname != null) {
            cache.put(memberId, nickname);
        }
    }

    public void putAll(Map<Long, String> nicknames) {
        cache.putAll(nicknames);
    }

    public void evict(Long memberId) {
        if (memberId != null) {
            cache.invalidate(memberId);
        }
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        CaffeineCacheMetrics.monitor(registry, cache, CACHE_NAME);
    }
}
