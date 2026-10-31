package com.traveler.member.global.outbox.event;

public interface OutboxEventType {
    String getAggregateType();

    String getEventType();

    String getTopicKey();
}
