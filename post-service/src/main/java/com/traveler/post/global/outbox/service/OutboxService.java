package com.traveler.post.global.outbox.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.traveler.common.core.code.ErrorCode;
import com.traveler.post.global.exception.PostServiceException;
import com.traveler.post.global.outbox.entity.Outbox;
import com.traveler.post.global.outbox.event.OutboxEvent;
import com.traveler.post.global.outbox.mapper.OutboxMapper;
import com.traveler.post.global.outbox.repository.OutboxRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxService {
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final OutboxMapper outboxMapper;
    private final OutboxStatusManager outboxStatusManager;
    private final OutboxRelay outboxRelay;

    // 최대 재시도 횟수.
    // 재시도 간격은 1초부터 두 배씩 증가하며 최대 60초로 제한한다(Outbox.retryDelay).
    // 최대 20회 재시도 시 대기 시간의 합은 약 15분이다.
    static final int MAX_RETRY_COUNT = 20;
    // INIT 상태가 일정 시간 지속되면 재시도 대상으로 판단하는 기준.
    // 프로듀서의 delivery.timeout.ms 기본값인 120초 이내에 비동기 전송 결과(SENT/FAILED)가 도착해야 한다.
    // 여기에 30초의 여유를 더해 150초가 지난 INIT을 미처리 행으로 간주한다.
    static final Duration INIT_RETRY_THRESHOLD = Duration.ofSeconds(150);
    private static final int BATCH_SIZE_RETRY = 100;
    private static final int BATCH_SIZE_DELETE = 500;

    @Transactional(propagation = Propagation.MANDATORY)
    public void createOutbox(OutboxEvent event) {
        String jsonPayload = serializePayload(event.payload());

        Outbox outbox = outboxMapper.toEntity(event, jsonPayload);

        outboxRepository.save(outbox);
    }

    public void publish(OutboxEvent event) {
        String jsonPayload = serializePayload(event.payload());
        outboxRelay.relayAsync(
                event.eventId(), event.topic(), partitionKey(event.aggregateId()), event.eventType(), jsonPayload);
    }

    public void retry() {
        Instant now = Instant.now();
        Instant initThreshold = now.minus(INIT_RETRY_THRESHOLD);
        Pageable pageable = PageRequest.of(0, BATCH_SIZE_RETRY);

        while (true) {
            // 1) 짧은 트랜잭션에서 대상 선점(락) + retryCount 증가 → 커밋되며 락 해제
            List<Outbox> claimed =
                    outboxStatusManager.claimRetryableMessages(initThreshold, MAX_RETRY_COUNT, now, pageable);

            if (claimed.isEmpty()) break;

            log.info("Outbox 재시도 대상 {}건 처리 중", claimed.size());

            // 2) 락 밖에서 카프카 전송 (relaySync 내부에서 SENT/FAILED 갱신)
            for (Outbox outbox : claimed) {
                try {
                    outboxRelay.relaySync(
                            outbox.getEventId(),
                            outbox.getTopic(),
                            partitionKey(outbox.getAggregateId()),
                            outbox.getEventType(),
                            outbox.getPayload(),
                            // claim에서 retryCount를 이미 올렸으므로 한도에 닿았으면 마지막 시도다
                            !outbox.isRetryable(MAX_RETRY_COUNT));
                } catch (Exception e) {
                    log.error("Outbox 재시도 중 오류 발생: id={}", outbox.getId(), e);
                }
            }

            if (claimed.size() < BATCH_SIZE_RETRY) break;
        }
    }

    public void cleanupOldSentMessages(int daysToKeep) {
        Instant threshold = Instant.now().minus(daysToKeep, ChronoUnit.DAYS);
        int totalDeleted = 0;

        while (true) {
            Slice<Long> targetIds = outboxRepository.findSentOutboxIds(threshold, PageRequest.of(0, BATCH_SIZE_DELETE));

            if (targetIds.isEmpty()) break;

            outboxStatusManager.deleteBatch(targetIds.getContent());

            totalDeleted += targetIds.getNumberOfElements();
        }
        log.info("[OutboxCleanup] 총 {}건의 완료된 메시지 정리 완료", totalDeleted);
    }

    // aggregateId가 같은 이벤트가 같은 파티션에 들어가도록 키로 쓴다. 댓글·좋아요는 게시글 ID를 넘긴다
    private static String partitionKey(Long aggregateId) {
        return aggregateId == null ? null : aggregateId.toString();
    }

    private String serializePayload(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("Outbox Payload 직렬화 실패: type={}", payload.getClass().getSimpleName(), e);
            throw new PostServiceException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}
