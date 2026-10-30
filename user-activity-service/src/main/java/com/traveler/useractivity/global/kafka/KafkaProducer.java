package com.traveler.useractivity.global.kafka;

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

    private final KafkaTemplate<String, String> kafkaTemplate;

    // 파이프라인용 (헤더 없이 Payload만 전송)
    public CompletableFuture<SendResult<String, String>> send(String topic, String payload) {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, payload);
        return executeSend(record);
    }

    // 외부 서비스 이벤트용 (헤더에 eventType, eventId 포함하여 전송)
    public CompletableFuture<SendResult<String, String>> send(
            String topic, String payload, String eventType, String eventId) {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, payload);
        record.headers().add("event-type", eventType.getBytes(StandardCharsets.UTF_8));
        record.headers().add("event-id", eventId.getBytes(StandardCharsets.UTF_8));
        return executeSend(record);
    }

    private CompletableFuture<SendResult<String, String>> executeSend(ProducerRecord<String, String> record) {
        return kafkaTemplate.send(record).whenComplete((result, ex) -> {
            String topic = record.topic();

            if (ex == null) {
                // 성공 시: 어디에(topic, partition) 몇 번째(offset)로 저장되었는지 디버그 로깅
                log.debug(
                        "Kafka 메시지 전송 성공: topic=[{}], partition=[{}], offset=[{}]",
                        topic,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            } else {
                // 실패 시: 에러 스택트레이스와 함께 명확하게 로깅
                log.error("Kafka 메시지 전송 실패: topic=[{}]", topic, ex);
            }
        });
    }
}
