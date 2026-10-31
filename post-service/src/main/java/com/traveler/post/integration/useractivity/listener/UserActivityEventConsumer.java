package com.traveler.post.integration.useractivity.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.traveler.post.domain.post.repository.PostStatsSyncRepository;
import com.traveler.post.integration.useractivity.dto.message.UserActivityMessage;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * user-activity-service 이벤트 소비. 지금은 게시글 조회 이벤트(user-activity-service.sink.events)만 받는다.
 *
 * <p>중복 판단은 event-id 헤더로 한다(PostViewedSinkHandler). user-activity-service는 메시지 키를 보내지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserActivityEventConsumer {
    private final PostStatsSyncRepository postStatsSyncRepository;
    private final ObjectMapper objectMapper;

    private static final Duration IDEMPOTENCY_TTL = Duration.ofMinutes(10);

    @SneakyThrows
    @KafkaListener(topics = "${app.kafka.topics.sink-events}", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(
            @Payload String payload,
            @Header(value = "event-type", required = false) String eventType,
            @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key,
            @Header(value = "event-id", required = false) String eventId,
            Acknowledgment ack) {

        log.debug("UserActivity Kafka Consumer: Type=[{}], key=[{}], eventId=[{}]", eventType, key, eventId);

        if (eventType == null) {
            log.error("Missing 'event-type' header. key={} eventId={}", key, eventId);
            ack.acknowledge();
            return;
        }

        switch (eventType) {
            case "POST_VIEWED" -> {
                UserActivityMessage.PostViewedDTO dto =
                        objectMapper.readValue(payload, UserActivityMessage.PostViewedDTO.class);
                incrementViewCount(dto, eventId);
            }
            default -> log.warn("지원하지 않는 사용자 활동 이벤트 타입입니다: {}", eventType);
        }
        ack.acknowledge();
    }

    private void incrementViewCount(UserActivityMessage.PostViewedDTO dto, String eventId) {
        if (eventId == null) {
            log.error("Missing 'event-id' header. postId={}", dto.postId());
            return;
        }

        // 멱등성 체크와 조회수 증가
        boolean isFirst = postStatsSyncRepository.incrementViewCountIfFirst(eventId, dto.postId(), IDEMPOTENCY_TTL);

        if (!isFirst) {
            log.debug("중복된 조회수 이벤트로 무시되었습니다. eventId: {}", eventId);
        }

        log.debug("조회수 증가 메시지 Redis 추가 완료 eventId: {}", eventId);
    }
}
