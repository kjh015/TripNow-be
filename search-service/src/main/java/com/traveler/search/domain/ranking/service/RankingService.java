package com.traveler.search.domain.ranking.service;

import com.traveler.search.domain.post.document.PostDocument;
import com.traveler.search.domain.post.repository.PostDocumentRepository;
import com.traveler.search.domain.ranking.dto.response.RankingResponse;
import com.traveler.search.domain.ranking.mapper.RankingMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RankingService {
    private final PostDocumentRepository postDocumentRepository;
    private final RankingMapper rankingMapper;

    public RankingResponse.LiveDTO fetchLiveRanking() {
        List<RankingResponse.PostRank> posts = getTopPosts(5);
        List<RankingResponse.CategoryRank> categories = getTopCategories(5);
        List<RankingResponse.RegionRank> regions = getTopRegions(5);

        return rankingMapper.toLiveDTO(posts, categories, regions);
    }

    public List<RankingResponse.PostRank> getTopPosts(int topN) {
        Pageable pageable =
                PageRequest.of(0, topN, Sort.by(Sort.Direction.DESC, PostDocument.Fields.POPULARITY_SCORE_VALUE));
        return postDocumentRepository.findAllExcludingDeleted(pageable).stream()
                .map(rankingMapper::toPostRank)
                .toList();
    }

    public List<RankingResponse.CategoryRank> getTopCategories(int topN) {
        return rankingMapper.toCategoryRankList(
                postDocumentRepository.aggregateTopPopular(PostDocument.Fields.CATEGORY, topN));
    }

    public List<RankingResponse.RegionRank> getTopRegions(int topN) {
        return rankingMapper.toRegionRankList(
                postDocumentRepository.aggregateTopPopular(PostDocument.Fields.REGION, topN));
    }
}
