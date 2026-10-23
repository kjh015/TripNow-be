package com.traveler.web.domain.search.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.traveler.web.domain.search.service.RankingService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RankingSseMetricsTest {

    @Mock
    private RankingService rankingService;

    @InjectMocks
    private RankingSseMetrics rankingSseMetrics;

    @Test
    @DisplayName("스크랩 시점의 SSE 연결 수를 보여 준다")
    void reportsActiveEmitterCount() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        rankingSseMetrics.bindTo(registry);
        given(rankingService.getActiveEmitterCount()).willReturn(5);

        assertThat(registry.get("sse.emitters.active").gauge().value()).isEqualTo(5.0);
    }
}
