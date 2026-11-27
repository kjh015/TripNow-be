package com.traveler.web.domain.post.client.dto.response;

import java.time.Instant;
import java.util.List;

public final class PostClientResponse {

    private PostClientResponse() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public record ImageInfo(String imageKey, int sortOrder) {}

    // post-service가 돌려주는 저장된 게시글 전체. version은 게시글 본문 버전
    public record CreateDTO(
            Long postId,
            Long memberId,
            String title,
            String content,
            String travelPlace,
            String address,
            String category,
            String region,
            List<ImageInfo> images,
            Instant createdAt,
            Instant updatedAt,
            Long version) {}

    public record UpdateDTO(
            Long postId,
            Long memberId,
            String title,
            String content,
            String travelPlace,
            String address,
            String category,
            String region,
            List<ImageInfo> images,
            Instant createdAt,
            Instant updatedAt,
            Long version) {}

    public record DeleteDTO(Long postId, Instant deletedAt, Long version) {}

    public record PresignedUrlDTO(String url, String imageKey) {}
}
