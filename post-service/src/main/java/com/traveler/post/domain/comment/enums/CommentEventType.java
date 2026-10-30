package com.traveler.post.domain.comment.enums;

import com.traveler.post.global.outbox.event.OutboxEventType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CommentEventType implements OutboxEventType {
    CREATED("COMMENT", "CREATED", "comment-events"),
    UPDATED("COMMENT", "UPDATED", "comment-events"),
    DELETED("COMMENT", "DELETED", "comment-events"),
    BATCH_DELETED("COMMENT", "BATCH_DELETED", "comment-events");

    private final String aggregateType;
    private final String eventType;
    private final String topicKey;
}
