package com.traveler.search.domain.ranking.scheduler;

import com.traveler.search.domain.ranking.dto.response.RankingResponse;
import com.traveler.search.domain.ranking.service.RankingService;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RankingScheduler {
    private final RankingService rankingService;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String RANKING_CHANNEL = "ranking:live";
    private static final String RANKING_CACHE_KEY = "ranking:latest";

    // 이전 집계 결과를 보관 (메모리 캐시)
    private RankingResponse.LiveDTO lastResponse;

    // 실패가 이어지는 동안 첫 실패 시각. null이면 정상 상태
    private Instant failingSince;

    /**
     * 10초마다 인기 순위를 집계하고, 변화가 있을 경우에만 Redis Pub/Sub으로 발행합니다.
     * ES·Redis 장애 시 10초마다 같은 스택이 쌓이지 않도록 상태가 바뀔 때만 WARN/INFO를 남긴다.
     */
    @Scheduled(fixedRate = 10000)
    public void refreshAndPublishRankings() {
        try {
            refreshAndPublish();
            markRecovered();
        } catch (RuntimeException e) {
            markFailed(e);
        }
    }

    private void refreshAndPublish() {
        // 최신 데이터 집계
        RankingResponse.LiveDTO currentResponse = rankingService.fetchLiveRanking();

        // 이전 데이터와 비교
        if (currentResponse.equals(lastResponse)) {
            return;
        }

        // 변화가 있을 때만 Redis에 기록 및 발행
        this.lastResponse = currentResponse;
        redisTemplate.opsForValue().set(RANKING_CACHE_KEY, currentResponse);
        redisTemplate.convertAndSend(RANKING_CHANNEL, currentResponse);
    }

    private void markFailed(RuntimeException e) {
        if (failingSince == null) {
            failingSince = Instant.now();
            log.warn("[Ranking] 랭킹 집계·발행 실패, 복구될 때까지 실패 로그는 DEBUG로 남김", e);
            return;
        }
        log.debug("[Ranking] 랭킹 집계·발행 실패 지속 since={} error={}", failingSince, e.toString());
    }

    private void markRecovered() {
        if (failingSince == null) {
            return;
        }
        long failedSeconds = Duration.between(failingSince, Instant.now()).toSeconds();
        failingSince = null;
        log.info("[Ranking] 랭킹 집계·발행 복구 failedSeconds={}", failedSeconds);
    }
}
