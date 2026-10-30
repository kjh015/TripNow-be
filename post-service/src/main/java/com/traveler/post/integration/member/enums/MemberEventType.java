package com.traveler.post.integration.member.enums;

import com.traveler.post.global.outbox.event.OutboxEventType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MemberEventType implements OutboxEventType {
    CONTENT_DELETED("MEMBER", "CONTENT_DELETED", "content-events");

    private final String aggregateType;
    private final String eventType;
    private final String topicKey;
}
