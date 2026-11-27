package com.traveler.search.domain.post.repository;

import com.traveler.search.domain.post.document.PostDocument;
import com.traveler.search.domain.post.dto.message.PostSearchMessage;
import com.traveler.search.domain.post.dto.request.PostSearchRequest;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.SearchHit;

public interface PostDocumentRepositoryCustom {

    record RankingResult(String name, Double score) {
        public static RankingResult of(String name, Double score) {
            return new RankingResult(name, score);
        }
    }

    List<RankingResult> aggregateTopPopular(String field, int topN);

    Page<PostDocument> search(PostSearchRequest.SearchDTO request, Pageable pageable);

    List<SearchHit<PostDocument>> searchFeed(PostSearchRequest.FeedDTO dto, List<Object> searchAfter, int limit);

    List<String> findAutocompleteTitles(String keyword);

    /** 내 게시글 조회. 삭제 표시(tombstone) 문서는 제외한다. */
    Page<PostDocument> findByMemberId(Long memberId, Pageable pageable);

    /** 찜한 게시글 조회. 삭제 표시(tombstone) 문서는 제외한다. */
    List<PostDocument> findAllByIdIn(List<Long> postIds);

    /** 인기 게시글 순위 조회. 삭제 표시(tombstone) 문서는 제외한다. */
    Page<PostDocument> findAllExcludingDeleted(Pageable pageable);

    /**
     * CREATED·UPDATED 이벤트 처리.
     * 메시지의 본문을 문서의 postVersion보다 높은 버전인 경우에만 반영한다. 대상 문서가 없으면 새로 생성한다.
     */
    void upsertContent(PostSearchMessage.ContentSnapshot snapshot);

    /**
     * DELETED 이벤트 처리.
     * 메시지의 버전이 문서의 postVersion보다 높은 경우에만 삭제 표시(tombstone)한다. 대상 문서가 없으면 삭제 표시 문서를 생성한다.
     */
    void markDeleted(PostSearchMessage.DeletedDTO dto);

    /** 삭제 시각(deletedAt)이 threshold 이전인 tombstone 문서를 삭제하고, 삭제 건수를 반환한다. */
    long deleteTombstonesBefore(Instant threshold);

    // postVersion 도입 이전 메시지와의 호환을 위한 메서드.
    void updatePartial(PostSearchMessage.UpdatedDTO dto);

    /**
     * STAT_UPDATED 이벤트 처리.
     * 메시지의 statSyncedAt이 문서의 값보다 최신인 경우에만 통계를 반영한다.
     * 대상 문서가 없으면 일반 조회에 노출되지 않는 자리 표시 문서를 생성한다.
     * 자리 표시 문서는 삭제 표시 상태이며 postVersion은 -1로 설정한다.
     */
    void updateStatistics(PostSearchMessage.StatUpdateDoc doc);
}
