package com.traveler.search.domain.post.service;

import static com.traveler.search.testsupport.fixture.SearchFixture.READABLE_TIMESTAMP;
import static com.traveler.search.testsupport.fixture.SearchFixture.nextId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.traveler.common.core.response.CursorResponse;
import com.traveler.search.domain.post.document.PostDocument;
import com.traveler.search.domain.post.dto.request.PostSearchRequest;
import com.traveler.search.domain.post.dto.response.PostSearchResponse;
import com.traveler.search.domain.post.enums.PostSortType;
import com.traveler.search.domain.post.repository.PostDocumentRepository;
import com.traveler.search.global.exception.SearchServiceException;
import com.traveler.search.testsupport.IntegrationTestSupport;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;

class PostFeedIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private PostSearchQueryService queryService;

    @Autowired
    private PostDocumentRepository postDocumentRepository;

    @Autowired
    private ElasticsearchOperations operations;

    @Test
    @DisplayName("25건을 10건씩 읽으면 10/10/5건이고 마지막에 hasNext가 false다")
    void readsAllPages() {
        // 다른 테스트 문서와 섞이지 않게 테스트마다 지역 값을 따로 쓴다
        String region = uniqueRegion();
        saveAll(region, 25);

        CursorResponse<PostSearchResponse.SearchDTO> first = feed(region, PostSortType.LATEST, null);
        CursorResponse<PostSearchResponse.SearchDTO> second = feed(region, PostSortType.LATEST, first.nextCursor());
        CursorResponse<PostSearchResponse.SearchDTO> third = feed(region, PostSortType.LATEST, second.nextCursor());

        assertThat(first.content()).hasSize(10);
        assertThat(first.hasNext()).isTrue();
        assertThat(second.content()).hasSize(10);
        assertThat(second.hasNext()).isTrue();
        assertThat(third.content()).hasSize(5);
        assertThat(third.hasNext()).isFalse();
        assertThat(third.nextCursor()).isNull();
    }

    @Test
    @DisplayName("페이지 사이에 새 글이 들어와도 최신순에서 중복·누락이 없다")
    void newDocumentBetweenPagesCausesNoDuplicates() {
        String region = uniqueRegion();
        List<Long> savedIds = saveAll(region, 15);

        CursorResponse<PostSearchResponse.SearchDTO> first = feed(region, PostSortType.LATEST, null);
        save(document(nextId(), region, 15).build());
        refresh();
        CursorResponse<PostSearchResponse.SearchDTO> second = feed(region, PostSortType.LATEST, first.nextCursor());

        List<Long> read = new ArrayList<>(postIds(first));
        read.addAll(postIds(second));
        assertThat(read).containsExactlyInAnyOrderElementsOf(savedIds);
    }

    @ParameterizedTest
    @EnumSource(PostSortType.class)
    @DisplayName("모든 정렬에서 동점 문서가 많아도 cursor로 끝까지 읽으면 전체를 한 번씩 읽는다")
    void cursorRoundTripForEverySort(PostSortType sort) {
        String region = uniqueRegion();
        List<Long> savedIds = saveAll(region, 7);

        assertThat(readAll(region, sort, Sort.Direction.DESC)).containsExactlyInAnyOrderElementsOf(savedIds);
    }

    @Test
    @DisplayName("오름차순도 값이 작은 글부터 끝까지 읽는다")
    void ascendingDirection() {
        String region = uniqueRegion();
        List<Long> savedIds = saveAll(region, 7);

        assertThat(readAll(region, PostSortType.VIEW_COUNT, Sort.Direction.ASC)).containsExactlyElementsOf(savedIds);
    }

    @Test
    @DisplayName("정렬 필드가 없는 예전 문서가 있어도 cursor가 끊기지 않는다")
    void documentWithoutSortField() {
        String region = uniqueRegion();
        List<Long> savedIds = new ArrayList<>(saveAll(region, 3));
        long legacyId = nextId();
        save(document(legacyId, region, 3).popularityScoreValue(null).build());
        savedIds.add(legacyId);
        refresh();

        assertThat(readAll(region, PostSortType.POPULAR, Sort.Direction.DESC))
                .containsExactlyInAnyOrderElementsOf(savedIds);
    }

    @Test
    @DisplayName("정확도순은 키워드 점수가 높은 글이 먼저 나온다")
    void accuracySortsByScore() {
        String region = uniqueRegion();
        long weakId = nextId();
        long strongId = nextId();
        save(document(weakId, region, 0).title("일기").content("경주 다녀옴").build());
        save(document(strongId, region, 1).title("경주 경주").content("경주").build());
        refresh();

        CursorResponse<PostSearchResponse.SearchDTO> result = queryService.getFeed(PostSearchRequest.FeedDTO.builder()
                .keyword("경주")
                .region(region)
                .sort(PostSortType.ACCURACY)
                .build());

        assertThat(postIds(result)).containsExactly(strongId, weakId);
    }

    @Test
    @DisplayName("다른 정렬의 cursor는 400 예외로 거부한다")
    void rejectsCursorOfOtherSort() {
        String region = uniqueRegion();
        saveAll(region, 3);
        String cursor = queryService
                .getFeed(request(region, PostSortType.LATEST, Sort.Direction.DESC, null, 1))
                .nextCursor();

        assertThatThrownBy(() ->
                        queryService.getFeed(request(region, PostSortType.POPULAR, Sort.Direction.DESC, cursor, 1)))
                .isInstanceOf(SearchServiceException.class);
    }

    private List<Long> readAll(String region, PostSortType sort, Sort.Direction direction) {
        List<Long> read = new ArrayList<>();
        String cursor = null;
        do {
            CursorResponse<PostSearchResponse.SearchDTO> page =
                    queryService.getFeed(request(region, sort, direction, cursor, 2));
            read.addAll(postIds(page));
            cursor = page.nextCursor();
        } while (cursor != null);
        return read;
    }

    private CursorResponse<PostSearchResponse.SearchDTO> feed(String region, PostSortType sort, String cursor) {
        return queryService.getFeed(request(region, sort, Sort.Direction.DESC, cursor, 10));
    }

    private static PostSearchRequest.FeedDTO request(
            String region, PostSortType sort, Sort.Direction direction, String cursor, int size) {
        return PostSearchRequest.FeedDTO.builder()
                .region(region)
                .sort(sort)
                .direction(direction)
                .cursor(cursor)
                .size(size)
                .build();
    }

    private static List<Long> postIds(CursorResponse<PostSearchResponse.SearchDTO> page) {
        return page.content().stream().map(PostSearchResponse.SearchDTO::postId).toList();
    }

    // i가 커질수록 viewCount가 크다. 나머지 정렬 값은 3개 단위로 같아 동점이 생긴다
    private List<Long> saveAll(String region, int count) {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            long id = nextId();
            save(document(id, region, i).build());
            ids.add(id);
        }
        refresh();
        return ids;
    }

    private static PostDocument.PostDocumentBuilder<?, ?> document(long id, String region, int order) {
        return PostDocument.builder()
                .id(id)
                .memberId(1L)
                .title("피드 게시글")
                .content("본문")
                .category("FOOD")
                .region(region)
                .starAvg((double) (order / 3))
                .viewCount((long) order)
                .popularityScoreValue((double) (order / 3))
                .createdAt(READABLE_TIMESTAMP.plus(order / 3, ChronoUnit.MINUTES));
    }

    private void save(PostDocument document) {
        postDocumentRepository.save(document);
    }

    private void refresh() {
        operations.indexOps(PostDocument.class).refresh();
    }

    private static String uniqueRegion() {
        return "REGION-" + UUID.randomUUID();
    }
}
