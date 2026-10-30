package com.traveler.member.integration.post.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.traveler.member.domain.member.service.command.MemberWithdrawalCommandService;
import com.traveler.member.integration.post.dto.message.PostMessage;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * post-service의 탈퇴 회원 콘텐츠 정리 완료 이벤트 소비. 재시도 후에도 실패하면
 * KafkaExceptionHandler가 ERROR(key=memberId, eventId)를 남기고 DLT로 보낸다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PostEventConsumer {

    private final MemberWithdrawalCommandService memberWithdrawalCommandService;
    private final ObjectMapper objectMapper;

    @SneakyThrows
    @KafkaListener(topics = "${app.kafka.topics.content-events}", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(
            @Payload String payload,
            @Header(value = "event-type", required = false) String eventType,
            @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key,
            @Header(value = "event-id", required = false) String eventId,
            Acknowledgment ack) {

        log.debug("Member Content Kafka Consumer: Type=[{}], key=[{}], eventId=[{}]", eventType, key, eventId);

        if (eventType == null) {
            log.error("Missing 'event-type' header. key={} eventId={}", key, eventId);
            ack.acknowledge();
            return;
        }

        switch (eventType) {
            case "CONTENT_DELETED" -> {
                PostMessage.ContentDeletedDTO dto =
                        objectMapper.readValue(payload, PostMessage.ContentDeletedDTO.class);
                memberWithdrawalCommandService.markContentDeleted(dto.memberId(), dto.deletedAt());
            }
            default -> log.warn("지원하지 않는 회원 정리 이벤트 타입입니다: {}", eventType);
        }
        ack.acknowledge();
    }
}
