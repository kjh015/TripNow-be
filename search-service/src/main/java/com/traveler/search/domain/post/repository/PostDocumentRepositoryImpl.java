package com.traveler.search.domain.post.repository;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHitSupport;
import org.springframework.data.elasticsearch.core.SearchHits;
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

    @Override
    public Page<PostDocument> search(PostSearchRequest.SearchDTO dto, Pageable pageable) {
        NativeQueryBuilder queryBuilder = new NativeQueryBuilder().withPageable(pageable);

        // 1. Must & Filter Query 구성
        queryBuilder.withQuery(q -> q.bool(b -> {
            buildMustClause(b, dto.keyword());
            buildFilterClause(b, dto.category(), dto.region());
            if (dto.sort() == PostSortType.ACCURACY) {
                b.should(s -> s.rankFeature(rf -> rf.field(PostDocument.Fields.POPULARITY_SCORE)
                        .boost(2.0f)
                        .saturation(sa -> sa)));
            }
            return b;
        }));

        SearchHits<PostDocument> searchHits =
                operations.search(queryBuilder.build(), PostDocument.class, IndexCoordinates.of(postIndexName));
        return SearchHitSupport.searchPageFor(searchHits, pageable).map(SearchHit::getContent);
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
     * 문서가 없으면 404
     */
    private static final String UPDATE_STATISTICS_SCRIPT =
            """
            if (ctx._source.statSyncedAt != null && ctx._source.statSyncedAt >= params.statSyncedAt) {
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

    @SneakyThrows
    @Override
    public void updateStatistics(PostSearchMessage.StatUpdateDoc doc) {
        Map<String, JsonData> params = Map.of(
                "starAvg", JsonData.of(doc.starAvg()),
                "commentCount", JsonData.of(doc.commentCount()),
                "likeCount", JsonData.of(doc.likeCount()),
                "viewCount", JsonData.of(doc.viewCount()),
                "popularityScore", JsonData.of(doc.popularityScore()),
                "popularityScoreValue", JsonData.of(doc.popularityScoreValue()),
                "statSyncedAt", JsonData.of(doc.statSyncedAt()));
        client.update(
                u -> u.index(postIndexName)
                        .id(String.valueOf(doc.postId()))
                        .script(s -> s.lang("painless")
                                .source(UPDATE_STATISTICS_SCRIPT)
                                .params(params))
                        .retryOnConflict(3),
                PostSearchMessage.StatUpdateDoc.class);
    }

    @SneakyThrows
    @Override
    public List<RankingResult> aggregateTopPopular(String field, int topN) {
        // 1. NativeQueryBuilder를 사용하여 집계 쿼리 생성
        NativeQuery query = NativeQuery.builder()
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
