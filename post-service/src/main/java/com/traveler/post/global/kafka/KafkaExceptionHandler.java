package com.traveler.post.global.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.traveler.post.global.exception.PostServiceException;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Component
@Slf4j
public class KafkaExceptionHandler {
    private static final String EVENT_ID_HEADER = "event-id";

    public void handle(Exception exception, ConsumerRecord<?, ?> record) {
        Throwable cause = NestedExceptionUtils.getMostSpecificCause(exception);

        switch (cause) {
            case PostServiceException pse -> {
                if (isClientError(pse)) {
                    handleClientError(pse, record);
                } else {
                    handleServerError(pse, record);
                }
            }
            case JsonProcessingException jsonEx -> handleConversionError(jsonEx, record);
            case S3Exception s3Ex -> handleInfraError(s3Ex, record);
            case SdkClientException sdkEx -> handleInfraError(sdkEx, record);
            default -> handleUnknownError(cause, record);
        }
    }

    // 비즈니스 예외 (Client Error - 4xx)
    private void handleClientError(PostServiceException pse, ConsumerRecord<?, ?> record) {
        log.error(
                "[Final Fail - Client Error] Code: {}, {}, Msg: {}",
                pse.getCode().getCode(),
                describe(record),
                pse.getMessage(),
                pse);
    }

    // 비즈니스 예외 (Server Error - 5xx)
    private void handleServerError(PostServiceException pse, ConsumerRecord<?, ?> record) {
        log.error("[Final Fail - Server Error] {}, Msg: {}", describe(record), pse.getMessage(), pse);
    }

    // Jackson 예외 메시지(getMessage)와 스택에는 원문 일부가 들어가므로 위치 정보를 뺀 원래 메시지만 남긴다
    private void handleConversionError(JsonProcessingException ex, ConsumerRecord<?, ?> record) {
        log.error(
                "[Final Fail - Message Conversion Error] 데이터 형식이 올바르지 않습니다. {}, Error: {}",
                describe(record),
                ex.getOriginalMessage());
    }

    private void handleInfraError(Exception ex, ConsumerRecord<?, ?> record) {
        log.error("[Final Fail - Infra Error] Target: AWS S3, {}, Error: {}", describe(record), ex.getMessage(), ex);
    }

    // 알 수 없는 예외
    private void handleUnknownError(Throwable t, ConsumerRecord<?, ?> record) {
        log.error("[Final Fail - Unknown Error] {}, Error: {}", describe(record), t.getMessage(), t);
    }

    private boolean isClientError(PostServiceException pse) {
        int status = pse.getCode().getStatus();
        return status >= 400 && status < 500;
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
