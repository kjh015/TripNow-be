package com.traveler.post.global.outbox.metrics;

import com.traveler.post.global.outbox.enums.OutboxStatus;
import com.traveler.post.global.outbox.repository.OutboxRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 아직 발행되지 않은 Outbox 건수 지표 (outbox_messages{status="INIT"|"FAILED"}).
 * INIT이 쌓이면 발행이 밀리는 것이고, FAILED가 늘면 Kafka 발행이 실패하고 있는 것이다.
 * 값은 스크랩할 때마다 DB에서 센다. 스크랩하지 않으면 쿼리가 나가지 않는다.
 */
@Component
@RequiredArgsConstructor
public class OutboxMetrics implements MeterBinder {

    private static final List<OutboxStatus> PENDING_STATUSES = List.of(OutboxStatus.INIT, OutboxStatus.FAILED);

    private final OutboxRepository outboxRepository;

    @Override
    public void bindTo(MeterRegistry registry) {
        for (OutboxStatus status : PENDING_STATUSES) {
            Gauge.builder("outbox.messages", outboxRepository, repository -> repository.countByStatus(status))
                    .description("발행 대기·실패 상태의 Outbox 건수")
                    .tag("status", status.name())
                    .strongReference(true)
                    .register(registry);
        }
    }
}
