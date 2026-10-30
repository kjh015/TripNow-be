package com.traveler.search.domain.like.dto.message;

import java.time.Instant;
import java.util.List;

public final class LikeSearchMessage {

    private LikeSearchMessage() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public record AddedDTO(Long likeId, Long postId, Long memberId, Instant createdAt) {}

    public record RemovedDTO(Long likeId) {}

    public record BatchRemovedDTO(Long postId, List<Long> likeIds) {}
}
