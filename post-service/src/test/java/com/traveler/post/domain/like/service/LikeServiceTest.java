package com.traveler.post.domain.like.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.traveler.post.domain.like.dto.event.LikeEvent;
import com.traveler.post.domain.like.dto.request.LikeRequest;
import com.traveler.post.domain.like.entity.Like;
import com.traveler.post.domain.like.mapper.LikeMapper;
import com.traveler.post.domain.like.repository.LikeRepository;
import com.traveler.post.domain.post.entity.Post;
import com.traveler.post.domain.post.repository.PostRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

class LikeServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long POST_ID = 10L;

    private final LikeRepository likeRepository = mock(LikeRepository.class);
    private final PostRepository postRepository = mock(PostRepository.class);
    private final LikeMapper likeMapper = mock(LikeMapper.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

    private final LikeService likeService = new LikeService(likeRepository, postRepository, likeMapper, eventPublisher);

    private Post post;

    @BeforeEach
    void setUp() {
        post = Post.builder().id(POST_ID).memberId(2L).build();
        given(postRepository.findByIdWithLock(POST_ID)).willReturn(Optional.of(post));
    }

    @Test
    @DisplayName("이미 좋아요한 게시글이면 저장·카운터·이벤트 없이 성공한다")
    void addLikeIsIdempotent() {
        given(likeRepository.existsByPostIdAndMemberId(POST_ID, MEMBER_ID)).willReturn(true);

        likeService.addLike(new LikeRequest.AddDTO(POST_ID), MEMBER_ID);

        verify(likeRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
        assertThat(post.getLikeCount()).isZero();
    }

    @Test
    @DisplayName("처음 좋아요하면 저장하고 카운터를 올린 뒤 이벤트를 발행한다")
    void addLikeSaves() {
        Like like = Like.builder().memberId(MEMBER_ID).post(post).build();
        LikeEvent.Added event = new LikeEvent.Added(null, null);
        given(likeRepository.existsByPostIdAndMemberId(POST_ID, MEMBER_ID)).willReturn(false);
        given(likeMapper.toAddEntity(post, MEMBER_ID)).willReturn(like);
        given(likeRepository.save(like)).willReturn(like);
        given(likeMapper.toAddedEvent(like, post)).willReturn(event);

        likeService.addLike(new LikeRequest.AddDTO(POST_ID), MEMBER_ID);

        assertThat(post.getLikeCount()).isEqualTo(1);
        verify(eventPublisher).publishEvent(event);
    }

    @Test
    @DisplayName("유니크 제약 위반은 흡수하지 않고 그대로 전파한다")
    void addLikeDoesNotSwallowConstraintViolation() {
        given(likeRepository.existsByPostIdAndMemberId(POST_ID, MEMBER_ID)).willReturn(false);
        given(likeRepository.save(any())).willThrow(new DataIntegrityViolationException("uk_like_member_post"));

        assertThatThrownBy(() -> likeService.addLike(new LikeRequest.AddDTO(POST_ID), MEMBER_ID))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(post.getLikeCount()).isZero();
        verify(eventPublisher, never()).publishEvent(any());
    }
}
