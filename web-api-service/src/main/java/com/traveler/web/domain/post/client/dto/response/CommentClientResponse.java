package com.traveler.web.domain.post.client.dto.response;

import java.time.Instant;

public final class CommentClientResponse {

    private CommentClientResponse() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public record CreateDTO(Long commentId, Instant createdAt, Long commentCount, Double starAvg) {}

    public record UpdateDTO(Long commentId, Instant updatedAt, Long commentCount, Double starAvg) {}

    public record DeleteDTO(Long commentId, Instant deletedAt, Long commentCount, Double starAvg) {}
}
