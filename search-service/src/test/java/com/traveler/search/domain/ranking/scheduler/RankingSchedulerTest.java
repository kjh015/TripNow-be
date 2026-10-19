package com.traveler.search.domain.ranking.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.traveler.search.domain.ranking.dto.response.RankingResponse;
import com.traveler.search.domain.ranking.service.RankingService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(OutputCaptureExtension.class)
class RankingSchedulerTest {

    private static final String FAILURE_MESSAGE = "랭킹 집계·발행 실패";
    private static final String RECOVERY_MESSAGE = "랭킹 집계·발행 복구";

    private final RankingService rankingService = mock(RankingService.class);

    @SuppressWarnings("unchecked")
    private final RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);

    private final RankingScheduler scheduler = new RankingScheduler(rankingService, redisTemplate);

    @Test
    @DisplayName("집계가 연속으로 실패하면 첫 실패만 WARN으로 남기고, 복구되면 INFO를 한 번 남긴다")
    void logsOnlyOnStateChange(CapturedOutput output) {
        @SuppressWarnings("unchecked")
        ValueOperations<String, Object> valueOperations = mock(ValueOperations.class);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(rankingService.fetchLiveRanking())
                .willThrow(new IllegalStateException("es down"))
                .willThrow(new IllegalStateException("es down"))
                .willThrow(new IllegalStateException("es down"))
                .willReturn(new RankingResponse.LiveDTO(List.of(), List.of(), List.of()));

        scheduler.refreshAndPublishRankings();
        scheduler.refreshAndPublishRankings();
        scheduler.refreshAndPublishRankings();
        scheduler.refreshAndPublishRankings();
        scheduler.refreshAndPublishRankings();

        assertThat(countLines(output, "WARN", FAILURE_MESSAGE)).isEqualTo(1);
        assertThat(countLines(output, "INFO", RECOVERY_MESSAGE)).isEqualTo(1);
        // 스택은 첫 실패 1회만 남는다
        assertThat(countOccurrences(output.getAll(), "java.lang.IllegalStateException: es down"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("정상 실행이 이어지면 실패·복구 로그를 남기지 않는다")
    void noLogWhenHealthy(CapturedOutput output) {
        @SuppressWarnings("unchecked")
        ValueOperations<String, Object> valueOperations = mock(ValueOperations.class);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(rankingService.fetchLiveRanking())
                .willReturn(new RankingResponse.LiveDTO(List.of(), List.of(), List.of()));

        scheduler.refreshAndPublishRankings();
        scheduler.refreshAndPublishRankings();

        assertThat(output.getAll()).doesNotContain(FAILURE_MESSAGE).doesNotContain(RECOVERY_MESSAGE);
    }

    private static long countLines(CapturedOutput output, String level, String message) {
        return output.getAll()
                .lines()
                .filter(line -> line.contains(level) && line.contains(message))
                .count();
    }

    private static int countOccurrences(String text, String token) {
        int count = 0;
        for (int i = text.indexOf(token); i >= 0; i = text.indexOf(token, i + token.length())) {
            count++;
        }
        return count;
    }
}
