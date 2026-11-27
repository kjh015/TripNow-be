package com.traveler.post.global.outbox.entity;

import com.traveler.common.db.entity.BaseEntity;
import com.traveler.post.global.outbox.enums.OutboxStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
// 상태별 건수(OutboxMetrics), 재시도 대상 조회(status), SENT 정리(status + sent_at)가 함께 사용
// 재시도 대상 조회(FAILED + next_retry_at)는 idx_outbox_status_next_retry_at을 사용
@Table(
        indexes = {
            @Index(name = "idx_outbox_status_sent_at", columnList = "status, sent_at"),
            @Index(name = "idx_outbox_status_next_retry_at", columnList = "status, next_retry_at")
        })
public class Outbox extends BaseEntity {

    @Column(unique = true, nullable = false, length = 36)
    private String eventId; // UUID

    private String aggregateType; // 예: "POST"
    private Long aggregateId; // 예: postId
    private String eventType; // 예: "CREATED"
    private String topic;

    @Column(columnDefinition = "TEXT")
    private String payload; // JSON으로 변환된 DTO

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private OutboxStatus status = OutboxStatus.INIT;

    @Builder.Default
    private int retryCount = 0;

    private Instant sentAt;

    // 전송 실패 행을 다시 보낼 시각. 배포 전에 실패한 행은 null이고 바로 재시도 대상이 된다
    private Instant nextRetryAt;

    // 재시도 대기: 1초부터 두 배씩, 최대 60초
    static final Duration FIRST_RETRY_DELAY = Duration.ofSeconds(1);
    static final Duration MAX_RETRY_DELAY = Duration.ofSeconds(60);

    public void sent() {
        this.status = OutboxStatus.SENT;
        this.sentAt = Instant.now();
    }

    // 전송 완료 콜백은 상태 갱신 풀의 대기열을 거쳐 늦게 올 수 있다. 그사이 재시도가 SENT로 바꿨으면
    // 늦은 실패 결과로 되돌리지 않는다(되돌리면 재시도 스케줄러가 한 번 더 발행한다)
    public void failed() {
        if (this.status == OutboxStatus.SENT) {
            return;
        }
        this.status = OutboxStatus.FAILED;
        this.nextRetryAt = Instant.now().plus(retryDelay(this.retryCount));
    }

    /** 재시도 횟수에 따른 대기. min(1초 × 2^retryCount, 60초) */
    static Duration retryDelay(int retryCount) {
        // 2^6초 = 64초 > 60초이므로 6 이상은 최대값. 큰 retryCount에서 시프트가 넘치지 않게 먼저 자른다
        if (retryCount >= 6) {
            return MAX_RETRY_DELAY;
        }
        Duration delay = FIRST_RETRY_DELAY.multipliedBy(1L << Math.max(retryCount, 0));
        return delay.compareTo(MAX_RETRY_DELAY) > 0 ? MAX_RETRY_DELAY : delay;
    }

    public void incrementRetryCount() {
        this.retryCount++;
    }

    public boolean isRetryable(int maxRetryCount) {
        return this.retryCount < maxRetryCount;
    }
}
