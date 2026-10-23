package com.traveler.web.domain.search.metrics;

import com.traveler.web.domain.search.service.RankingService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 실시간 랭킹 SSE에 연결된 클라이언트 수 지표 (sse_emitters_active).
 * 연결이 많을 때 sseExecutor(executor_* 지표)가 브로드캐스트를 감당하는지 함께 본다.
 */
@Component
@RequiredArgsConstructor
public class RankingSseMetrics implements MeterBinder {

    private final RankingService rankingService;

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("sse.emitters.active", rankingService, RankingService::getActiveEmitterCount)
                .description("연결된 실시간 랭킹 SSE 클라이언트 수")
                .strongReference(true)
                .register(registry);
    }
}
