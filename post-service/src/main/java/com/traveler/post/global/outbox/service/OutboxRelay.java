package com.traveler.post.global.outbox.service;

import com.traveler.post.global.config.AsyncConfig;
import com.traveler.post.global.kafka.KafkaProducer;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class OutboxRelay {
    private final KafkaProducer kafkaProducer;
    private final OutboxStatusManager outboxStatusManager;
    private final Executor statusExecutor;

    public OutboxRelay(
            KafkaProducer kafkaProducer,
            OutboxStatusManager outboxStatusManager,
            @Qualifier(AsyncConfig.OUTBOX_STATUS_EXECUTOR) Executor statusExecutor) {
        this.kafkaProducer = kafkaProducer;
        this.outboxStatusManager = outboxStatusManager;
        this.statusExecutor = statusExecutor;
    }

    /**
     * 비동기 전달. 실패하면 FAILED로 남고 재시도 스케줄러가 다시 보낸다.
     * 상태 갱신은 프로듀서 전송 스레드가 아닌 statusExecutor에서 해, DB 커밋을 기다리는 동안에도 다음 메시지 전송이 멈추지 않게 한다
     */
    public void relayAsync(String eventId, String topic, String key, String eventType, String payload) {
        kafkaProducer
                .send(topic, key, eventId, eventType, payload)
                .whenCompleteAsync(
                        (result, ex) -> {
                            try {
                                if (ex == null) {
                                    outboxStatusManager.updateToSent(eventId);
                                } else {
                                    outboxStatusManager.updateToFailed(eventId);
                                    log.warn(
                                            "Outbox 비동기 전달 실패, 재시도 예정 topic={} key={} eventId={}",
                                            topic,
                                            key,
                                            eventId,
                                            ex);
                                }
                            } catch (Exception statusEx) {
                                log.error("Outbox 상태 갱신 실패 [ID: {}]", eventId, statusEx);
                            }
                        },
                        statusExecutor);
    }

    /**
     * 동기 전달: 재시도 로직에서 사용
     *
     * @param lastAttempt 재시도 한도의 마지막 시도인지. 마지막 시도가 실패하면 더 이상 재시도하지 않으므로 ERROR로 남긴다
     */
    public void relaySync(
            String eventId, String topic, String key, String eventType, String payload, boolean lastAttempt) {
        try {
            kafkaProducer.send(topic, key, eventId, eventType, payload).get(5, TimeUnit.SECONDS);
            outboxStatusManager.updateToSent(eventId);
        } catch (Exception e) {
            outboxStatusManager.updateToFailed(eventId);
            if (lastAttempt) {
                log.error("Outbox 재시도 한도 소진, 전달 포기 topic={} key={} eventId={}", topic, key, eventId, e);
            } else {
                log.warn("Outbox 재시도 전달 실패 topic={} key={} eventId={}", topic, key, eventId, e);
            }
        }
    }
}
