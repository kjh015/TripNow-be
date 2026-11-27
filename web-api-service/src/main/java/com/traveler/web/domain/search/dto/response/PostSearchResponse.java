package com.traveler.web.domain.search.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(description = "게시글 조회 응답 DTO")
public final class PostSearchResponse {
    private PostSearchResponse() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    @Schema(description = "게시글 이미지 정보")
    public record ImageInfo(
            @Schema(description = "S3 이미지 식별자(Key)", example = "images/2026/04/27/a1b2c3d4.png") String imageKey,
            @Schema(description = "이미지 정렬 순서", example = "1") int sortOrder) {}

    @Schema(description = "게시글 리스트")
    public record ListDTO(
            @Schema(description = "게시글 ID", example = "101") Long postId,
            @Schema(description = "작성자 ID", example = "202") Long memberId,
            @Schema(description = "작성자 닉네임", example = "홍길동") String memberNickname,
            @Schema(description = "게시글 제목", example = "경주 황리단길 맛집 투어") String title,
            @Schema(description = "카테고리", example = "FOOD") String category,
            @Schema(description = "지역", example = "GYEONGJU") String region,
            @Schema(description = "평균 별점", example = "4.5") Double starAvg,
            @Schema(description = "조회수", example = "1250") Long viewCount,
            @Schema(description = "좋아요 수", example = "45") Long likeCount,
            @Schema(description = "댓글 수", example = "12") Long commentCount,
            @Schema(description = "인기 점수", example = "10.1") Double popularityScore,
            @Schema(description = "최종 수정 일시", example = "2026-04-27T19:39:46") Instant updatedAt,
            @Schema(description = "게시글 본문 버전. 작성·수정 응답의 version 이상이면 그 쓰기가 반영된 것이다. 버전 도입 전 문서는 null", example = "3")
                    Long version) {}

    @Schema(description = "게시글 상세 정보")
    public record DetailDTO(
            @Schema(description = "게시글 ID", example = "101") Long postId,
            @Schema(description = "작성자 ID", example = "202") Long memberId,
            @Schema(description = "작성자 닉네임", example = "홍길동") String memberNickname,
            @Schema(description = "게시글 제목", example = "경주 황리단길 맛집 투어") String title,
            @Schema(description = "게시글 본문 내용", example = "황리단길에서 정말 맛있는 식당을 발견했습니다...") String content,
            @Schema(description = "여행 장소 명칭", example = "황리단길") String travelPlace,
            @Schema(description = "상세 주소", example = "경상북도 경주시 포석로") String address,
            @Schema(description = "카테고리", example = "FOOD") String category,
            @Schema(description = "지역", example = "GYEONGJU") String region,
            @Schema(description = "평균 별점", example = "4.5") Double starAvg,
            @Schema(description = "조회수", example = "1250") Long viewCount,
            @Schema(description = "좋아요 수", example = "45") Long likeCount,
            @Schema(description = "댓글 수", example = "12") Long commentCount,
            @Schema(description = "최종 수정 일시", example = "2026-04-27T19:39:46") Instant updatedAt,
            @Schema(description = "이미지 리스트") List<ImageInfo> images,
            @Schema(description = "게시글 본문 버전. 작성·수정 응답의 version 이상이면 그 쓰기가 반영된 것이다. 버전 도입 전 문서는 null", example = "3")
                    Long version) {}

    @Schema(description = "검색어 자동완성 응답")
    public record AutocompleteDTO(
            @Schema(description = "추천 검색어 제목 리스트", example = "[\"경주 맛집\", \"경주 가볼만한곳\"]") List<String> titles) {}
}
