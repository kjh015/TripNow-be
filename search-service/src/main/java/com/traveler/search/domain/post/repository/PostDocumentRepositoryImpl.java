package com.traveler.search.domain.post.repository;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.util.NamedValue;
import com.traveler.search.domain.post.document.PostDocument;
import com.traveler.search.domain.post.dto.message.PostSearchMessage;
import com.traveler.search.domain.post.dto.request.PostSearchRequest;
import com.traveler.search.domain.post.enums.PostSortType;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHitSupport;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.convert.ElasticsearchDateConverter;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.FetchSourceFilter;
import org.springframework.util.StringUtils;

@RequiredArgsConstructor
@Slf4j
public class PostDocumentRepositoryImpl implements PostDocumentRepositoryCustom {
    private final ElasticsearchOperations operations;
    private final ElasticsearchClient client;

    @Value("${app.elasticsearch.indices.post}")
    private String postIndexName;

    private static final int BOOST_TITLE = 5;
    private static final int BOOST_TITLE_NGRAM = 3;
    private static final int BOOST_TITLE_CHOSUNG = 2;
    private static final String TOP_TAGS_AGG = "top_tags";
    private static final String TOTAL_SCORE_AGG = "total_score";

    // 스크립트로 넣는 날짜는 매핑·PostDocument와 같은 형식(소수 6자리)으로 보낸다. 다른 자릿수는 Spring Data ES가 다시 읽지 못한다
    private static final ElasticsearchDateConverter DATE_CONVERTER =
            ElasticsearchDateConverter.of(DateFormat.strict_date_optional_time_nanos);
    // scripted upsert는 문서가 없을 때 이 빈 문서로 스크립트를 실행한다
    private static final Map<String, Object> EMPTY_UPSERT = Map.of();

    @Override
    public Page<PostDocument> search(PostSearchRequest.SearchDTO dto, Pageable pageable) {
        NativeQueryBuilder queryBuilder = new NativeQueryBuilder().withPageable(pageable);

        // 1. Must & Filter Query 구성
        applySearchQuery(queryBuilder, dto.keyword(), dto.category(), dto.region(), dto.sort());

        SearchHits<PostDocument> searchHits =
                operations.search(queryBuilder.build(), PostDocument.class, IndexCoordinates.of(postIndexName));
        return SearchHitSupport.searchPageFor(searchHits, pageable).map(SearchHit::getContent);
    }

    @Override
    public List<SearchHit<PostDocument>> searchFeed(
            PostSearchRequest.FeedDTO dto, List<Object> searchAfter, int limit) {
        NativeQueryBuilder queryBuilder =
                new NativeQueryBuilder().withMaxResults(limit).withTrackTotalHits(false);

        applySearchQuery(queryBuilder, dto.keyword(), dto.category(), dto.region(), dto.sort());
        applyFeedSort(queryBuilder, dto.sort(), dto.direction().isAscending() ? SortOrder.Asc : SortOrder.Desc);
        if (searchAfter != null) {
            queryBuilder.withSearchAfter(searchAfter);
        }

        return operations
                .search(queryBuilder.build(), PostDocument.class, IndexCoordinates.of(postIndexName))
                .getSearchHits();
    }

    @Override
    public List<String> findAutocompleteTitles(String keyword) {
        NativeQueryBuilder queryBuilder = new NativeQueryBuilder();

        queryBuilder.withQuery(q -> q.bool(b -> {
            // 1. 일반 완성형 자동완성 (Edge-Ngram) - 높은 가중치
            b.should(s -> s.prefix(p -> p.field(PostDocument.Fields.TITLE_AUTOCOMPLETE)
                    .value(keyword)
                    .boost(5.0f)));

            // 2. 초성 자동완성 (Ngram) - 낮은 가중치
            b.should(s -> s.match(m ->
                    m.field(PostDocument.Fields.TITLE_CHOSUNG).query(keyword).analyzer("chosung_ngram_analyzer")));

            // should만 있으면 하나 이상 맞아야 하지만, must_not을 더하면 기본값이 0이 되므로 직접 정한다
            b.minimumShouldMatch("1");
            excludeDeleted(b);
            return b;
        }));

        // 3. 성능 최적화: 필요한 필드(title)만 fetch, 10개 제한
        queryBuilder.withSourceFilter(new FetchSourceFilter(new String[] {PostDocument.Fields.TITLE}, null));
        queryBuilder.withMaxResults(10);

        SearchHits<PostDocument> searchHits =
                operations.search(queryBuilder.build(), PostDocument.class, IndexCoordinates.of(postIndexName));

        return searchHits.stream().map(hit -> hit.getContent().getTitle()).toList();
    }

