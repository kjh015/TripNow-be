package com.traveler.post.domain.post.dto.result;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 통계 동기화 한 번에 처리할 묶음. 조회수 증가분(게시글 ID → 증가량)과 좋아요·댓글 통계가 바뀐 게시글 ID.
 */
public record PostStatsSyncBatch(Map<Long, Long> viewCounts, Set<Long> updatedPostIds) {

    /** 검색 서버로 통계를 보낼 게시글: 조회수가 늘었거나 통계가 바뀐 게시글 */
    public Set<Long> postIds() {
        Set<Long> postIds = new LinkedHashSet<>(updatedPostIds);
        postIds.addAll(viewCounts.keySet());
        return postIds;
    }
}
