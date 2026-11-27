package com.traveler.search.domain.post.dto.message;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

public final class PostSearchMessage {

    private PostSearchMessage() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public record ImageInfo(String imageKey, int sortOrder) {}

    // version: post-service 게시글 본문 버전. null이면 버전 도입 전에 발행된 메시지다(기존 경로로 처리)
    public record CreatedDTO(
            Long postId,
            Long memberId,
            String title,
            String content,
            String category,
            String region,
            String travelPlace,
            String address,
            Instant createdAt,
            List<ImageInfo> images,
            Long version) {}

    // 버전이 있는 메시지는 게시글 전체 상태(스냅숏)다. 버전 없는 메시지의 부분 갱신(updatePartial)이 null 필드를 덮어쓰지 않도록
    // NON_EMPTY는 남긴다
    @JsonInclude(JsonInclude.Include.NON_EMPTY) // null이나 ""은 JSON 직렬화에서 제외
    public record UpdatedDTO(
            Long postId,
            Long memberId,
            String title,
            String content,
            String category,
            String region,
            String travelPlace,
            String address,
            Instant createdAt,
            Instant updatedAt,
            List<ImageInfo> images,
            Long version) {}

    public record DeletedDTO(Long postId, Long version, Instant deletedAt) {}

    /** 본문 반영(CREATED·UPDATED)에 쓰는 게시글 전체 상태. 생성 메시지는 updatedAt이 없다(기존 생성 문서와 같다) */
    public record ContentSnapshot(
            Long postId,
            Long memberId,
            String title,
            String titleChosung,
            String content,
            String category,
            String region,
            String travelPlace,
            String address,
            Instant createdAt,
            Instant updatedAt,
            List<ImageInfo> images,
            long version) {}

    // syncedAt: post-service가 통계를 읽어 발행한 시각(epoch ms). 이보다 새로운 통계가 이미 반영돼 있으면 무시한다
    public record StatUpdatedDTO(
            Long postId, Double starAvg, Long commentCount, Long likeCount, Long viewCount, Long syncedAt) {}

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record StatUpdateDoc(
            Long postId,
            Double starAvg,
            Long commentCount,
            Long likeCount,
            Long viewCount,
            Double popularityScore,
            Double popularityScoreValue,
            Long statSyncedAt) {}
}
