package com.traveler.member.global.outbox.event;

import java.util.UUID;
import lombok.Builder;

@Builder
public record OutboxEvent(
        String eventId, String topic, String aggregateType, Long aggregateId, String eventType, Object payload) {
    public OutboxEvent {
        if (eventId == null) {
            eventId = UUID.randomUUID().toString();
        }
    }

    public static OutboxEvent of(Long aggregateId, OutboxEventType type, String topic, Object payload) {
        return OutboxEvent.builder()
                .topic(topic)
                .aggregateType(type.getAggregateType())
                .aggregateId(aggregateId)
                .eventType(type.getEventType())
                .payload(payload)
                .build();
    }
}