    @Override
    public Page<PostDocument> findByMemberId(Long memberId, Pageable pageable) {
        NativeQuery query = new NativeQueryBuilder()
                .withQuery(q -> q.bool(b -> {
                    b.filter(f ->
                            f.term(t -> t.field(PostDocument.Fields.MEMBER_ID).value(memberId)));
                    excludeDeleted(b);
                    return b;
                }))
                .withPageable(pageable)
                .build();
        SearchHits<PostDocument> hits =
                operations.search(query, PostDocument.class, IndexCoordinates.of(postIndexName));
        return SearchHitSupport.searchPageFor(hits, pageable).map(SearchHit::getContent);
    }

    @Override
    public List<PostDocument> findAllByIdIn(List<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return List.of();
        }
        List<FieldValue> ids = postIds.stream().map(FieldValue::of).toList();
        NativeQuery query = new NativeQueryBuilder()
                .withQuery(q -> q.bool(b -> {
                    b.filter(f -> f.terms(t -> t.field(PostDocument.Fields.ID).terms(v -> v.value(ids))));
                    excludeDeleted(b);
                    return b;
                }))
                .withMaxResults(postIds.size())
                .build();
        return operations.search(query, PostDocument.class, IndexCoordinates.of(postIndexName)).stream()
                .map(SearchHit::getContent)
                .toList();
    }

    @Override
    public Page<PostDocument> findAllExcludingDeleted(Pageable pageable) {
        NativeQuery query = new NativeQueryBuilder()
                .withQuery(q -> q.bool(b -> {
                    excludeDeleted(b);
                    return b;
                }))
                .withPageable(pageable)
                .build();
        SearchHits<PostDocument> hits =
                operations.search(query, PostDocument.class, IndexCoordinates.of(postIndexName));
        return SearchHitSupport.searchPageFor(hits, pageable).map(SearchHit::getContent);
    }

    /**
     * 본문 반영(CREATED·UPDATED). 이미 같거나 큰 버전이 반영돼 있으면 아무것도 하지 않는다. 늦게 온 예전 버전·같은 메시지의 재전송이
     * 최신 본문을 덮어쓰거나 삭제 표시된 글을 되살리지 않는다. 통계는 statSyncedAt 스크립트(UPDATE_STATISTICS_SCRIPT)가 관리하므로
     * 통계가 없는 새 문서에만 기본값(기존 생성 문서와 같은 값)을 넣는다
     */
    private static final String UPSERT_CONTENT_SCRIPT =
            """
            if (ctx._source.postVersion != null && ctx._source.postVersion >= params.postVersion) {
                ctx.op = 'noop';
            } else {
                ctx._source.id = params.id;
                ctx._source.memberId = params.memberId;
                ctx._source.title = params.title;
                ctx._source.titleChosung = params.titleChosung;
                ctx._source.content = params.content;
                ctx._source.category = params.category;
                ctx._source.region = params.region;
                ctx._source.travelPlace = params.travelPlace;
                ctx._source.address = params.address;
                ctx._source.images = params.images;
                ctx._source.createdAt = params.createdAt;
                ctx._source.updatedAt = params.updatedAt;
                ctx._source.postVersion = params.postVersion;
                ctx._source.deleted = false;
                ctx._source.deletedAt = null;
                if (ctx._source.starAvg == null) { ctx._source.starAvg = 0.0; }
                if (ctx._source.viewCount == null) { ctx._source.viewCount = 0L; }
                if (ctx._source.likeCount == null) { ctx._source.likeCount = 0L; }
                if (ctx._source.commentCount == null) { ctx._source.commentCount = 0L; }
                if (ctx._source.popularityScoreValue == null) { ctx._source.popularityScoreValue = 0.0; }
            }
            """;

    /** 삭제 표시. 문서가 없어도 버전을 남겨야 늦게 온 생성 이벤트를 막을 수 있으므로 삭제 표시 문서를 만든다 */
    private static final String MARK_DELETED_SCRIPT =
            """
            if (ctx._source.postVersion != null && ctx._source.postVersion >= params.postVersion) {
                ctx.op = 'noop';
            } else {
                ctx._source.id = params.id;
                ctx._source.postVersion = params.postVersion;
                ctx._source.deleted = true;
                ctx._source.deletedAt = params.deletedAt;
            }
            """;

    @SneakyThrows
    @Override
    public void upsertContent(PostSearchMessage.ContentSnapshot snapshot) {
        Map<String, JsonData> params = new HashMap<>();
        params.put("id", JsonData.of(snapshot.postId()));
        putIfPresent(params, "memberId", snapshot.memberId());
        putIfPresent(params, "title", snapshot.title());
        putIfPresent(params, "titleChosung", snapshot.titleChosung());
        putIfPresent(params, "content", snapshot.content());
        putIfPresent(params, "category", snapshot.category());
        putIfPresent(params, "region", snapshot.region());
        putIfPresent(params, "travelPlace", snapshot.travelPlace());
        putIfPresent(params, "address", snapshot.address());
        params.put("images", JsonData.of(snapshot.images() == null ? List.of() : snapshot.images()));
        putIfPresent(params, "createdAt", formatDate(snapshot.createdAt()));
        putIfPresent(params, "updatedAt", formatDate(snapshot.updatedAt()));
        params.put("postVersion", JsonData.of(snapshot.version()));
        scriptedUpsert(snapshot.postId(), UPSERT_CONTENT_SCRIPT, params);
    }

    @SneakyThrows
    @Override
    public void markDeleted(PostSearchMessage.DeletedDTO dto) {
        Map<String, JsonData> params = new HashMap<>();
        params.put("id", JsonData.of(dto.postId()));
        params.put("postVersion", JsonData.of(dto.version()));
        // 버전 있는 삭제 메시지는 deletedAt을 싣는다. 없으면 정리 기준을 잃지 않게 받은 시각으로 둔다
        params.put("deletedAt", JsonData.of(formatDate(dto.deletedAt() != null ? dto.deletedAt() : Instant.now())));
        scriptedUpsert(dto.postId(), MARK_DELETED_SCRIPT, params);
    }

    @SneakyThrows
    private void scriptedUpsert(Long postId, String script, Map<String, JsonData> params) {
        client.update(
                u -> u.index(postIndexName)
                        .id(String.valueOf(postId))
                        .script(s -> s.lang("painless").source(script).params(params))
                        .scriptedUpsert(true)
                        .upsert(EMPTY_UPSERT)
                        .retryOnConflict(3),
                Map.class);
    }

    @SneakyThrows
    @Override
    public long deleteTombstonesBefore(Instant threshold) {
        String before = formatDate(threshold);
        Long deleted = client.deleteByQuery(d -> d.index(postIndexName)
                        .query(q -> q.bool(b -> b.filter(f -> f.term(t ->
                                        t.field(PostDocument.Fields.DELETED).value(true)))
                                .filter(f -> f.range(r -> r.date(dr ->
                                        dr.field(PostDocument.Fields.DELETED_AT).lt(before))))))
                        // 정리 중 통계 갱신 등으로 문서 버전이 바뀌어도 멈추지 않는다. 남은 문서는 다음 날 지운다
                        .conflicts(co.elastic.clients.elasticsearch._types.Conflicts.Proceed))
                .deleted();
        return deleted == null ? 0L : deleted;
    }

    // JsonData는 null을 직렬화하지 못한다. 값이 없으면 넣지 않고, 스크립트의 params.x가 null로 읽는다
    private static void putIfPresent(Map<String, JsonData> params, String name, Object value) {
        if (value != null) {
            params.put(name, JsonData.of(value));
        }
    }

    private static String formatDate(Instant instant) {
        return instant == null ? null : DATE_CONVERTER.format(instant);
    }

    @Override
    @SneakyThrows
    public void updatePartial(PostSearchMessage.UpdatedDTO dto) {
        client.update(
                u -> u.index(postIndexName)
                        .id(String.valueOf(dto.postId()))
                        .doc(dto)
                        .retryOnConflict(3),
                PostSearchMessage.UpdatedDTO.class);
    }

    /**
     * 문서에 저장된 statSyncedAt보다 새로운 통계만 반영한다. 재전송되거나 순서가 바뀌어 늦게 도착한 예전 통계가 최신 값을 덮어쓰지 않는다.
     * 문서가 없으면(본문 이벤트보다 통계가 먼저 온 경우) 통계를 버리지 않고, 버전 -1의 삭제 표시 문서로 받아 둔다. 삭제 표시라 모든 조회에서
     * 빠지고, 뒤에 오는 CREATED(v0 이상)가 본문을 채우면서 통계를 그대로 이어받는다. CREATED가 끝내 오지 않으면 삭제 표시 정리가 지운다
     */
    private static final String UPDATE_STATISTICS_SCRIPT =
            """
            boolean created = ctx._source.isEmpty();
            if (created) {
                ctx._source.id = params.id;
                ctx._source.postVersion = -1L;
                ctx._source.deleted = true;
                ctx._source.deletedAt = params.deletedAt;
            }
            if (!created && ctx._source.statSyncedAt != null && ctx._source.statSyncedAt >= params.statSyncedAt) {
                ctx.op = 'noop';
            } else {
                ctx._source.starAvg = params.starAvg;
                ctx._source.commentCount = params.commentCount;
                ctx._source.likeCount = params.likeCount;
                ctx._source.viewCount = params.viewCount;
                ctx._source.popularityScore = params.popularityScore;
                ctx._source.popularityScoreValue = params.popularityScoreValue;
                ctx._source.statSyncedAt = params.statSyncedAt;
            }
            """;

    @Override
    public void updateStatistics(PostSearchMessage.StatUpdateDoc doc) {
        Map<String, JsonData> params = new HashMap<>();
        params.put("id", JsonData.of(doc.postId()));
        // 문서가 없을 때만 쓰인다. 자리 표시 문서도 삭제 표시 정리 기준(deletedAt)을 갖게 한다
        params.put("deletedAt", JsonData.of(formatDate(Instant.now())));
        putIfPresent(params, "starAvg", doc.starAvg());
        putIfPresent(params, "commentCount", doc.commentCount());
        putIfPresent(params, "likeCount", doc.likeCount());
        putIfPresent(params, "viewCount", doc.viewCount());
        putIfPresent(params, "popularityScore", doc.popularityScore());
        putIfPresent(params, "popularityScoreValue", doc.popularityScoreValue());
        putIfPresent(params, "statSyncedAt", doc.statSyncedAt());
        scriptedUpsert(doc.postId(), UPDATE_STATISTICS_SCRIPT, params);
    }

    @SneakyThrows
    @Override
    public List<RankingResult> aggregateTopPopular(String field, int topN) {
        // 1. NativeQueryBuilder를 사용하여 집계 쿼리 생성
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> {
                    excludeDeleted(b);
                    return b;
                }))
                .withMaxResults(0) // 실제 문서는 필요 없음 (size: 0)
                .withAggregation(TOP_TAGS_AGG, Aggregation.of(a -> a.terms(t -> t.field(field)
                                .size(topN)
                                .order(List.of(NamedValue.of(TOTAL_SCORE_AGG, SortOrder.Desc)))
                                .shardSize(topN * 10))
                        .aggregations(
                                TOTAL_SCORE_AGG,
                                sub -> sub.sum(sum -> sum.field(PostDocument.Fields.POPULARITY_SCORE_VALUE)))))
                .build();

        // 2. 실행
        SearchHits<PostDocument> searchHits = operations.search(query, PostDocument.class);

        // 3. 결과 파싱
        ElasticsearchAggregations aggregations = (ElasticsearchAggregations) searchHits.getAggregations();
        if (aggregations == null) return List.of();

        return Objects.requireNonNull(aggregations.get(TOP_TAGS_AGG))
                .aggregation()
                .getAggregate()
                .sterms() // String Terms 집계
                .buckets()
                .array()
                .stream()
                .map(bucket -> RankingResult.of(
                        bucket.key().stringValue(),
                        bucket.aggregations().get(TOTAL_SCORE_AGG).sum().value()))
                .toList();
    }

    private void applySearchQuery(
            NativeQueryBuilder queryBuilder, String keyword, String category, String region, PostSortType sort) {
        queryBuilder.withQuery(q -> q.bool(b -> {
            buildMustClause(b, keyword);
            buildFilterClause(b, category, region);
            excludeDeleted(b);
            if (sort == PostSortType.ACCURACY) {
                b.should(s -> s.rankFeature(rf -> rf.field(PostDocument.Fields.POPULARITY_SCORE)
                        .boost(2.0f)
                        .saturation(sa -> sa)));
            }
            return b;
        }));
    }

    // search_after는 정렬 값으로 위치를 찾으므로, 값이 같은 글이 있으면 글이 빠질 수 있다. 따라서 게시글 ID를 정렬 마지막에 둔다.
    private void applyFeedSort(NativeQueryBuilder queryBuilder, PostSortType sort, SortOrder order) {
        if (sort == PostSortType.ACCURACY) {
            queryBuilder.withSort(s -> s.score(sc -> sc.order(SortOrder.Desc)));
        } else {
            if (sort != PostSortType.LATEST) {
                // 필드가 없는 문서의 정렬 값은 0으로 고정
                queryBuilder.withSort(
                        s -> s.field(f -> f.field(sort.getField()).order(order).missing(FieldValue.of(0))));
            }
            queryBuilder.withSort(
                    s -> s.field(f -> f.field(PostDocument.Fields.CREATED_AT).order(order)));
        }
        queryBuilder.withSort(s -> s.field(f -> f.field(PostDocument.Fields.ID).order(order)));
    }

    // 삭제 표시 문서를 제외한다. deleted 필드가 없는 예전 문서도 나오도록 must_not으로 건다
    private static void excludeDeleted(BoolQuery.Builder b) {
        b.mustNot(m -> m.term(t -> t.field(PostDocument.Fields.DELETED).value(true)));
    }

    private void buildMustClause(BoolQuery.Builder b, String keyword) {
        if (StringUtils.hasText(keyword)) {
            b.must(m -> m.multiMatch(mm -> mm.fields(
                            boost(PostDocument.Fields.TITLE, BOOST_TITLE),
                            boost(PostDocument.Fields.TITLE_NGRAM, BOOST_TITLE_NGRAM),
                            boost(PostDocument.Fields.TITLE_CHOSUNG, BOOST_TITLE_CHOSUNG),
                            PostDocument.Fields.CONTENT,
                            PostDocument.Fields.TRAVEL_PLACE,
                            PostDocument.Fields.ADDRESS)
                    .query(keyword)
                    .type(TextQueryType.MostFields)));
        } else {
            // 키워드가 없을 경우 match_all 추가
            b.must(m -> m.matchAll(ma -> ma));
        }
    }

    private void buildFilterClause(BoolQuery.Builder b, String category, String region) {
        if (StringUtils.hasText(category)) {
            b.filter(f -> f.term(t -> t.field(PostDocument.Fields.CATEGORY).value(category)));
        }
        if (StringUtils.hasText(region)) {
            b.filter(f -> f.term(t -> t.field(PostDocument.Fields.REGION).value(region)));
        }
    }

    private String boost(String field, int val) {
        return field + "^" + val;
    }
}
