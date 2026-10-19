package com.traveler.search.global.kafka;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.traveler.search.global.exception.SearchServiceException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class KafkaExceptionHandler {
    private static final String EVENT_ID_HEADER = "event-id";

    public void handle(Exception exception, ConsumerRecord<?, ?> record) {
        Throwable cause = NestedExceptionUtils.getMostSpecificCause(exception);

        switch (cause) {
            case SearchServiceException sse -> {
                if (isClientError(sse)) {
                    handleClientError(sse, record);
                } else {
                    handleServerError(sse, record);
                }
            }
            case JsonProcessingException jsonEx -> handleConversionError(jsonEx, record);
            case IOException ioEx -> handleInfraError(ioEx, record);
            case ElasticsearchException esEx -> handleInfraError(esEx, record);
            default -> handleUnknownError(cause, record);
        }
    }

    // 비즈니스 예외 (Client Error - 4xx)
    private void handleClientError(SearchServiceException sse, ConsumerRecord<?, ?> record) {
        log.error(
                "[Final Fail - Client Error] Code: {}, {}, Msg: {}",
                sse.getCode().getCode(),
                describe(record),
                sse.getMessage(),
                sse);
    }

    // 비즈니스 예외 (Server Error - 5xx)
    private void handleServerError(SearchServiceException sse, ConsumerRecord<?, ?> record) {
        log.error("[Final Fail - Server Error] {}, Msg: {}", describe(record), sse.getMessage(), sse);
    }

    // Jackson 예외 메시지(getMessage)와 스택에는 원문 일부가 들어가므로 위치 정보를 뺀 원래 메시지만 남긴다
    private void handleConversionError(JsonProcessingException ex, ConsumerRecord<?, ?> record) {
        log.error(
                "[Final Fail - Message Conversion Error] 데이터 형식이 올바르지 않습니다. {}, Error: {}",
                describe(record),
                ex.getOriginalMessage());
    }

    private void handleInfraError(Exception ex, ConsumerRecord<?, ?> record) {
        log.error(
                "[Final Fail - Infra Error] Target: Elasticsearch, {}, Error: {}",
                describe(record),
                ex.getMessage(),
                ex);
    }

    // 알 수 없는 예외
    private void handleUnknownError(Throwable t, ConsumerRecord<?, ?> record) {
        log.error("[Final Fail - Unknown Error] {}, Error: {}", describe(record), t.getMessage(), t);
    }

    private boolean isClientError(SearchServiceException sse) {
        int status = sse.getCode().getStatus();
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
