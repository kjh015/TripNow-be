package com.traveler.post.domain.post.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.traveler.post.domain.post.dto.message.PostMessage;
import com.traveler.post.domain.post.repository.PostViewRepository;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PostViewConsumer {
    private final PostViewRepository postViewRepository;
    private final ObjectMapper objectMapper;

    private static final Duration IDEMPOTENCY_TTL = Duration.ofMinutes(10);

    @SneakyThrows
    @KafkaListener(topics = "${app.kafka.topics.view-events}", groupId = "${spring.kafka.consumer.group-id}")
    public void consumeViewCount(@Payload String payload, Acknowledgment ack) {
        PostMessage.PostViewedDTO dto = objectMapper.readValue(payload, PostMessage.PostViewedDTO.class);
        log.debug("조회수 증가 메시지 수신 traceId: {}", dto.traceId());

        // 멱등성 체크와 조회수 증가
        boolean isFirst = postViewRepository.incrementViewCountIfFirst(dto.traceId(), dto.postId(), IDEMPOTENCY_TTL);

        if (!isFirst) {
            log.debug("중복된 조회수 이벤트로 무시되었습니다. traceId: {}", dto.traceId());
        }

        log.debug("조회수 증가 메시지 Redis 추가 완료 traceId: {}", dto.traceId());

        // 수동 커밋
        ack.acknowledge();
    }
}
