package com.traveler.useractivity.domain.process.sink.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.traveler.common.core.code.ErrorCode;
import com.traveler.useractivity.domain.process.core.message.LogPayload;
import com.traveler.useractivity.domain.process.sink.message.SinkMessage;
import com.traveler.useractivity.global.exception.UserActivityServiceException;
import com.traveler.useractivity.global.kafka.KafkaProducer;
import com.traveler.useractivity.global.kafka.KafkaTopicProperties;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PostViewedSinkHandler implements SinkHandler {

    private final KafkaProducer kafkaProducer;
    private final KafkaTopicProperties topics;
    private final ObjectMapper objectMapper;

    private static final String FIELD_EVENT_ACTION = "event";
    private static final String FIELD_POST_ID = "postId";
    private static final String ACTION_VIEW = "travel_detail_pageview";
    private static final String EVENT_TYPE = "POST_VIEWED";

    private static final Pattern NUMERIC_PATTERN = Pattern.compile("^\\d+$");

    @Override
    public boolean supports(Map<String, String> logData) {
        String eventAction = logData.get(FIELD_EVENT_ACTION);
        String postIdStr = logData.get(FIELD_POST_ID);
        return ACTION_VIEW.equalsIgnoreCase(eventAction)
                && postIdStr != null
                && !"null".equalsIgnoreCase(postIdStr)
                && NUMERIC_PATTERN.matcher(postIdStr).matches();
    }

    @Override
    public CompletableFuture<Void> handle(LogPayload<Map<String, String>> logPayload) {
        Map<String, String> logData = logPayload.data();
        Long postId = Long.valueOf(logData.get(FIELD_POST_ID));

        String payload = serializePayload(new SinkMessage.PostViewedDTO(postId));

        log.debug("조회수 증가 이벤트를 Kafka로 전송합니다. Post ID: {}", postId);

        return kafkaProducer
                .send(
                        topics.sinkEvents(),
                        payload,
                        EVENT_TYPE,
                        eventId(logPayload.metadata().traceId()))
                .thenAccept(result -> {});
    }

    // 같은 로그를 다시 처리해도 값이 같아야 post-service가 중복을 걸러낸다. 한 요청의 다른 이벤트와 겹치지 않게 타입을 붙인다
    private String eventId(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return EVENT_TYPE + ":" + UUID.randomUUID();
        }
        return EVENT_TYPE + ":" + traceId;
    }

    private String serializePayload(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new UserActivityServiceException(ErrorCode.INTERNAL_SERVER_ERROR, e);
        }
    }
}
