package com.traveler.post.global.outbox.repository;

import com.traveler.post.global.outbox.entity.Outbox;
import com.traveler.post.global.outbox.enums.OutboxStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.*;

public interface OutboxRepository extends JpaRepository<Outbox, Long> {
    Optional<Outbox> findByEventId(String eventId);

    // 상태별 건수 (OutboxMetrics 게이지)
    long countByStatus(OutboxStatus status);

    // 재처리 대상:
    // 1. 생성 후 initThreshold가 지난 INIT 상태의 메시지
    // 2. 재시도 횟수가 maxRetry 미만이고 재시도 시각(nextRetryAt)이 지난 FAILED 상태의 메시지
    // nextRetryAt이 null인 FAILED는 배포 이전에 실패한 메시지로 간주해 재시도 대상으로 포함한다.
    // PESSIMISTIC_WRITE와 SKIP LOCKED(-2)를 사용해 다른 서버가 잠근 행은 대기하지 않고 건너뛴다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2")})
    @Query("SELECT o FROM Outbox o WHERE "
            + "(o.status = 'INIT' AND o.createdAt < :initThreshold) OR "
            + "(o.status = 'FAILED' AND o.retryCount < :maxRetry AND (o.nextRetryAt IS NULL OR o.nextRetryAt <= :now)) "
            + "ORDER BY o.id")
    Slice<Outbox> findRetryableMessages(Instant initThreshold, int maxRetry, Instant now, Pageable pageable);

    @Query("SELECT o.id FROM Outbox o WHERE o.status = 'SENT' AND o.sentAt < :threshold")
    Slice<Long> findSentOutboxIds(Instant threshold, Pageable pageable);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM Outbox o WHERE o.id IN :ids")
    void deleteByIdsIn(List<Long> ids);
}
