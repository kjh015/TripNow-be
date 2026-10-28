package com.traveler.post.domain.like.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.traveler.post.domain.like.dto.event.LikeEvent;
import com.traveler.post.domain.like.dto.request.LikeRequest;
import com.traveler.post.domain.like.dto.response.LikeResponse;
import com.traveler.post.domain.like.entity.Like;
import com.traveler.post.domain.like.mapper.LikeMapper;
import com.traveler.post.domain.like.repository.LikeRepository;
import com.traveler.post.domain.post.dto.result.PostStatsSummary;
import com.traveler.post.domain.post.entity.PostStats;
import com.traveler.post.domain.post.repository.PostRepository;
import com.traveler.post.domain.post.support.PostStatsSlots;
import com.traveler.post.global.exception.PostServiceException;
import com.traveler.post.global.exception.code.PostServiceErrorCode;
import com.traveler.post.testsupport.fixture.PostFixture;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class LikeServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long POST_ID = 10L;

    @Mock
    private LikeRepository likeRepository;

    @Mock
    private PostRepository postRepository;

    @Mock
    private PostStatsSlots postStatsSlots;

    @Mock
    private LikeMapper likeMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private LikeService likeService;

    @Test
    @DisplayName("없거나 삭제된 게시글이면 좋아요를 넣지 않고 POST_NOT_FOUND")
    void addLikeOnMissingPost() {
        given(postRepository.existsById(POST_ID)).willReturn(false);

        assertThatThrownBy(() -> likeService.addLike(new LikeRequest.AddDTO(POST_ID), MEMBER_ID))
                .isInstanceOf(PostServiceException.class)
                .extracting("code")
                .isEqualTo(PostServiceErrorCode.POST_NOT_FOUND);

        verify(likeRepository, never()).insertIfAbsent(anyLong(), anyLong(), any());
        verify(postStatsSlots, never()).getRandomForUpdate(anyLong());
    }

    @Test
    @DisplayName("이미 좋아요한 게시글이면(삽입 0행) 통계·이벤트 없이 현재 좋아요 수를 돌려준다")
    void addLikeIsIdempotent() {
        given(postRepository.existsById(POST_ID)).willReturn(true);
        given(likeRepository.insertIfAbsent(eq(POST_ID), eq(MEMBER_ID), any())).willReturn(0);
        PostStatsSummary stats = new PostStatsSummary(POST_ID, 7L, 0L, 0L);
        LikeResponse.StatsDTO response = new LikeResponse.StatsDTO(POST_ID, 7L);
        given(postStatsSlots.summarize(POST_ID)).willReturn(stats);
        given(likeMapper.toStatsDTO(stats)).willReturn(response);

        LikeResponse.StatsDTO result = likeService.addLike(new LikeRequest.AddDTO(POST_ID), MEMBER_ID);

        assertThat(result).isEqualTo(response);
        verify(postStatsSlots, never()).getRandomForUpdate(anyLong());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("처음 좋아요하면 좋아요를 넣은 뒤 슬롯 하나를 잠가 올리고, 이벤트(통계 변경 포함)를 한 번 발행한 뒤 좋아요 수를 돌려준다")
    void addLikeUpdatesSlotLast() {
        Like like = Like.builder()
                .memberId(MEMBER_ID)
                .post(PostFixture.post(POST_ID, 2L))
                .build();
        PostStats slot = PostStats.createSlots(PostFixture.post(POST_ID, 2L)).getFirst();
        LikeEvent.Added event = new LikeEvent.Added(null);
        given(postRepository.existsById(POST_ID)).willReturn(true);
        given(likeRepository.insertIfAbsent(eq(POST_ID), eq(MEMBER_ID), any())).willReturn(1);
        given(likeRepository.findByPostIdAndMemberId(POST_ID, MEMBER_ID)).willReturn(Optional.of(like));
        given(postStatsSlots.getRandomForUpdate(POST_ID)).willReturn(slot);
        given(likeMapper.toAddedEvent(like)).willReturn(event);
        PostStatsSummary stats = new PostStatsSummary(POST_ID, 8L, 0L, 0L);
        LikeResponse.StatsDTO response = new LikeResponse.StatsDTO(POST_ID, 8L);
        given(postStatsSlots.summarize(POST_ID)).willReturn(stats);
        given(likeMapper.toStatsDTO(stats)).willReturn(response);

        LikeResponse.StatsDTO result = likeService.addLike(new LikeRequest.AddDTO(POST_ID), MEMBER_ID);

        InOrder order = inOrder(likeRepository, postStatsSlots, eventPublisher);
        order.verify(likeRepository).insertIfAbsent(eq(POST_ID), eq(MEMBER_ID), any());
        order.verify(postStatsSlots).getRandomForUpdate(POST_ID);
        order.verify(eventPublisher).publishEvent(event);
        verify(eventPublisher, times(1)).publishEvent(any(Object.class));
        assertThat(slot.getLikeCount()).isEqualTo(1);
        assertThat(result).isEqualTo(response);
    }

    @Test
    @DisplayName("좋아요하지 않은 게시글을 취소하면 통계·이벤트 없이 현재 좋아요 수를 돌려준다")
    void removeLikeWithoutLike() {
        given(postRepository.existsById(POST_ID)).willReturn(true);
        given(likeRepository.findByPostIdAndMemberId(POST_ID, MEMBER_ID)).willReturn(Optional.empty());
        PostStatsSummary stats = PostStatsSummary.empty(POST_ID);
        LikeResponse.StatsDTO response = new LikeResponse.StatsDTO(POST_ID, 0L);
        given(postStatsSlots.summarize(POST_ID)).willReturn(stats);
        given(likeMapper.toStatsDTO(stats)).willReturn(response);

        LikeResponse.StatsDTO result = likeService.removeLike(POST_ID, MEMBER_ID);

        assertThat(result).isEqualTo(response);
        verify(postStatsSlots, never()).getRandomForUpdate(anyLong());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("같은 좋아요를 다른 요청이 먼저 지웠으면(삭제 0행) 통계를 내리지 않는다")
    void removeLikeAlreadyDeleted() {
        Like like = Like.builder().id(5L).memberId(MEMBER_ID).build();
        given(postRepository.existsById(POST_ID)).willReturn(true);
        given(likeRepository.findByPostIdAndMemberId(POST_ID, MEMBER_ID)).willReturn(Optional.of(like));
        given(likeRepository.deleteIfPresent(5L)).willReturn(0);
        given(postStatsSlots.summarize(POST_ID)).willReturn(PostStatsSummary.empty(POST_ID));

        likeService.removeLike(POST_ID, MEMBER_ID);

        verify(postStatsSlots, never()).getRandomForUpdate(anyLong());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("좋아요를 지운 요청만 슬롯을 내리고 이벤트(통계 변경 포함)를 한 번 발행한다")
    void removeLikeDecreasesSlot() {
        Like like = Like.builder().id(5L).memberId(MEMBER_ID).build();
        PostStats slot = PostStats.createSlots(PostFixture.post(POST_ID, 2L)).getFirst();
        LikeEvent.Removed event = new LikeEvent.Removed(null, POST_ID);
        given(postRepository.existsById(POST_ID)).willReturn(true);
        given(likeRepository.findByPostIdAndMemberId(POST_ID, MEMBER_ID)).willReturn(Optional.of(like));
        given(likeRepository.deleteIfPresent(5L)).willReturn(1);
        given(postStatsSlots.getRandomForUpdate(POST_ID)).willReturn(slot);
        given(likeMapper.toRemovedEvent(like)).willReturn(event);
        given(postStatsSlots.summarize(POST_ID)).willReturn(PostStatsSummary.empty(POST_ID));

        likeService.removeLike(POST_ID, MEMBER_ID);

        assertThat(slot.getLikeCount()).isEqualTo(-1);
        verify(eventPublisher).publishEvent(event);
        verify(eventPublisher, times(1)).publishEvent(any(Object.class));
    }
}
