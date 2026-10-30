package com.traveler.search.domain.comment.dto.message;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

public final class CommentSearchMessage {

    private CommentSearchMessage() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public record CreatedDTO(
            Long commentId, Long postId, Long memberId, String content, Integer star, Instant createdAt) {}

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record UpdatedDTO(Long commentId, String content, Integer star, Instant updatedAt) {}

    public record DeletedDTO(Long commentId) {}

    public record BatchDeletedDTO(Long postId, List<Long> commentIds) {}
}
