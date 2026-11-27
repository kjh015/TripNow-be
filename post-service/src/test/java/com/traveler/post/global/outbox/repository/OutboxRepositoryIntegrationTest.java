package com.traveler.post.global.outbox.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.traveler.post.global.outbox.entity.Outbox;
import com.traveler.post.global.outbox.enums.OutboxStatus;
import com.traveler.post.testsupport.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

/**
 * 재시도 대상 조회 조건(INIT 기준 시각, FAILED의 nextRetryAt·한도)을 운영과 같은 MySQL에서 확인한다.
 *
 * <p>테스트 트랜잭션 안에서 행을 만들고 롤백한다. 커밋하지 않은 행이라 1초마다 도는 재시도 스케줄러가 가져가지 않는다.
 */
@Transactional
class OutboxRepositoryIntegrationTest extends IntegrationTestSupport {

    private static final int MAX_RETRY = 20;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private EntityManager em;

    @Test
    @DisplayName("INIT은 기준 시각 전에 만든 것만, FAILED는 재시도 시각이 지났거나 없고 한도 미만인 것만 고른다")
    void findRetryableMessages() {
        Instant now = Instant.now();
        Instant initThreshold = now.minusSeconds(150);

        Outbox oldInit = save(OutboxStatus.INIT, 0, null, now.minusSeconds(200));
        Outbox freshInit = save(OutboxStatus.INIT, 0, null, now.minusSeconds(100));
        Outbox dueFailed = save(OutboxStatus.FAILED, 3, now.minusSeconds(1), now.minusSeconds(10));
        Outbox waitingFailed = save(OutboxStatus.FAILED, 3, now.plusSeconds(30), now.minusSeconds(10));
        Outbox legacyFailed = save(OutboxStatus.FAILED, 1, null, now.minusSeconds(10));
        Outbox exhaustedFailed = save(OutboxStatus.FAILED, MAX_RETRY, now.minusSeconds(1), now.minusSeconds(10));
        Outbox sent = save(OutboxStatus.SENT, 0, null, now.minusSeconds(500));
        em.flush();
        em.clear();

        Set<Long> mine = Set.of(
                oldInit.getId(),
                freshInit.getId(),
                dueFailed.getId(),
                waitingFailed.getId(),
                legacyFailed.getId(),
                exhaustedFailed.getId(),
                sent.getId());
        List<Long> found =
                outboxRepository.findRetryableMessages(initThreshold, MAX_RETRY, now, PageRequest.of(0, 1000)).stream()
                        .map(Outbox::getId)
                        .filter(mine::contains)
                        .toList();

        // id 순서
        assertThat(found).containsExactly(oldInit.getId(), dueFailed.getId(), legacyFailed.getId());
    }

    private Outbox save(OutboxStatus status, int retryCount, Instant nextRetryAt, Instant createdAt) {
        Outbox outbox = outboxRepository.saveAndFlush(Outbox.builder()
                .eventId(UUID.randomUUID().toString())
                .aggregateType("POST")
                .aggregateId(1L)
                .eventType("CREATED")
                .topic("test-topic")
                .payload("{}")
                .status(status)
                .retryCount(retryCount)
                .build());
        // created_at은 Auditing이 채우므로 직접 바꾼다. next_retry_at도 엔티티 메서드 대신 값으로 정한다
        em.createNativeQuery("UPDATE outbox SET created_at = :createdAt, next_retry_at = :nextRetryAt WHERE id = :id")
                .setParameter("createdAt", createdAt.truncatedTo(ChronoUnit.MICROS))
                .setParameter("nextRetryAt", nextRetryAt == null ? null : nextRetryAt.truncatedTo(ChronoUnit.MICROS))
                .setParameter("id", outbox.getId())
                .executeUpdate();
        return outbox;
    }
}
