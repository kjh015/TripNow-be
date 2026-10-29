package com.traveler.web.domain.post.client.dto.response;

public final class LikeClientResponse {

    private LikeClientResponse() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public record StatsDTO(Long postId, Long likeCount) {}
}
