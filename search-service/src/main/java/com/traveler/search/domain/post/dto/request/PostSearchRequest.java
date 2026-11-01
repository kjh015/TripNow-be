package com.traveler.search.domain.post.dto.request;

import com.traveler.search.domain.post.enums.PostSortType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class PostSearchRequest {

    private PostSearchRequest() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    @Builder
    public record SearchDTO(
            @Schema(description = "검색어 (제목, 본문, 장소 포함 검색)", example = "맛집")
                    @Size(max = 50, message = "검색어는 50자를 초과할 수 없습니다.")
                    String keyword,
            @Schema(description = "카테고리 필터", example = "FOOD") String category,
            @Schema(description = "지역 필터", example = "SEOUL") String region,
            @Schema(description = "정렬 기준 (ACCURACY, POPULAR, LATEST, STAR_AVG, VIEW_COUNT)", example = "ACCURACY")
                    PostSortType sort,
            @Schema(description = "정렬 방향", example = "DESC") Sort.Direction direction,
            @Schema(description = "페이지 번호 (0부터 시작)", example = "0") @Min(0) Integer page,
            @Schema(description = "페이지 크기", example = "10") @Positive Integer size) {

        private static final int DEFAULT_SIZE = 10;

        // 음수는 여기서 바꾸지 않는다. @Min·@Positive 검증이 400으로 거부한다
        public SearchDTO {
            sort = (sort == null) ? PostSortType.ACCURACY : sort;
            direction = (direction == null) ? Sort.Direction.DESC : direction;
            page = (page == null) ? 0 : page;
            size = (size == null) ? DEFAULT_SIZE : size;
        }

        public Pageable toPageable() {
            return PageRequest.of(page, size, sort.getSort(direction).and(PostSortType.LATEST.getSort(direction)));
        }
    }

    @Builder
    public record FeedDTO(
            @Schema(description = "검색어 (제목, 본문, 장소 포함 검색)", example = "맛집")
                    @Size(max = 50, message = "검색어는 50자를 초과할 수 없습니다.")
                    String keyword,
            @Schema(description = "카테고리 필터", example = "FOOD") String category,
            @Schema(description = "지역 필터", example = "SEOUL") String region,
            @Schema(description = "정렬 기준 (ACCURACY, POPULAR, LATEST, STAR_AVG, VIEW_COUNT)", example = "ACCURACY")
                    PostSortType sort,
            @Schema(description = "정렬 방향 (ACCURACY는 항상 DESC)", example = "DESC") Sort.Direction direction,
            @Schema(description = "이전 응답의 nextCursor. 첫 요청에서는 생략")
                    @Size(max = 500, message = "cursor는 500자를 초과할 수 없습니다.")
                    String cursor,
            @Schema(description = "조회 크기", example = "10") @Positive @Max(50) Integer size) {

        private static final int DEFAULT_SIZE = 10;

        public FeedDTO {
            sort = (sort == null) ? PostSortType.ACCURACY : sort;
            direction = (sort == PostSortType.ACCURACY || direction == null) ? Sort.Direction.DESC : direction;
            size = (size == null) ? DEFAULT_SIZE : size;
        }
    }
}
