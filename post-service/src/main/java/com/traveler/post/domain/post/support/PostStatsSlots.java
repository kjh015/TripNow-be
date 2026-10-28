package com.traveler.post.domain.post.support;

import com.traveler.post.domain.post.dto.result.PostStatsSummary;
import com.traveler.post.domain.post.entity.PostStats;
import com.traveler.post.domain.post.repository.PostStatsRepository;
import com.traveler.post.global.exception.PostServiceException;
import com.traveler.post.global.exception.code.PostServiceErrorCode;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 게시글 통계의 슬롯 묶음(게시글당 {@link PostStats#SLOT_COUNT}개의 행). 좋아요·댓글·관리자·통계 동기화가 함께 쓴다. 갱신할 슬롯을
 * 골라 잠그고, 여러 슬롯을 합쳐 게시글 통계로 읽는다.
 *
 * <p>값을 바꾸는 규칙은 {@link PostStats}의 엔티티 메서드에 있다.
 */
@Component
@RequiredArgsConstructor
public class PostStatsSlots {
    private final PostStatsRepository postStatsRepository;

    /**
     * 무작위 슬롯 하나를 잠그고 읽는다. 같은 게시글의 요청이 한 행에 몰리지 않게 한다. 잠긴 행은 커밋까지 풀리지 않으므로 좋아요·댓글
     * 처리의 마지막에 호출한다. 슬롯이 없으면 게시글이 없는 것이므로 POST_NOT_FOUND(앞선 쓰기도 되돌린다).
     */
    public PostStats getRandomForUpdate(Long postId) {
        int slot = ThreadLocalRandom.current().nextInt(PostStats.SLOT_COUNT);
        return postStatsRepository
                .findSlotForUpdate(postId, slot)
                .orElseThrow(() -> new PostServiceException(PostServiceErrorCode.POST_NOT_FOUND));
    }

    /** 게시글 ID별 통계(슬롯 합계). 통계 행이 없는 게시글은 0 통계로 채운다 */
    public Map<Long, PostStatsSummary> summarizeByPostId(Collection<Long> postIds) {
        Map<Long, PostStatsSummary> summaries = postStatsRepository.summarizeAll(postIds).stream()
                .collect(Collectors.toMap(PostStatsSummary::postId, Function.identity()));
        postIds.forEach(postId -> summaries.putIfAbsent(postId, PostStatsSummary.empty(postId)));
        return summaries;
    }

    public PostStatsSummary summarize(Long postId) {
        return summarizeByPostId(List.of(postId)).get(postId);
    }
}
