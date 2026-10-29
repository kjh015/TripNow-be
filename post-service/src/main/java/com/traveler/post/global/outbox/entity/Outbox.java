package com.traveler.post.global.outbox.entity;

import com.traveler.common.db.entity.BaseEntity;
import com.traveler.post.global.outbox.enums.OutboxStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
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
@Table(indexes = {@Index(name = "idx_outbox_status_sent_at", columnList = "status, sent_at")})
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
    }

    public void incrementRetryCount() {
        this.retryCount++;
    }

    public boolean isRetryable(int maxRetryCount) {
        return this.retryCount < maxRetryCount;
    }
}
