package com.traveler.web.domain.member.cache;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MemberNicknameCacheTest {

    private final MemberNicknameCache memberNicknameCache = new MemberNicknameCache();

    @Test
    @DisplayName("적중·실패 횟수를 cache.gets 지표로 노출한다")
    void bindsHitMissMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        memberNicknameCache.bindTo(registry);
        memberNicknameCache.put(1L, "여행자");

        memberNicknameCache.get(1L);
        memberNicknameCache.getAll(List.of(1L, 2L));

        assertThat(gets(registry, "hit")).isEqualTo(2);
        assertThat(gets(registry, "miss")).isEqualTo(1);
    }

    @Test
    @DisplayName("memberId가 null이면 캐시에 없는 것으로 보고 넣지도 않는다")
    void nullMemberId_ignored() {
        memberNicknameCache.put(null, "여행자");

        assertThat(memberNicknameCache.get(null)).isNull();
        assertThat(memberNicknameCache.getAll(Arrays.asList(1L, null))).isEmpty();
    }

    @Test
    @DisplayName("evict로 지운 회원은 캐시에서 빠진다")
    void evict_removesEntry() {
        memberNicknameCache.putAll(Map.of(1L, "여행자", 2L, "산책가"));

        memberNicknameCache.evict(1L);

        assertThat(memberNicknameCache.getAll(List.of(1L, 2L))).containsExactly(Map.entry(2L, "산책가"));
    }

    private static double gets(SimpleMeterRegistry registry, String result) {
        return registry.get("cache.gets")
                .tag("cache", MemberNicknameCache.CACHE_NAME)
                .tag("result", result)
                .functionCounter()
                .count();
    }
}
