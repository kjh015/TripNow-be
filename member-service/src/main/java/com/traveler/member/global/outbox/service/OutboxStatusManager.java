package com.traveler.member.global.outbox.service;

import com.traveler.member.global.outbox.entity.Outbox;
import com.traveler.member.global.outbox.repository.OutboxRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxStatusManager {
    private final OutboxRepository outboxRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateToSent(String eventId) {
        outboxRepository
                .findByEventId(eventId)
                .ifPresentOrElse(Outbox::sent, () -> log.warn("Outbox not found for eventId: {}", eventId));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateToFailed(String eventId) {
        outboxRepository
                .findByEventId(eventId)
                .ifPresentOrElse(Outbox::failed, () -> log.warn("Outbox not found for eventId: {}", eventId));
    }

    @Transactional // 짧은 트랜잭션: FOR UPDATE 조회 + retryCount 증가 후 커밋 → 락 즉시 해제
    public List<Outbox> claimRetryableMessages(Instant threshold, int maxRetry, Pageable pageable) {
        Slice<Outbox> slice = outboxRepository.findRetryableMessages(threshold, maxRetry, pageable);
        List<Outbox> content = slice.getContent();
        content.forEach(Outbox::incrementRetryCount); // 같은 트랜잭션 안에서 dirty checking → 커밋 시 UPDATE
        return content;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        outboxRepository.deleteByIdsIn(ids);
    }
}
