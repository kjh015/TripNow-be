package com.traveler.post.integration.member.publisher;

import com.traveler.post.global.kafka.KafkaTopicProperties;
import com.traveler.post.global.outbox.event.OutboxEventPublisher;
import com.traveler.post.integration.member.dto.message.MemberMessage;
import com.traveler.post.integration.member.enums.MemberEventType;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** 회원 탈퇴 Saga의 결과(정리 완료)를 member-service에 알린다. 호출한 트랜잭션이 커밋될 때 outbox 행이 저장된다. */
@Component
public class MemberOutboxPublisher extends OutboxEventPublisher {

    public MemberOutboxPublisher(ApplicationEventPublisher eventPublisher, KafkaTopicProperties topicProperties) {
        super(eventPublisher, topicProperties);
    }

    public void publishContentDeleted(MemberMessage.ContentDeletedDTO payload) {
        publish(payload.memberId(), MemberEventType.CONTENT_DELETED, payload);
    }
}
