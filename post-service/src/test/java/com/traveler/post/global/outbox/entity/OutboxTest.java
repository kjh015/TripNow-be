package com.traveler.post.global.outbox.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.traveler.post.global.outbox.enums.OutboxStatus;
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
}
