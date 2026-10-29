package com.traveler.web.domain.post.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public final class CommentResponse {

    private CommentResponse() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    // commentCount·starAvg: 요청 반영 직후의 게시글 통계. 사용자 화면에는 검색 문서 갱신을 기다리지 않고 이 값을 바로 표시
    public record CreateDTO(
            @Schema(description = "생성된 댓글 ID", example = "501") Long commentId,
            @Schema(description = "작성 일시") Instant createdAt,
            @Schema(description = "게시글 댓글 수", example = "11") Long commentCount,
            @Schema(description = "게시글 평균 별점", example = "4.2") Double starAvg) {}

    public record UpdateDTO(
            @Schema(description = "수정된 댓글 ID", example = "501") Long commentId,
            @Schema(description = "수정 일시") Instant updatedAt,
            @Schema(description = "게시글 댓글 수", example = "11") Long commentCount,
            @Schema(description = "게시글 평균 별점", example = "4.2") Double starAvg) {}

    public record DeleteDTO(
            @Schema(description = "삭제된 댓글 ID", example = "501") Long commentId,
            @Schema(description = "삭제 일시") Instant deletedAt,
            @Schema(description = "게시글 댓글 수", example = "10") Long commentCount,
            @Schema(description = "게시글 평균 별점", example = "4.1") Double starAvg) {}
}
