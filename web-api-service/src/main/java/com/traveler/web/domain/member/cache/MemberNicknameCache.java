package com.traveler.web.domain.member.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
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
 * <p>조회가 삭제보다 먼저 시작해 늦게 끝나면 수정 전 닉네임을 다시 넣을 수 있다. 그래서 조회 전에 {@link #version()}을
 * 읽어 두고, 저장할 때 그사이 삭제가 있었으면 저장을 건너뛴다. 삭제와 저장은 같은 잠금으로 묶어 확인과 저장 사이에 삭제가
 * 끼어들지 못하게 한다. 삭제는 드물어 잠금 경합은 거의 없다.
 *
 * <p>캐시 적중률 관련 지표: {@code cache_gets_total{cache="memberNickname",result="hit"|"miss"}}.
 */
@Component
public class MemberNicknameCache implements MeterBinder {

    static final String CACHE_NAME = "memberNickname";
    private static final Duration EXPIRE_AFTER_WRITE = Duration.ofSeconds(60);
    private static final long MAXIMUM_SIZE = 10_000;

    private final Cache<Long, String> cache;
    // 삭제할 때마다 1씩 오른다. 조회 시작 이후 삭제가 있었는지 확인하는 데 쓴다
    private long evictionVersion;

    public MemberNicknameCache() {
        this(Ticker.systemTicker());
    }

    // 테스트에서 시간을 직접 움직이기 위한 생성자
    MemberNicknameCache(Ticker ticker) {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(EXPIRE_AFTER_WRITE)
                .maximumSize(MAXIMUM_SIZE)
                .ticker(ticker)
                .recordStats()
                .build();
    }

    /** member-service 조회를 시작하기 전에 읽어 두고, 결과를 저장할 때 넘긴다. */
    public synchronized long version() {
        return evictionVersion;
    }

    // Caffeine은 null 키를 받지 않는다. memberId가 null이면 캐시에 없는 것으로 본다
    public String get(Long memberId) {
        return memberId != null ? cache.getIfPresent(memberId) : null;
    }

    /** 캐시에 있는 회원만 담아 돌려준다. */
    public Map<Long, String> getAll(Collection<Long> memberIds) {
        return cache.getAllPresent(memberIds.stream().filter(Objects::nonNull).toList());
    }

    /**
     * member-service에서 받은 실제 닉네임만 넣는다. 장애 시 기본 닉네임은 넣지 않는다.
     * 조회 시작({@code version}) 이후 삭제가 있었으면 수정 전 값일 수 있어 넣지 않는다.
     */
    public synchronized void put(Long memberId, String nickname, long version) {
        if (memberId != null && nickname != null && version == evictionVersion) {
            cache.put(memberId, nickname);
        }
    }

    public synchronized void putAll(Map<Long, String> nicknames, long version) {
        if (version == evictionVersion) {
            cache.putAll(nicknames);
        }
    }

    public synchronized void evict(Long memberId) {
        if (memberId != null) {
            evictionVersion++;
            cache.invalidate(memberId);
        }
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        CaffeineCacheMetrics.monitor(registry, cache, CACHE_NAME);
    }
}
