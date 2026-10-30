package com.traveler.post.domain.like.enums;

import com.traveler.post.global.outbox.event.OutboxEventType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum LikeEventType implements OutboxEventType {
    ADDED("LIKE", "ADDED", "like-events"),
    REMOVED("LIKE", "REMOVED", "like-events"),
    BATCH_REMOVED("LIKE", "BATCH_REMOVED", "like-events");

    private final String aggregateType;
    private final String eventType;
    private final String topicKey;
}
