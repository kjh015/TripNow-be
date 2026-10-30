package com.traveler.post.integration.member.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.traveler.post.integration.member.dto.message.MemberMessage;
import com.traveler.post.integration.member.handler.MemberWithdrawalHandler;
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
 * member-service 회원 이벤트 소비. 메시지 키는 memberId라 같은 회원의 이벤트는 한 파티션에서 차례로 처리된다.
 *
 * <p>처리 중 예외는 잡지 않는다. 재시도 후에도 실패하면 KafkaExceptionHandler가 ERROR(key=memberId, eventId)를 남기고 DLT로 보낸다.
 * 탈퇴 자체는 되돌리지 않으므로, DLT 메시지를 원래 토픽으로 다시 보내 재처리한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MemberEventConsumer {

    private final MemberWithdrawalHandler memberWithdrawalHandler;
    private final ObjectMapper objectMapper;

    @SneakyThrows
    @KafkaListener(topics = "${app.kafka.topics.member-events}", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(
            @Payload String payload,
            @Header(value = "event-type", required = false) String eventType,
            @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key,
            @Header(value = "event-id", required = false) String eventId,
            Acknowledgment ack) {

        log.debug("Member Kafka Consumer: Type=[{}], key=[{}], eventId=[{}]", eventType, key, eventId);

        if (eventType == null) {
            log.error("Missing 'event-type' header. key={} eventId={}", key, eventId);
            ack.acknowledge();
            return;
        }

        switch (eventType) {
            case "WITHDRAWN" -> {
                MemberMessage.WithdrawnDTO dto = objectMapper.readValue(payload, MemberMessage.WithdrawnDTO.class);
                memberWithdrawalHandler.handle(dto.memberId());
            }
            default -> log.warn("지원하지 않는 회원 이벤트 타입입니다: {}", eventType);
        }
        ack.acknowledge();
    }
}
