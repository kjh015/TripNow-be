package com.traveler.post.domain.like.service;

import com.traveler.post.domain.like.dto.request.LikeRequest;
import com.traveler.post.domain.like.dto.response.LikeResponse;
import com.traveler.post.domain.like.entity.Like;
import com.traveler.post.domain.like.mapper.LikeMapper;
import com.traveler.post.domain.like.repository.LikeRepository;
import com.traveler.post.domain.post.repository.PostRepository;
import com.traveler.post.domain.post.support.PostStatsSlots;
import com.traveler.post.global.exception.PostServiceException;
import com.traveler.post.global.exception.code.PostServiceErrorCode;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 좋아요 추가·취소. 게시글 행을 잠그지 않고, 좋아요 행을 먼저 쓴 뒤 마지막에 통계 슬롯(post_stats) 하나를 잠가 갱신한다.
 * 응답에는 처리 직후의 좋아요 수(슬롯 합계)를 싣는다.
 *
 * <p>게시글 존재 확인은 잠그지 않는 조회라, 삭제와 거의 동시에 들어온 좋아요는 삭제된 게시글에 붙을 수 있다. 삭제된 게시글은 검색에
 * 보이지 않고 영구 삭제 때 좋아요도 함께 지워지므로 허용한다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class LikeService {
    private final LikeRepository likeRepository;
    private final PostRepository postRepository;
    private final PostStatsSlots postStatsSlots;
    private final LikeMapper likeMapper;
    private final ApplicationEventPublisher eventPublisher;

    public LikeResponse.StatsDTO addLike(LikeRequest.AddDTO dto, Long memberId) {
        Long postId = dto.postId();
        validatePostExists(postId);

        // 이미 좋아요한 게시글이면 0행. 같은 회원의 동시 요청도 유니크 제약으로 한 건만 들어간다
        if (likeRepository.insertIfAbsent(postId, memberId, Instant.now()) == 0) {
            return likeMapper.toStatsDTO(postStatsSlots.summarize(postId));
        }

        Like like = likeRepository
                .findByPostIdAndMemberId(postId, memberId)
                .orElseThrow(() -> new IllegalStateException("방금 넣은 좋아요가 없다 postId=" + postId));
        postStatsSlots.getRandomForUpdate(postId).addLike();

        eventPublisher.publishEvent(likeMapper.toAddedEvent(like));

        return likeMapper.toStatsDTO(postStatsSlots.summarize(postId));
    }

    public LikeResponse.StatsDTO removeLike(Long postId, Long memberId) {
        validatePostExists(postId);

        likeRepository.findByPostIdAndMemberId(postId, memberId).ifPresent(like -> {
            // 같은 좋아요를 동시에 취소하면 실제로 지운 요청만 통계를 내린다
            if (likeRepository.deleteIfPresent(like.getId()) == 0) {
                return;
            }
            postStatsSlots.getRandomForUpdate(postId).removeLike();

            eventPublisher.publishEvent(likeMapper.toRemovedEvent(like));
        });

        return likeMapper.toStatsDTO(postStatsSlots.summarize(postId));
    }

    // 소프트 삭제된 게시글은 @SQLRestriction으로 걸러져 없는 게시글로 본다
    private void validatePostExists(Long postId) {
        if (!postRepository.existsById(postId)) {
            throw new PostServiceException(PostServiceErrorCode.POST_NOT_FOUND);
        }
    }
}
