package com.traveler.post.domain.post.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.traveler.post.domain.post.dto.message.PostMessage;
import com.traveler.post.global.s3.S3Service;
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
public class PostCommandConsumer {

    private final S3Service s3Service;
    private final ObjectMapper objectMapper;

    @SneakyThrows
    @KafkaListener(topics = "${app.kafka.topics.post-commands}", groupId = "${spring.kafka.consumer.group-id}")
    public void consumeS3Delete(
            @Payload String payload,
            @Header(value = "event-type", required = false) String eventType,
            @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key,
            @Header(value = "event-id", required = false) String eventId,
            Acknowledgment ack) {

        log.debug("S3 Kafka Consumer [post-commands]: Type=[{}], key=[{}], eventId=[{}]", eventType, key, eventId);

        if (eventType == null) {
            // payload에는 게시글 본문 등이 들어가므로 남기지 않는다
            log.error("Missing 'event-type' header. key={} eventId={}", key, eventId);
            ack.acknowledge();
            return;
        }

        switch (eventType) {
            case "IMAGE_DELETED", "IMAGE_BATCH_DELETED" -> {
                var dto = objectMapper.readValue(payload, PostMessage.ImagesDeleteDTO.class);
                s3Service.deleteFilesByUrls(dto.imageKeys());
            }
            default -> log.warn("지원하지 않는 커맨드 타입입니다: {}", eventType);
        }
        ack.acknowledge();
    }
}
