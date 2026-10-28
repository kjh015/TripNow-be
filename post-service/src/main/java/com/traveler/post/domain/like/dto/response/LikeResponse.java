package com.traveler.post.domain.like.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public final class LikeResponse {

    private LikeResponse() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    // likeCount: 요청 반영 직후의 게시글 좋아요 수. 사용자 화면에는 검색 문서 갱신을 기다리지 않고 이 값을 바로 표시
    public record StatsDTO(
            @Schema(description = "게시글 ID", example = "101") Long postId,
            @Schema(description = "게시글 좋아요 수", example = "25") Long likeCount) {}
}
