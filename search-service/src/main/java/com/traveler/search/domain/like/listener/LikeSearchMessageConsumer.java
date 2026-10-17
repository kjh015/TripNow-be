package com.traveler.search.domain.like.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.traveler.search.domain.like.dto.message.LikeSearchMessage;
import com.traveler.search.domain.like.service.LikeSearchCommandService;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class LikeSearchMessageConsumer {
    private final LikeSearchCommandService likeSearchCommandService;
    private final ObjectMapper objectMapper;

    @SneakyThrows
    @KafkaListener(topics = "${app.kafka.topics.like-events}", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(
            @Payload String payload,
            @Header(value = "event-type", required = false) String eventType,
            @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key,
            @Header(value = "event-id", required = false) String eventId,
            Acknowledgment ack) {
        log.debug("LikeSearch Kafka Consumer: Type=[{}], key=[{}], eventId=[{}]", eventType, key, eventId);

        if (eventType == null) {
            log.error("Missing 'event-type' header. Payload: {}", payload);
            ack.acknowledge();
            return;
        }

        switch (eventType) {
            case "ADDED" -> {
                likeSearchCommandService.addDocument(objectMapper.readValue(payload, LikeSearchMessage.AddedDTO.class));
            }
            case "REMOVED" -> {
                likeSearchCommandService.removeDocument(
                        objectMapper.readValue(payload, LikeSearchMessage.RemovedDTO.class));
            }
            default -> log.warn("지원하지 않는 이벤트 타입입니다: {}", eventType);
        }
        // 모든 처리가 정상일 때만 커밋
        ack.acknowledge();
    }
}
