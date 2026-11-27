package com.traveler.post.domain.post.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

public final class PostResponse {

    private PostResponse() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    @Schema(name = "PostImageInfo", description = "게시글 이미지 정보")
    public record ImageInfo(
            @Schema(description = "S3 이미지 식별자(Key)", example = "posts/1/a1b2c3d4.png") String imageKey,
            @Schema(description = "이미지 정렬 순서", example = "1") int sortOrder) {}

    // 작성·수정 응답은 저장된 게시글 전체를 돌려준다. 화면이 검색 서버에 반영되기 전에도 방금 쓴 내용을 그릴 수 있게 한다.
    // 필드 이름은 검색 서버의 게시글 상세 응답(PostSearchResponse.DetailDTO)과 같게 둔다
    public record CreateDTO(
            @Schema(description = "생성된 게시글 ID", example = "101") Long postId,
            @Schema(description = "작성자 ID", example = "202") Long memberId,
            @Schema(description = "게시글 제목") String title,
            @Schema(description = "게시글 본문 내용") String content,
            @Schema(description = "여행 장소 명칭") String travelPlace,
            @Schema(description = "상세 주소") String address,
            @Schema(description = "카테고리 코드", example = "FOOD") String category,
            @Schema(description = "지역 코드", example = "GYEONGJU") String region,
            @Schema(description = "이미지 리스트(정렬 순서대로)") List<ImageInfo> images,
            @Schema(description = "생성 일시") Instant createdAt,
            @Schema(description = "최종 수정 일시") Instant updatedAt,
            @Schema(description = "게시글 본문 버전. 조회 응답의 version이 이 값 이상이면 검색 서버에 반영된 것이다", example = "0") Long version) {}

    public record UpdateDTO(
            @Schema(description = "수정된 게시글 ID", example = "101") Long postId,
            @Schema(description = "작성자 ID", example = "202") Long memberId,
            @Schema(description = "게시글 제목") String title,
            @Schema(description = "게시글 본문 내용") String content,
            @Schema(description = "여행 장소 명칭") String travelPlace,
            @Schema(description = "상세 주소") String address,
            @Schema(description = "카테고리 코드", example = "FOOD") String category,
            @Schema(description = "지역 코드", example = "GYEONGJU") String region,
            @Schema(description = "이미지 리스트(정렬 순서대로)") List<ImageInfo> images,
            @Schema(description = "생성 일시") Instant createdAt,
            @Schema(description = "수정 일시") Instant updatedAt,
            @Schema(description = "게시글 본문 버전. 조회 응답의 version이 이 값 이상이면 검색 서버에 반영된 것이다", example = "1") Long version) {}

    public record DeleteDTO(
            @Schema(description = "삭제된 게시글 ID", example = "101") Long postId,
            @Schema(description = "삭제 일시") Instant deletedAt,
            @Schema(description = "삭제로 올라간 게시글 본문 버전", example = "2") Long version) {}
}
