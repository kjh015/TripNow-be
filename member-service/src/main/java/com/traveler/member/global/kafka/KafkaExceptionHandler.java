package com.traveler.member.global.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.stereotype.Component;

/** 재시도가 끝난 메시지를 DLT로 보내기 전에 남기는 로그 */
@Component
@Slf4j
public class KafkaExceptionHandler {
    private static final String EVENT_ID_HEADER = "event-id";

    public void handle(Exception exception, ConsumerRecord<?, ?> record) {
        Throwable cause = NestedExceptionUtils.getMostSpecificCause(exception);

        // Jackson 예외 메시지(getMessage)와 스택에는 원문 일부가 들어가므로 위치 정보를 뺀 원래 메시지만 남긴다
        if (cause instanceof JsonProcessingException jsonEx) {
            log.error(
                    "[Final Fail - Message Conversion Error] 데이터 형식이 올바르지 않습니다. {}, Error: {}",
                    describe(record),
                    jsonEx.getOriginalMessage());
            return;
        }
        log.error("[Final Fail] {}, Error: {}", describe(record), cause.getMessage(), cause);
    }

    public void logRetry(ConsumerRecord<?, ?> record, Exception ex, int deliveryAttempt) {
        log.warn("Kafka Retry Attempt: {}, {}, Error: {}", deliveryAttempt, describe(record), ex.getMessage());
    }

    /** 메시지 식별 정보: topic-partition@offset, key, event-id 헤더 (payload는 남기지 않는다) */
    static String describe(ConsumerRecord<?, ?> record) {
        Header eventId = record.headers().lastHeader(EVENT_ID_HEADER);
        return String.format(
                "record=%s-%d@%d key=%s eventId=%s",
                record.topic(),
                record.partition(),
                record.offset(),
                record.key(),
                eventId != null && eventId.value() != null ? new String(eventId.value(), StandardCharsets.UTF_8) : "-");
    }
}
