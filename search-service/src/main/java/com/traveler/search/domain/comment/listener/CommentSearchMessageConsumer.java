package com.traveler.search.domain.comment.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.traveler.search.domain.comment.dto.message.CommentSearchMessage;
import com.traveler.search.domain.comment.service.CommentSearchCommandService;
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
public class CommentSearchMessageConsumer {
    private final CommentSearchCommandService commentSearchCommandService;
    private final ObjectMapper objectMapper;

    @SneakyThrows
    @KafkaListener(topics = "${app.kafka.topics.comment-events}", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(
            @Payload String payload,
            @Header(value = "event-type", required = false) String eventType,
            @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key,
            @Header(value = "event-id", required = false) String eventId,
            Acknowledgment ack) {
        log.debug("CommentSearch Kafka Consumer: Type=[{}], key=[{}], eventId=[{}]", eventType, key, eventId);

        if (eventType == null) {
            // payload에는 게시글 본문 등이 들어가므로 남기지 않는다
            log.error("Missing 'event-type' header. key={} eventId={}", key, eventId);
            ack.acknowledge();
            return;
        }

        switch (eventType) {
            case "CREATED" -> {
                commentSearchCommandService.createDocument(
                        objectMapper.readValue(payload, CommentSearchMessage.CreatedDTO.class));
            }
            case "UPDATED" -> {
                commentSearchCommandService.updateDocument(
                        objectMapper.readValue(payload, CommentSearchMessage.UpdatedDTO.class));
            }
            case "DELETED" -> {
                commentSearchCommandService.deleteDocument(
                        objectMapper.readValue(payload, CommentSearchMessage.DeletedDTO.class));
            }
            case "BATCH_DELETED" -> {
                commentSearchCommandService.deleteDocuments(
                        objectMapper.readValue(payload, CommentSearchMessage.BatchDeletedDTO.class));
            }
            default -> log.warn("지원하지 않는 이벤트 타입입니다: {}", eventType);
        }
        // 모든 처리가 정상일 때만 커밋
        ack.acknowledge();
    }
}
