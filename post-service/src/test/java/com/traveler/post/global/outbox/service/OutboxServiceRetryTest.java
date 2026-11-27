package com.traveler.post.global.outbox.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.traveler.post.global.outbox.mapper.OutboxMapper;
import com.traveler.post.global.outbox.repository.OutboxRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OutboxServiceRetryTest {

    private final OutboxStatusManager outboxStatusManager = mock(OutboxStatusManager.class);

    private final OutboxService outboxService = new OutboxService(
            mock(OutboxRepository.class),
            new ObjectMapper(),
            mock(OutboxMapper.class),
            outboxStatusManager,
            mock(OutboxRelay.class));

    @Test
    @DisplayName("재시도 대상은 생성 150초가 지난 INIT, 재시도 시각이 지난 FAILED, 20회 한도로 고른다")
    void retryClaimsWithThresholds() {
        given(outboxStatusManager.claimRetryableMessages(any(), anyInt(), any(), any()))
                .willReturn(List.of());
        Instant before = Instant.now();

        outboxService.retry();

        ArgumentCaptor<Instant> initThreshold = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> now = ArgumentCaptor.forClass(Instant.class);
        verify(outboxStatusManager).claimRetryableMessages(initThreshold.capture(), eq(20), now.capture(), any());
        assertThat(now.getValue()).isBetween(before, Instant.now());
        assertThat(Duration.between(initThreshold.getValue(), now.getValue())).isEqualTo(Duration.ofSeconds(150));
    }
}
