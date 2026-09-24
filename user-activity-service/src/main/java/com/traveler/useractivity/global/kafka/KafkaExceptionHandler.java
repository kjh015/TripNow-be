package com.traveler.useractivity.global.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.traveler.useractivity.global.exception.UserActivityServiceException;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class KafkaExceptionHandler {
    public void handle(Exception exception, ConsumerRecord<?, ?> record) {
        Throwable cause = NestedExceptionUtils.getMostSpecificCause(exception);

        switch (cause) {
            case UserActivityServiceException uase -> {
                if (KafkaErrorClassifier.isClientError(uase)) {
                    handleClientError(uase, record);
                } else {
                    handleServerError(uase, record);
                }
            }
            case JsonProcessingException jsonEx -> handleConversionError(jsonEx, record);
            case IllegalArgumentException iae -> handleIllegalArgument(iae, record);
            default -> handleUnknownError(cause, record);
        }
    }

    // 비즈니스 예외 (Client Error - 4xx)
    private void handleClientError(UserActivityServiceException uase, ConsumerRecord<?, ?> record) {
        log.error(
                "[Final Fail - Client Error] Code: {}, Topic: {}, Msg: {}",
                uase.getCode().getCode(),
                record.topic(),
                uase.getMessage());
    }

    // 비즈니스 예외 (Server Error - 5xx)
    private void handleServerError(UserActivityServiceException uase, ConsumerRecord<?, ?> record) {
        log.error(
                "[Final Fail - Server Error] Topic: {}, Offset: {}, Msg: {}",
                record.topic(),
                record.offset(),
                uase.getMessage());
    }

    private void handleConversionError(Exception ex, ConsumerRecord<?, ?> record) {
        log.error(
                "[Final Fail - Message Conversion Error] 데이터 형식이 올바르지 않습니다. Topic: {}, Error: {}",
                record.topic(),
                ex.getMessage());
    }

    private void handleIllegalArgument(IllegalArgumentException ex, ConsumerRecord<?, ?> record) {
        log.error("[Final Fail - Invalid Message Contract] 토픽: {}, 원인: {}", record.topic(), ex.getMessage());
    }

    // 알 수 없는 예외
    private void handleUnknownError(Throwable t, ConsumerRecord<?, ?> record) {
        log.error("[Final Fail - Unknown Error] Topic: {}, Error: {}", record.topic(), t.getMessage());
    }

    public void logRetry(ConsumerRecord<?, ?> record, Exception ex, int deliveryAttempt) {
        log.info(
                "Kafka Retry Attempt: {}, Topic: {}, Offset: {}, Error: {}",
                deliveryAttempt,
                record.topic(),
                record.offset(),
                ex.getMessage());
    }
}
