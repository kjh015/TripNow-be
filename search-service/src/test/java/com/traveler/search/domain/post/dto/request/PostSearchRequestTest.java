package com.traveler.search.domain.post.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.traveler.search.domain.post.enums.PostSortType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

class PostSearchRequestTest {

    @Test
    @DisplayName("검색 요청의 빈 값은 기본값으로 채운다")
    void searchDefaults() {
        PostSearchRequest.SearchDTO dto = PostSearchRequest.SearchDTO.builder().build();

        assertThat(dto.sort()).isEqualTo(PostSortType.ACCURACY);
        assertThat(dto.direction()).isEqualTo(Sort.Direction.DESC);
        assertThat(dto.page()).isZero();
        assertThat(dto.size()).isEqualTo(10);
    }

    @Test
    @DisplayName("검색 요청의 잘못된 값은 검증이 거부하도록 그대로 둔다")
    void searchKeepsInvalidValues() {
        PostSearchRequest.SearchDTO dto =
                PostSearchRequest.SearchDTO.builder().page(-1).size(0).build();

        assertThat(dto.page()).isEqualTo(-1);
        assertThat(dto.size()).isZero();
    }

    @Test
    @DisplayName("검색 Pageable은 요청 정렬 뒤에 작성일 정렬을 붙인다")
    void toPageable() {
        Pageable pageable = PostSearchRequest.SearchDTO.builder()
                .sort(PostSortType.VIEW_COUNT)
                .direction(Sort.Direction.ASC)
                .page(2)
                .size(5)
                .build()
                .toPageable();

        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(5);
        assertThat(pageable.getSort()).containsExactly(Sort.Order.asc("viewCount"), Sort.Order.asc("createdAt"));
    }

    @Test
    @DisplayName("피드 요청의 빈 값은 기본값으로 채운다")
    void feedDefaults() {
        PostSearchRequest.FeedDTO dto = PostSearchRequest.FeedDTO.builder().build();

        assertThat(dto.sort()).isEqualTo(PostSortType.ACCURACY);
        assertThat(dto.direction()).isEqualTo(Sort.Direction.DESC);
        assertThat(dto.size()).isEqualTo(10);
    }

    @Test
    @DisplayName("피드 정확도순은 방향을 보내도 DESC로 고정한다")
    void feedAccuracyIsAlwaysDesc() {
        PostSearchRequest.FeedDTO dto = PostSearchRequest.FeedDTO.builder()
                .sort(PostSortType.ACCURACY)
                .direction(Sort.Direction.ASC)
                .build();

        assertThat(dto.direction()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    @DisplayName("피드의 다른 정렬은 요청한 방향을 유지한다")
    void feedKeepsDirection() {
        PostSearchRequest.FeedDTO dto = PostSearchRequest.FeedDTO.builder()
                .sort(PostSortType.LATEST)
                .direction(Sort.Direction.ASC)
                .build();

        assertThat(dto.direction()).isEqualTo(Sort.Direction.ASC);
    }
}
