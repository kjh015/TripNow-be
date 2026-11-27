package com.traveler.search.domain.post.service;

import static com.traveler.search.testsupport.fixture.SearchFixture.READABLE_TIMESTAMP;
import static com.traveler.search.testsupport.fixture.SearchFixture.nextId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.traveler.search.domain.post.document.PostDocument;
import com.traveler.search.domain.post.dto.message.PostSearchMessage;
import com.traveler.search.domain.post.dto.request.PostSearchRequest;
import com.traveler.search.domain.post.dto.response.PostSearchResponse;
import com.traveler.search.domain.post.enums.PostSortType;
import com.traveler.search.domain.post.repository.PostDocumentRepository;
import com.traveler.search.domain.post.repository.PostDocumentRepositoryCustom.RankingResult;
import com.traveler.search.domain.post.scheduler.PostTombstoneCleanupScheduler;
import com.traveler.search.global.exception.SearchServiceException;
import com.traveler.search.global.util.HangulUtils;
import com.traveler.search.testsupport.IntegrationTestSupport;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;

/** 버전이 있는 게시글 이벤트가 순서·중복과 상관없이 같은 결과를 내고, 삭제 표시 문서가 모든 조회에서 빠지는지 확인한다. */
class PostVersionedIndexingIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private PostSearchCommandService commandService;

    @Autowired
    private PostSearchQueryService queryService;

    @Autowired
    private PostDocumentRepository postDocumentRepository;

    @Autowired
    private PostTombstoneCleanupScheduler tombstoneCleanupScheduler;

    @Autowired
    private ElasticsearchOperations operations;

    @Autowired
    private ElasticsearchClient client;

    @Value("${app.elasticsearch.indices.post}")
    private String postIndexName;

    @Test
    @DisplayName("UPDATED(v1) 뒤에 CREATED(v0)가 와도 문서는 v1 내용이다")
    void staleCreatedAfterUpdatedIsIgnored() {
        long postId = nextId();

        commandService.updateDocument(updated(postId, "수정한 제목", 1L));
        commandService.createDocument(created(postId, "처음 제목", 0L));

        PostDocument doc = postDocumentRepository.findById(postId).orElseThrow();
        assertThat(doc.getTitle()).isEqualTo("수정한 제목");
        assertThat(doc.getPostVersion()).isEqualTo(1L);
        assertThat(doc.getUpdatedAt()).isEqualTo(READABLE_TIMESTAMP);
        // UPDATED만으로 만든 문서에도 통계 기본값이 들어간다
        assertThat(doc.getLikeCount()).isZero();
        assertThat(doc.getStarAvg()).isZero();
        assertThat(doc.isTombstone()).isFalse();
    }

    @Test
    @DisplayName("DELETED(v1) 뒤에 CREATED(v0)가 와도 삭제 표시로 남고, 조회 8곳 모두에서 보이지 않는다")
    void staleCreatedAfterDeletedStaysHidden() {
        long postId = nextId();
        long memberId = nextId();
        String category = "CAT-" + UUID.randomUUID();
        String title = "tomb" + postId;

        commandService.createDocument(created(postId, memberId, category, title, 0L));
        refresh();
        assertVisible(postId, memberId, category, title, true);

        commandService.deleteDocument(deleted(postId, 1L));
        commandService.createDocument(created(postId, memberId, category, title, 0L));
        refresh();

        PostDocument doc = postDocumentRepository.findById(postId).orElseThrow();
        assertThat(doc.isTombstone()).isTrue();
        assertThat(doc.getPostVersion()).isEqualTo(1L);
        assertThat(doc.getDeletedAt()).isEqualTo(READABLE_TIMESTAMP);
        assertVisible(postId, memberId, category, title, false);
    }

    @Test
    @DisplayName("문서가 없을 때 DELETED가 먼저 오면 삭제 표시 문서를 만들어, 늦게 온 CREATED가 글을 만들지 않는다")
    void deletedBeforeCreatedCreatesTombstone() {
        long postId = nextId();

        commandService.deleteDocument(deleted(postId, 1L));
        commandService.createDocument(created(postId, "늦게 온 생성", 0L));

        PostDocument doc = postDocumentRepository.findById(postId).orElseThrow();
        assertThat(doc.isTombstone()).isTrue();
        assertThat(doc.getTitle()).isNull();
    }

    @Test
    @DisplayName("DELETED(v1) 뒤 복구 CREATED(v2)가 오면 다시 보이고 삭제 전 통계가 남아 있다")
    void restoreAfterDeleteKeepsStatistics() {
        long postId = nextId();
        commandService.createDocument(created(postId, "복구할 글", 0L));
        commandService.updateStatistics(new PostSearchMessage.StatUpdatedDTO(postId, 4.0, 2L, 3L, 10L, 1_000L));

        commandService.deleteDocument(deleted(postId, 1L));
        commandService.createDocument(created(postId, "복구할 글", 2L));

        PostDocument doc = postDocumentRepository.findById(postId).orElseThrow();
        assertThat(doc.isTombstone()).isFalse();
        assertThat(doc.getDeletedAt()).isNull();
        assertThat(doc.getPostVersion()).isEqualTo(2L);
        assertThat(doc.getLikeCount()).isEqualTo(3L);
        assertThat(doc.getCommentCount()).isEqualTo(2L);
        assertThat(queryService.getPostDetail(postId).version()).isEqualTo(2L);
    }

    @Test
    @DisplayName("같은 메시지를 두 번 받아도 결과가 같다")
    void sameMessageTwice() {
        long postId = nextId();
        PostSearchMessage.UpdatedDTO update = updated(postId, "두 번 온 수정", 1L);

        commandService.createDocument(created(postId, "처음", 0L));
        commandService.updateDocument(update);
        PostDocument once = postDocumentRepository.findById(postId).orElseThrow();
        commandService.updateDocument(update);
        PostDocument twice = postDocumentRepository.findById(postId).orElseThrow();

        assertThat(twice).usingRecursiveComparison().isEqualTo(once);

        PostSearchMessage.DeletedDTO delete = deleted(postId, 2L);
        commandService.deleteDocument(delete);
        commandService.deleteDocument(delete);
        PostDocument tombstone = postDocumentRepository.findById(postId).orElseThrow();
        assertThat(tombstone.isTombstone()).isTrue();
        assertThat(tombstone.getPostVersion()).isEqualTo(2L);
    }

    @Test
    @DisplayName("통계 갱신과 본문 반영이 섞여도 서로의 값을 덮어쓰지 않는다")
    void statisticsAndContentDoNotOverwriteEachOther() {
        long postId = nextId();
        commandService.createDocument(created(postId, "통계 섞임", 0L));

        commandService.updateStatistics(new PostSearchMessage.StatUpdatedDTO(postId, 4.0, 2L, 30L, 10L, 2_000L));
        commandService.updateDocument(updated(postId, "통계 뒤 수정", 1L));
        // 늦게 온 예전 본문(v0)과 예전 통계(syncedAt 1000)
        commandService.createDocument(created(postId, "통계 섞임", 0L));
        commandService.updateStatistics(new PostSearchMessage.StatUpdatedDTO(postId, 1.0, 1L, 1L, 1L, 1_000L));

        PostDocument doc = postDocumentRepository.findById(postId).orElseThrow();
        assertThat(doc.getTitle()).isEqualTo("통계 뒤 수정");
        assertThat(doc.getPostVersion()).isEqualTo(1L);
        assertThat(doc.getLikeCount()).isEqualTo(30L);
        assertThat(doc.getStarAvg()).isEqualTo(4.0);
        // 인기 점수도 새 통계 값 그대로
        assertThat(doc.getPopularityScoreValue()).isEqualTo(30 * 5.0 + 2 * 3.0 + 10 * 0.1 + 4.0 * 10);
    }

    @Test
    @DisplayName("UPDATED로 제목이 바뀌면 초성 필드도 바뀐다")
    void updatedTitleChangesChosung() {
        long postId = nextId();
        commandService.createDocument(created(postId, "경주 여행", 0L));

        commandService.updateDocument(updated(postId, "부산 바다", 1L));

        assertThat(postDocumentRepository.findById(postId).orElseThrow().getTitleChosung())
                .isEqualTo(HangulUtils.extractChosung("부산 바다"));
    }

    @Test
    @DisplayName("버전 없는 메시지 뒤에 버전 있는 메시지가 오면 버전 있는 메시지가 반영된다")
    void versionedMessageAfterLegacyDocument() {
        long postId = nextId();
        commandService.createDocument(created(postId, "예전 메시지", null));
        assertThat(postDocumentRepository.findById(postId).orElseThrow().getPostVersion())
                .isNull();

        commandService.updateDocument(updated(postId, "새 메시지", 1L));

        PostDocument doc = postDocumentRepository.findById(postId).orElseThrow();
        assertThat(doc.getTitle()).isEqualTo("새 메시지");
        assertThat(doc.getPostVersion()).isEqualTo(1L);
        assertThat(queryService.getPostDetail(postId).version()).isEqualTo(1L);
    }

    @Test
    @DisplayName("삭제 표시 정리는 7일이 지난 것만 지운다")
    void cleanupRemovesOnlyExpiredTombstones() {
        long expired = nextId();
        long recent = nextId();
        long active = nextId();
        Instant now = Instant.now();
        commandService.deleteDocument(new PostSearchMessage.DeletedDTO(
                expired, 1L, now.minus(8, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MICROS)));
        commandService.deleteDocument(new PostSearchMessage.DeletedDTO(
                recent, 1L, now.minus(6, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MICROS)));
        commandService.createDocument(created(active, "살아 있는 글", 0L));
        refresh();

        tombstoneCleanupScheduler.cleanupTombstones();

        assertThat(postDocumentRepository.findById(expired)).isEmpty();
        assertThat(postDocumentRepository.findById(recent)).isPresent();
        assertThat(postDocumentRepository.findById(active)).isPresent();
    }

    @Test
    @DisplayName("문서가 없을 때 통계가 먼저 오면 통계만 담은 자리 표시 문서(삭제 표시, 버전 -1)를 만들고 조회 8곳 모두에서 보이지 않는다")
    void statisticsBeforeCreatedCreatesHiddenPlaceholder() {
        long postId = nextId();
        long memberId = nextId();
        String category = "CAT-" + UUID.randomUUID();
        String title = "placeholder" + postId;

        commandService.updateStatistics(stat(postId, 3L, 10L, 1_000L));
        refresh();

        PostDocument doc = postDocumentRepository.findById(postId).orElseThrow();
        assertThat(doc.isTombstone()).isTrue();
        assertThat(doc.getPostVersion()).isEqualTo(-1L);
        assertThat(doc.getDeletedAt()).isNotNull();
        assertThat(doc.getLikeCount()).isEqualTo(3L);
        assertThat(doc.getViewCount()).isEqualTo(10L);
        assertThat(doc.getTitle()).isNull();
        assertThat(statSyncedAt(postId)).isEqualTo(1_000L);
        assertVisible(postId, memberId, category, title, false);
    }

    @Test
    @DisplayName("자리 표시 문서 뒤에 CREATED(v0)가 오면 보이게 되고 먼저 온 통계를 이어받으며, 그보다 예전 통계는 무시된다")
    void createdAfterPlaceholderKeepsStatistics() {
        long postId = nextId();
        long memberId = nextId();
        String category = "CAT-" + UUID.randomUUID();
        String title = "placeholder" + postId;
        commandService.updateStatistics(stat(postId, 3L, 10L, 1_000L));

        commandService.createDocument(created(postId, memberId, category, title, 0L));
        refresh();

        PostDocument doc = postDocumentRepository.findById(postId).orElseThrow();
        assertThat(doc.isTombstone()).isFalse();
        assertThat(doc.getDeletedAt()).isNull();
        assertThat(doc.getPostVersion()).isEqualTo(0L);
        assertThat(doc.getTitle()).isEqualTo(title);
        assertThat(doc.getLikeCount()).isEqualTo(3L);
        assertThat(doc.getViewCount()).isEqualTo(10L);
        assertThat(statSyncedAt(postId)).isEqualTo(1_000L);
        assertVisible(postId, memberId, category, title, true);

        // 예전 통계(syncedAt 500)는 statSyncedAt 비교로 무시된다
        commandService.updateStatistics(stat(postId, 1L, 1L, 500L));

        assertThat(postDocumentRepository.findById(postId).orElseThrow().getLikeCount())
                .isEqualTo(3L);
        assertThat(statSyncedAt(postId)).isEqualTo(1_000L);
    }

    @Test
    @DisplayName("자리 표시 문서 뒤에 DELETED(v1), 늦은 CREATED(v0)가 오면 삭제 표시로 남고 통계는 남아 있다")
    void deletedAfterPlaceholderStaysHidden() {
        long postId = nextId();
        commandService.updateStatistics(stat(postId, 3L, 10L, 1_000L));

        commandService.deleteDocument(deleted(postId, 1L));
        commandService.createDocument(created(postId, "늦게 온 생성", 0L));

        PostDocument doc = postDocumentRepository.findById(postId).orElseThrow();
        assertThat(doc.isTombstone()).isTrue();
        assertThat(doc.getPostVersion()).isEqualTo(1L);
        assertThat(doc.getTitle()).isNull();
        assertThat(doc.getLikeCount()).isEqualTo(3L);
        assertThat(doc.getViewCount()).isEqualTo(10L);
    }

    @Test
    @DisplayName("자리 표시 문서에 더 새로운 통계가 오면 자리 표시 상태는 그대로이고 통계만 바뀐다")
    void newerStatisticsKeepPlaceholderState() {
        long postId = nextId();
        commandService.updateStatistics(stat(postId, 3L, 10L, 1_000L));
        Instant firstDeletedAt =
                postDocumentRepository.findById(postId).orElseThrow().getDeletedAt();

        commandService.updateStatistics(stat(postId, 7L, 20L, 2_000L));

        PostDocument doc = postDocumentRepository.findById(postId).orElseThrow();
        assertThat(doc.isTombstone()).isTrue();
        assertThat(doc.getPostVersion()).isEqualTo(-1L);
        assertThat(doc.getDeletedAt()).isEqualTo(firstDeletedAt);
        assertThat(doc.getLikeCount()).isEqualTo(7L);
        assertThat(doc.getViewCount()).isEqualTo(20L);
        assertThat(statSyncedAt(postId)).isEqualTo(2_000L);
    }

    @Test
    @DisplayName("CREATED가 오지 않은 자리 표시 문서는 삭제 표시 정리로 지워지고, 일반 문서는 남는다")
    void placeholderIsRemovedByTombstoneCleanup() {
        long placeholder = nextId();
        long active = nextId();
        commandService.updateStatistics(stat(placeholder, 3L, 10L, 1_000L));
        commandService.createDocument(created(active, "살아 있는 글", 0L));
        commandService.updateStatistics(stat(active, 1L, 1L, 1_000L));
        refresh();

        postDocumentRepository.deleteTombstonesBefore(Instant.now().plus(1, ChronoUnit.DAYS));

        assertThat(postDocumentRepository.findById(placeholder)).isEmpty();
        assertThat(postDocumentRepository.findById(active)).isPresent();
    }

    @Test
    @DisplayName("본문이 있는 문서에 통계를 반영해도 삭제 표시·버전은 바뀌지 않는다(버전 없는 예전 문서도 같다)")
    void statisticsOnExistingDocumentKeepDeletedAndVersion() {
        long versioned = nextId();
        long legacy = nextId();
        commandService.createDocument(created(versioned, "버전 있는 글", 0L));
        commandService.createDocument(created(legacy, "예전 글", null));

        commandService.updateStatistics(stat(versioned, 5L, 9L, 1_000L));
        commandService.updateStatistics(stat(legacy, 5L, 9L, 1_000L));

        PostDocument versionedDoc = postDocumentRepository.findById(versioned).orElseThrow();
        assertThat(versionedDoc.isTombstone()).isFalse();
        assertThat(versionedDoc.getDeletedAt()).isNull();
        assertThat(versionedDoc.getPostVersion()).isEqualTo(0L);
        assertThat(versionedDoc.getLikeCount()).isEqualTo(5L);

        PostDocument legacyDoc = postDocumentRepository.findById(legacy).orElseThrow();
        assertThat(legacyDoc.isTombstone()).isFalse();
        assertThat(legacyDoc.getDeletedAt()).isNull();
        assertThat(legacyDoc.getPostVersion()).isNull();
        assertThat(legacyDoc.getLikeCount()).isEqualTo(5L);
        assertThat(queryService.getPostDetail(legacy).postId()).isEqualTo(legacy);
    }

    /** 조회 8곳(검색·피드·자동완성·순위 집계·내 글·찜 목록·인기 글·상세)에서 보이는지 */
    private void assertVisible(long postId, long memberId, String category, String title, boolean visible) {
        List<Long> searched =
                queryService
                        .search(PostSearchRequest.SearchDTO.builder()
                                .category(category)
                                .size(50)
                                .build())
                        .content()
                        .stream()
                        .map(PostSearchResponse.SearchDTO::postId)
                        .toList();
        List<Long> feed = queryService
                .getFeed(PostSearchRequest.FeedDTO.builder()
                        .category(category)
                        .sort(PostSortType.LATEST)
                        .direction(Sort.Direction.DESC)
                        .size(50)
                        .build())
                .content()
                .stream()
                .map(PostSearchResponse.SearchDTO::postId)
                .toList();
        List<String> titles = queryService.autocomplete(title).titles();
        List<String> categories =
                postDocumentRepository.aggregateTopPopular(PostDocument.Fields.CATEGORY, 1000).stream()
                        .map(RankingResult::name)
                        .toList();
        List<Long> mine = queryService.getMyPosts(memberId, PageRequest.of(0, 50)).content().stream()
                .map(PostSearchResponse.MyDTO::postId)
                .toList();
        List<Long> liked = postDocumentRepository.findAllByIdIn(List.of(postId)).stream()
                .map(PostDocument::getId)
                .toList();
        List<Long> ranked = postDocumentRepository
                .findAllExcludingDeleted(PageRequest.of(0, 10_000))
                .map(PostDocument::getId)
                .getContent();

        if (visible) {
            assertThat(searched).containsExactly(postId);
            assertThat(feed).containsExactly(postId);
            assertThat(titles).contains(title);
            assertThat(categories).contains(category);
            assertThat(mine).containsExactly(postId);
            assertThat(liked).containsExactly(postId);
            assertThat(ranked).contains(postId);
            assertThat(queryService.getPostDetail(postId).postId()).isEqualTo(postId);
        } else {
            assertThat(searched).isEmpty();
            assertThat(feed).isEmpty();
            assertThat(titles).doesNotContain(title);
            assertThat(categories).doesNotContain(category);
            assertThat(mine).isEmpty();
            assertThat(liked).isEmpty();
            assertThat(ranked).doesNotContain(postId);
            assertThatThrownBy(() -> queryService.getPostDetail(postId)).isInstanceOf(SearchServiceException.class);
        }
    }

    private static PostSearchMessage.StatUpdatedDTO stat(long postId, long likeCount, long viewCount, long syncedAt) {
        return new PostSearchMessage.StatUpdatedDTO(postId, 4.0, 2L, likeCount, viewCount, syncedAt);
    }

    // statSyncedAt은 PostDocument에 없는 필드라 원본 문서에서 읽는다
    @SuppressWarnings("unchecked")
    private Long statSyncedAt(long postId) {
        try {
            Map<String, Object> source = client.get(g -> g.index(postIndexName).id(String.valueOf(postId)), Map.class)
                    .source();
            Object value = source == null ? null : source.get("statSyncedAt");
            return value == null ? null : ((Number) value).longValue();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private void refresh() {
        operations.indexOps(PostDocument.class).refresh();
    }

    private static PostSearchMessage.CreatedDTO created(long postId, String title, Long version) {
        return created(postId, 1L, "FOOD", title, version);
    }

    private static PostSearchMessage.CreatedDTO created(
            long postId, long memberId, String category, String title, Long version) {
        return new PostSearchMessage.CreatedDTO(
                postId,
                memberId,
                title,
                "본문",
                category,
                "GYEONGGI",
                "황리단길",
                "경상북도 경주시",
                READABLE_TIMESTAMP,
                List.of(new PostSearchMessage.ImageInfo("posts/" + memberId + "/a.png", 1)),
                version);
    }

    private static PostSearchMessage.UpdatedDTO updated(long postId, String title, long version) {
        return new PostSearchMessage.UpdatedDTO(
                postId,
                1L,
                title,
                "수정 본문",
                "FOOD",
                "GYEONGGI",
                "황리단길",
                "경상북도 경주시",
                READABLE_TIMESTAMP,
                READABLE_TIMESTAMP,
                List.of(new PostSearchMessage.ImageInfo("posts/1/b.png", 1)),
                version);
    }

    private static PostSearchMessage.DeletedDTO deleted(long postId, long version) {
        return new PostSearchMessage.DeletedDTO(postId, version, READABLE_TIMESTAMP);
    }
}
