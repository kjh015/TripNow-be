package com.traveler.post.global.outbox.service;

import com.traveler.post.global.kafka.KafkaProducer;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxRelay {
    private final KafkaProducer kafkaProducer;
    private final OutboxStatusManager outboxStatusManager;

    /** 비동기 전달 */
    public void relayAsync(String eventId, String topic, String key, String eventType, String payload) {
        kafkaProducer.send(topic, key, eventId, eventType, payload).whenComplete((result, ex) -> {
            try {
                if (ex == null) {
                    outboxStatusManager.updateToSent(eventId);
                } else {
                    outboxStatusManager.updateToFailed(eventId);
                    log.error("Outbox 비동기 전달 실패 [ID: {}] 원인: {}", eventId, ex.getMessage());
                }
            } catch (Exception statusEx) {
                log.error("Outbox 상태 갱신 실패 [ID: {}]", eventId, statusEx);
            }
        });
    }

    /** 동기 전달: 재시도 로직에서 사용 */
    public void relaySync(String eventId, String topic, String key, String eventType, String payload) {
        try {
            kafkaProducer.send(topic, key, eventId, eventType, payload).get(5, TimeUnit.SECONDS);
            outboxStatusManager.updateToSent(eventId);
        } catch (Exception e) {
            outboxStatusManager.updateToFailed(eventId);
            log.error("Outbox 동기 전달 실패 [ID: {}] 원인: {}", eventId, e.getMessage());
        }
    }
}
