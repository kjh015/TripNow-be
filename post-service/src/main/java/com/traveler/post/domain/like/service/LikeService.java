package com.traveler.post.domain.like.service;

import com.traveler.post.domain.like.dto.request.LikeRequest;
import com.traveler.post.domain.like.entity.Like;
import com.traveler.post.domain.like.mapper.LikeMapper;
import com.traveler.post.domain.like.repository.LikeRepository;
import com.traveler.post.domain.post.entity.Post;
import com.traveler.post.domain.post.repository.PostRepository;
import com.traveler.post.global.exception.PostServiceException;
import com.traveler.post.global.exception.code.PostServiceErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class LikeService {
    private final LikeRepository likeRepository;
    private final PostRepository postRepository;
    private final LikeMapper likeMapper;
    private final ApplicationEventPublisher eventPublisher;

    public void addLike(LikeRequest.AddDTO dto, Long memberId) {
        Post post = postRepository
                .findByIdWithLock(dto.postId())
                .orElseThrow(() -> new PostServiceException(PostServiceErrorCode.POST_NOT_FOUND));

        // 게시글 행 비관적 락으로 같은 게시글 요청이 직렬화되므로 선검사만으로 중복을 막는다.
        // 유니크 제약 위반을 catch해 흡수하면 롤백 전용이 된 트랜잭션을 이어가게 되므로 잡지 않는다.
        if (likeRepository.existsByPostIdAndMemberId(dto.postId(), memberId)) {
            return;
        }

        Like savedLike = likeRepository.save(likeMapper.toAddEntity(post, memberId));
        post.addLike();

        eventPublisher.publishEvent(likeMapper.toAddedEvent(savedLike, post));
    }

    public void removeLike(Long postId, Long memberId) {
        Post post = postRepository
                .findByIdWithLock(postId)
                .orElseThrow(() -> new PostServiceException(PostServiceErrorCode.POST_NOT_FOUND));

        likeRepository.findByPostIdAndMemberId(postId, memberId).ifPresent(like -> {
            likeRepository.delete(like);
            post.removeLike();

            eventPublisher.publishEvent(likeMapper.toRemovedEvent(like, post));
        });
    }
}
