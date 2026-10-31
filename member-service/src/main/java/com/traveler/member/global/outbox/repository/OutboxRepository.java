package com.traveler.member.global.outbox.repository;

import com.traveler.member.global.outbox.entity.Outbox;
import com.traveler.member.global.outbox.enums.OutboxStatus;
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

    // 1. INIT 상태로 생성된지 5분이 지났거나, 2. FAILED 상태인 데이터를 조회
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2")})
    @Query("SELECT o FROM Outbox o WHERE "
            + "(o.status = 'INIT' AND o.createdAt < :threshold) OR "
            + "(o.status = 'FAILED' AND o.retryCount < :maxRetry)")
    Slice<Outbox> findRetryableMessages(Instant threshold, int maxRetry, Pageable pageable);

    @Query("SELECT o.id FROM Outbox o WHERE o.status = 'SENT' AND o.sentAt < :threshold")
    Slice<Long> findSentOutboxIds(Instant threshold, Pageable pageable);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM Outbox o WHERE o.id IN :ids")
    void deleteByIdsIn(List<Long> ids);
}
