package com.traveler.web.domain.member.cache;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MemberNicknameCacheTest {

    private final MemberNicknameCache memberNicknameCache = new MemberNicknameCache();

    @Test
    @DisplayName("적중·실패 횟수를 cache.gets 지표로 노출한다")
    void bindsHitMissMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        memberNicknameCache.bindTo(registry);
        memberNicknameCache.put(1L, "여행자", memberNicknameCache.version());

        memberNicknameCache.get(1L);
        memberNicknameCache.getAll(List.of(1L, 2L));

        assertThat(gets(registry, "hit")).isEqualTo(2);
        assertThat(gets(registry, "miss")).isEqualTo(1);
    }

    @Test
    @DisplayName("memberId가 null이면 캐시에 없는 것으로 보고 넣지도 않는다")
    void nullMemberId_ignored() {
        memberNicknameCache.put(null, "여행자", memberNicknameCache.version());

        assertThat(memberNicknameCache.get(null)).isNull();
        assertThat(memberNicknameCache.getAll(Arrays.asList(1L, null))).isEmpty();
    }

    @Test
    @DisplayName("evict로 지운 회원은 캐시에서 빠진다")
    void evict_removesEntry() {
        memberNicknameCache.putAll(Map.of(1L, "여행자", 2L, "산책가"), memberNicknameCache.version());

        memberNicknameCache.evict(1L);

        assertThat(memberNicknameCache.getAll(List.of(1L, 2L))).containsExactly(Map.entry(2L, "산책가"));
    }

    @Test
    @DisplayName("조회를 시작한 뒤 삭제가 있었으면 그 조회 결과는 저장하지 않는다")
    void put_afterEviction_skipped() {
        long versionBeforeFetch = memberNicknameCache.version();
        memberNicknameCache.evict(1L); // 조회 중에 닉네임이 바뀌어 지워짐

        memberNicknameCache.put(1L, "수정 전 닉네임", versionBeforeFetch);
        memberNicknameCache.putAll(Map.of(2L, "산책가"), versionBeforeFetch);

        assertThat(memberNicknameCache.getAll(List.of(1L, 2L))).isEmpty();
        // 삭제 뒤에 시작한 조회는 저장된다
        memberNicknameCache.put(1L, "수정 후 닉네임", memberNicknameCache.version());
        assertThat(memberNicknameCache.get(1L)).isEqualTo("수정 후 닉네임");
    }

    @Test
    @DisplayName("저장 후 60초가 지나면 만료되고, 중간에 읽어도 만료 시각이 늘어나지 않는다")
    void expiresAfterWrite() {
        AtomicLong nanos = new AtomicLong();
        MemberNicknameCache cache = new MemberNicknameCache(nanos::get);
        cache.put(1L, "여행자", cache.version());

        nanos.addAndGet(Duration.ofSeconds(59).toNanos());
        assertThat(cache.get(1L)).isEqualTo("여행자");

        nanos.addAndGet(Duration.ofSeconds(2).toNanos());
        assertThat(cache.get(1L)).isNull();
    }

    private static double gets(SimpleMeterRegistry registry, String result) {
        return registry.get("cache.gets")
                .tag("cache", MemberNicknameCache.CACHE_NAME)
                .tag("result", result)
                .functionCounter()
                .count();
    }
}
