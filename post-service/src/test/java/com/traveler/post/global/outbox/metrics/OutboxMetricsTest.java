package com.traveler.post.global.outbox.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.traveler.post.global.outbox.enums.OutboxStatus;
import com.traveler.post.global.outbox.repository.OutboxRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboxMetricsTest {

    @Mock
    private OutboxRepository outboxRepository;

    @InjectMocks
    private OutboxMetrics outboxMetrics;

    @Test
    @DisplayName("발행 대기(INIT)·실패(FAILED) 건수를 status 태그로 나눠 스크랩 시점의 DB 값으로 보여 준다")
    void reportsPendingCountsByStatus() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        outboxMetrics.bindTo(registry);
        given(outboxRepository.countByStatus(OutboxStatus.INIT)).willReturn(3L);
        given(outboxRepository.countByStatus(OutboxStatus.FAILED)).willReturn(1L);

        assertThat(registry.get("outbox.messages").tag("status", "INIT").gauge().value())
                .isEqualTo(3.0);
        assertThat(registry.get("outbox.messages")
                        .tag("status", "FAILED")
                        .gauge()
                        .value())
                .isEqualTo(1.0);
        assertThat(registry.find("outbox.messages").tag("status", "SENT").gauge())
                .isNull();
    }
}
