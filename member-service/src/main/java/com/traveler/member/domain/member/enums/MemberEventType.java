package com.traveler.member.domain.member.enums;

import com.traveler.member.global.outbox.event.OutboxEventType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MemberEventType implements OutboxEventType {
    WITHDRAWN("MEMBER", "WITHDRAWN", "member-events");

    private final String aggregateType;
    private final String eventType;
    private final String topicKey;
}
