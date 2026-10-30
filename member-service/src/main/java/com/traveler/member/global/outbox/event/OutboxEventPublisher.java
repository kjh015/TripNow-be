package com.traveler.member.global.outbox.event;

import com.traveler.member.global.exception.MemberServiceException;
import com.traveler.member.global.exception.code.MemberServiceErrorCode;
import com.traveler.member.global.kafka.KafkaTopicProperties;
import org.springframework.context.ApplicationEventPublisher;

public abstract class OutboxEventPublisher {
    private final ApplicationEventPublisher eventPublisher;
    private final KafkaTopicProperties topicProperties;

    protected OutboxEventPublisher(ApplicationEventPublisher eventPublisher, KafkaTopicProperties topicProperties) {
        this.eventPublisher = eventPublisher;
        this.topicProperties = topicProperties;
    }

    protected void publish(Long aggregateId, OutboxEventType type, Object payload) {
        String topic = topicProperties.getTopic(type.getTopicKey());
        if (topic == null || topic.isBlank()) {
            throw new MemberServiceException(MemberServiceErrorCode.TOPIC_NOT_CONFIGURED);
        }
        eventPublisher.publishEvent(OutboxEvent.of(aggregateId, type, topic, payload));
    }
}
