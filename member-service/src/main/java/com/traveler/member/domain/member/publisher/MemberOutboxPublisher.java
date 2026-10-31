package com.traveler.member.domain.member.publisher;

import com.traveler.member.domain.member.dto.message.MemberMessage;
import com.traveler.member.domain.member.enums.MemberEventType;
import com.traveler.member.global.kafka.KafkaTopicProperties;
import com.traveler.member.global.outbox.event.OutboxEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 회원 이벤트를 Outbox에 기록한다. 호출한 트랜잭션에서 outbox 행을 함께 저장하고, 커밋된 뒤 Kafka로 발행한다.
 *
 * <p>회원 탈퇴는 Saga에서 되돌릴 수 없는 단계(pivot transaction)다. 이후 post-service의 콘텐츠 정리는 보상하지 않고, 멱등 처리와
 * 재시도·DLT를 통해 최종적으로 완료한다. 따라서 회원 탈퇴와 이벤트 기록을 같은 트랜잭션으로 묶어 탈퇴만 완료되거나 이벤트만 기록되는
 * 상황을 방지한다.
 */
@Component
public class MemberOutboxPublisher extends OutboxEventPublisher {

    public MemberOutboxPublisher(ApplicationEventPublisher eventPublisher, KafkaTopicProperties topicProperties) {
        super(eventPublisher, topicProperties);
    }

    public void publishWithdrawn(MemberMessage.WithdrawnDTO payload) {
        publish(payload.memberId(), MemberEventType.WITHDRAWN, payload);
    }
}
