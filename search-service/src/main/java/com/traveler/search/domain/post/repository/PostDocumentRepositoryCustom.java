package com.traveler.search.domain.post.repository;

import com.traveler.search.domain.post.document.PostDocument;
import com.traveler.search.domain.post.dto.message.PostSearchMessage;
import com.traveler.search.domain.post.dto.request.PostSearchRequest;
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

    void updatePartial(PostSearchMessage.UpdatedDTO dto);

    void updateStatistics(PostSearchMessage.StatUpdateDoc doc);
}
