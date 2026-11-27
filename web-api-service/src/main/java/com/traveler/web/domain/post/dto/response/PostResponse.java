package com.traveler.web.domain.post.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

public final class PostResponse {

    private PostResponse() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    @Schema(name = "PostWriteImageInfo", description = "게시글 이미지 정보")
    public record ImageInfo(
            @Schema(description = "S3 이미지 식별자(Key)", example = "posts/1/a1b2c3d4.png") String imageKey,
            @Schema(description = "이미지 정렬 순서", example = "1") int sortOrder) {}

    /**
     * 작성 응답. 저장된 게시글 전체와 새 글의 통계(0)를 담아, 게시글 상세 응답(PostSearchResponse.DetailDTO)처럼 쓸 수 있다. 화면은 검색에
     * 반영되기 전까지 이 값으로 방금 쓴 글을 그린다
     */
    public record CreateDTO(
            @Schema(description = "생성된 게시글 ID", example = "101") Long postId,
            @Schema(description = "작성자 ID", example = "202") Long memberId,
            @Schema(description = "작성자 닉네임. 조회에 실패하면 null", example = "홍길동") String memberNickname,
            @Schema(description = "게시글 제목", example = "경주 황리단길 맛집 투어") String title,
            @Schema(description = "게시글 본문 내용") String content,
            @Schema(description = "여행 장소 명칭", example = "황리단길") String travelPlace,
            @Schema(description = "상세 주소", example = "경상북도 경주시 포석로") String address,
            @Schema(description = "카테고리", example = "FOOD") String category,
            @Schema(description = "지역", example = "GYEONGJU") String region,
            @Schema(description = "평균 별점(새 글은 0)", example = "0.0") Double starAvg,
            @Schema(description = "조회수(새 글은 0)", example = "0") Long viewCount,
            @Schema(description = "좋아요 수(새 글은 0)", example = "0") Long likeCount,
            @Schema(description = "댓글 수(새 글은 0)", example = "0") Long commentCount,
            @Schema(description = "최종 수정 일시") Instant updatedAt,
            @Schema(description = "이미지 리스트") List<ImageInfo> images,
            @Schema(description = "생성 일시") Instant createdAt,
            @Schema(description = "게시글 본문 버전. 조회 응답의 version이 이 값 이상이면 조회에 반영된 것이다", example = "0") Long version) {}

    /** 수정 응답. 저장된 게시글 전체. 통계는 조회 응답의 값을 쓰므로 담지 않는다 */
    public record UpdateDTO(
            @Schema(description = "수정된 게시글 ID", example = "101") Long postId,
            @Schema(description = "작성자 ID", example = "202") Long memberId,
            @Schema(description = "작성자 닉네임. 조회에 실패하면 null", example = "홍길동") String memberNickname,
            @Schema(description = "게시글 제목", example = "경주 황리단길 맛집 투어") String title,
            @Schema(description = "게시글 본문 내용") String content,
            @Schema(description = "여행 장소 명칭", example = "황리단길") String travelPlace,
            @Schema(description = "상세 주소", example = "경상북도 경주시 포석로") String address,
            @Schema(description = "카테고리", example = "FOOD") String category,
            @Schema(description = "지역", example = "GYEONGJU") String region,
            @Schema(description = "수정 일시") Instant updatedAt,
            @Schema(description = "이미지 리스트") List<ImageInfo> images,
            @Schema(description = "생성 일시") Instant createdAt,
            @Schema(description = "게시글 본문 버전. 조회 응답의 version이 이 값 이상이면 조회에 반영된 것이다", example = "1") Long version) {}

    public record DeleteDTO(
            @Schema(description = "삭제된 게시글 ID", example = "101") Long postId,
            @Schema(description = "삭제 일시") Instant deletedAt,
            @Schema(description = "삭제로 올라간 게시글 본문 버전", example = "2") Long version) {}

    @Schema(name = "PostPresignedUrlResponse", description = "Presigned URL 발급 응답")
    public record PresignedUrlDTO(
            @Schema(
                            description = "S3 업로드용 URL",
                            example = "https://travel-bucket.s3.ap-northeast-2.amazonaws.com/posts/...")
                    String url,
            @Schema(
                            description = "S3 객체 키 (게시글 생성/수정 API 요청 시 images 리스트에 담아 전송)",
                            example = "images/2026/05/11/a1b2c3d4-e5f6.png")
                    String imageKey) {}
}
