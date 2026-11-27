package com.traveler.web.domain.search.client.dto.response;

import java.time.Instant;
import java.util.List;

public final class PostSearchClientResponse {

    private PostSearchClientResponse() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public record ImageInfo(String imageKey, int sortOrder) {}

    public record SearchDTO(
            Long postId,
            Long memberId,
            String title,
            String category,
            String region,
            Double starAvg,
            Long viewCount,
            Long likeCount,
            Long commentCount,
            Double popularityScore,
            Instant updatedAt,
            Long version) {}

    public record DetailDTO(
            Long postId,
            Long memberId,
            String title,
            String content,
            String travelPlace,
            String address,
            String category,
            String region,
            Double starAvg,
            Long viewCount,
            Long likeCount,
            Long commentCount,
            Instant updatedAt,
            List<ImageInfo> images,
            Long version) {}

    public record AutocompleteDTO(List<String> titles) {}

    public record MyDTO(
            Long postId,
            Long memberId,
            String title,
            String category,
            String region,
            Double starAvg,
            Long viewCount,
            Long likeCount,
            Long commentCount,
            Double popularityScore,
            Instant updatedAt,
            Long version) {}
}
