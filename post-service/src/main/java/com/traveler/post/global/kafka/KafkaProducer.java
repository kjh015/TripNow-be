package com.traveler.post.global.kafka;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaProducer {

    public static final String EVENT_TYPE_HEADER = "event-type";
    public static final String EVENT_ID_HEADER = "event-id";

    private final KafkaTemplate<String, String> kafkaTemplate;

    /**
     * @param key 파티션 키. 같은 키의 이벤트는 같은 파티션에 들어가 순서가 유지된다
     * @param eventId 소비 측 멱등 처리용 이벤트 식별자(Outbox eventId)
     */
    public CompletableFuture<SendResult<String, String>> send(
            String topic, String key, String eventId, String eventType, String payload) {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, payload);

        // Header 추가 (문자열을 byte[]로 변환하여 삽입)
        record.headers().add(EVENT_TYPE_HEADER, eventType.getBytes(StandardCharsets.UTF_8));
        record.headers().add(EVENT_ID_HEADER, eventId.getBytes(StandardCharsets.UTF_8));

        return kafkaTemplate.send(record).whenComplete((result, ex) -> {
            if (ex == null) {
                log.info(
                        "Kafka 메시지 전송 성공: topic=[{}], key=[{}], eventId=[{}], offset=[{}]",
                        topic,
                        key,
                        eventId,
                        result.getRecordMetadata().offset());
            } else {
                log.error("Kafka 메시지 전송 실패: topic=[{}], key=[{}], eventId=[{}]", topic, key, eventId, ex);
            }
        });
    }
}
