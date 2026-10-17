package com.traveler.search.domain.post.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.traveler.search.domain.post.dto.message.PostSearchMessage;
import com.traveler.search.domain.post.service.PostSearchCommandService;
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
public class PostSearchMessageConsumer {
    private final PostSearchCommandService postSearchCommandService;
    private final ObjectMapper objectMapper;

    @SneakyThrows
    @KafkaListener(topics = "${app.kafka.topics.post-events}", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(
            @Payload String payload,
            @Header(value = "event-type", required = false) String eventType,
            @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key,
            @Header(value = "event-id", required = false) String eventId,
            Acknowledgment ack) {
        log.debug("PostSearch Kafka Consumer: Type=[{}], key=[{}], eventId=[{}]", eventType, key, eventId);

        if (eventType == null) {
            log.error("Missing 'event-type' header. Payload: {}", payload);
            ack.acknowledge();
            return;
        }

        switch (eventType) {
            case "CREATED" -> {
                postSearchCommandService.createDocument(
                        objectMapper.readValue(payload, PostSearchMessage.CreatedDTO.class));
            }
            case "UPDATED" -> {
                postSearchCommandService.updateDocument(
                        objectMapper.readValue(payload, PostSearchMessage.UpdatedDTO.class));
            }
            case "DELETED" -> {
                postSearchCommandService.deleteDocument(
                        objectMapper.readValue(payload, PostSearchMessage.DeletedDTO.class));
            }
            case "STAT_UPDATED" -> {
                postSearchCommandService.updateStatistics(
                        objectMapper.readValue(payload, PostSearchMessage.StatUpdatedDTO.class));
            }
            default -> log.warn("지원하지 않는 이벤트 타입입니다: {}", eventType);
        }
        // 모든 처리가 정상일 때만 커밋
        ack.acknowledge();
    }
}
