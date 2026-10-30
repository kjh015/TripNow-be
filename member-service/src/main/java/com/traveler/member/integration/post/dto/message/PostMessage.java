package com.traveler.member.integration.post.dto.message;

import java.time.Instant;

/** post-service가 발행하는 이벤트(post-service.content.events)의 payload. */
public final class PostMessage {

    private PostMessage() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public record ContentDeletedDTO(Long memberId, Instant deletedAt, int posts, int comments, int likes) {}
}
