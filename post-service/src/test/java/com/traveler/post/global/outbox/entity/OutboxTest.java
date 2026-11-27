package com.traveler.post.global.outbox.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.traveler.post.global.outbox.enums.OutboxStatus;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OutboxTest {

    @Test
    @DisplayName("발행 대기 중 전송이 실패하면 FAILED가 된다")
    void failed_fromInit() {
        Outbox outbox = Outbox.builder().eventId("event-1").build();

        outbox.failed();

        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.FAILED);
    }

    @Test
    @DisplayName("이미 SENT인 행은 늦게 도착한 실패 결과로 FAILED가 되지 않는다")
    void failed_afterSent_keepsSent() {
        Outbox outbox = Outbox.builder().eventId("event-1").build();
        outbox.sent();

        outbox.failed();

        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(outbox.getSentAt()).isNotNull();
    }

    @Test
    @DisplayName("재시도 대기는 1초부터 두 배씩 늘고 60초에서 멈춘다")
    void retryDelayDoublesUpTo60Seconds() {
        assertThat(Outbox.retryDelay(0)).isEqualTo(Duration.ofSeconds(1));
        assertThat(Outbox.retryDelay(1)).isEqualTo(Duration.ofSeconds(2));
        assertThat(Outbox.retryDelay(2)).isEqualTo(Duration.ofSeconds(4));
        assertThat(Outbox.retryDelay(5)).isEqualTo(Duration.ofSeconds(32));
        assertThat(Outbox.retryDelay(6)).isEqualTo(Duration.ofSeconds(60));
        assertThat(Outbox.retryDelay(19)).isEqualTo(Duration.ofSeconds(60));
        assertThat(Outbox.retryDelay(Integer.MAX_VALUE)).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    @DisplayName("실패하면 재시도 횟수에 맞춘 대기 뒤로 nextRetryAt을 정한다")
    void failedSetsNextRetryAt() {
        Outbox first = Outbox.builder().eventId("event-1").build();
        Instant before = Instant.now();
        first.failed();
        assertThat(first.getNextRetryAt())
                .isBetween(before.plusSeconds(1), Instant.now().plusSeconds(1));

        Outbox third = Outbox.builder().eventId("event-2").retryCount(2).build();
        Instant before3 = Instant.now();
        third.failed();
        assertThat(third.getNextRetryAt())
                .isBetween(before3.plusSeconds(4), Instant.now().plusSeconds(4));
    }

    @Test
    @DisplayName("이미 SENT인 행은 늦은 실패 결과로 nextRetryAt도 바뀌지 않는다")
    void failedAfterSentKeepsNextRetryAt() {
        Outbox outbox = Outbox.builder().eventId("event-1").build();
        outbox.sent();

        outbox.failed();

        assertThat(outbox.getNextRetryAt()).isNull();
    }
}
