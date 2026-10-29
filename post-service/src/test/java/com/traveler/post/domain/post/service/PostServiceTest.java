package com.traveler.post.domain.post.service;

import static com.traveler.post.testsupport.fixture.PostFixture.MEMBER_1_KEY;
import static com.traveler.post.testsupport.fixture.PostFixture.MEMBER_1_KEY_2;
import static com.traveler.post.testsupport.fixture.PostFixture.MEMBER_2_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.traveler.post.domain.post.dto.event.PostEvent;
import com.traveler.post.domain.post.dto.request.PostRequest;
import com.traveler.post.domain.post.entity.Post;
import com.traveler.post.domain.post.entity.PostImage;
import com.traveler.post.domain.post.entity.PostStats;
import com.traveler.post.domain.post.entity.TravelPlace;
import com.traveler.post.domain.post.enums.Category;
import com.traveler.post.domain.post.enums.Region;
import com.traveler.post.domain.post.mapper.PostMapper;
import com.traveler.post.domain.post.mapper.TravelPlaceMapper;
import com.traveler.post.domain.post.repository.PostRepository;
import com.traveler.post.domain.post.repository.PostStatsRepository;
import com.traveler.post.domain.post.support.PostHardDeleter;
import com.traveler.post.domain.post.support.PostImageKeyValidator;
import com.traveler.post.domain.post.support.PostStatsSlots;
import com.traveler.post.global.exception.PostServiceException;
import com.traveler.post.global.exception.code.PostServiceErrorCode;
import com.traveler.post.global.s3.S3Service;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

class PostServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long POST_ID = 10L;

    private final PostRepository postRepository = mock(PostRepository.class);
    private final PostStatsRepository postStatsRepository = mock(PostStatsRepository.class);
    private final PostStatsSlots postStatsSlots = mock(PostStatsSlots.class);
    private final PostMapper postMapper = mock(PostMapper.class);
    private final TravelPlaceMapper travelPlaceMapper = mock(TravelPlaceMapper.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final S3Service s3Service = mock(S3Service.class);
    private final TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
    private final PostHardDeleter postHardDeleter = mock(PostHardDeleter.class);

    private final PostService postService = new PostService(
            postRepository,
            postStatsRepository,
            postStatsSlots,
            postMapper,
            travelPlaceMapper,
            eventPublisher,
            new PostImageKeyValidator(s3Service),
            postHardDeleter,
            transactionTemplate);

    @BeforeEach
    void setUp() {
        given(transactionTemplate.execute(any()))
                .willAnswer(invocation ->
                        invocation.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
        given(postMapper.toCreateEntity(any(), any(), any())).willAnswer(invocation -> post(invocation.getArgument(2)));
        given(postRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("생성: 타인 prefix 키는 S3 호출·저장·이벤트 발행 없이 거부된다")
    void createRejectsOtherMembersKey() {
        assertThatThrownBy(() -> postService.createPost(MEMBER_ID, createDto(List.of(MEMBER_1_KEY, MEMBER_2_KEY))))
                .isInstanceOf(PostServiceException.class)
                .extracting("code")
                .isEqualTo(PostServiceErrorCode.POST_IMAGE_INVALID_KEY);

        verifyNoInteractions(s3Service, eventPublisher, transactionTemplate);
        verify(postRepository, never()).save(any());
    }

    @Test
    @DisplayName("수정: 타인 prefix 키는 S3 호출·이미지 삭제 이벤트 발행 없이 거부된다")
    void updateRejectsOtherMembersKey() {
        Post post = post(MEMBER_ID);
        post.setImages(List.of(MEMBER_1_KEY));
        given(postRepository.findByIdWithDetails(POST_ID)).willReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.updatePost(POST_ID, MEMBER_ID, updateDto(List.of(MEMBER_2_KEY))))
                .isInstanceOf(PostServiceException.class)
                .extracting("code")
                .isEqualTo(PostServiceErrorCode.POST_IMAGE_INVALID_KEY);

        verifyNoInteractions(s3Service, eventPublisher, transactionTemplate);
        assertThat(post.getImages()).extracting(PostImage::getImageKey).containsExactly(MEMBER_1_KEY);
    }

    @Test
    @DisplayName("생성: S3에 없는 키는 거부된다")
    void createRejectsNotUploadedKey() {
        given(s3Service.findObjectSize(MEMBER_1_KEY)).willReturn(Optional.empty());

        assertThatThrownBy(() -> postService.createPost(MEMBER_ID, createDto(List.of(MEMBER_1_KEY))))
                .isInstanceOf(PostServiceException.class)
                .extracting("code")
                .isEqualTo(PostServiceErrorCode.POST_IMAGE_NOT_UPLOADED);

        verify(postRepository, never()).save(any());
        verify(s3Service, never()).deleteFilesByKeys(any());
    }

    @Test
    @DisplayName("생성: 10MB 초과 객체는 삭제 후 거부된다")
    void createRejectsAndDeletesOversizedObject() {
        given(s3Service.findObjectSize(MEMBER_1_KEY)).willReturn(Optional.of(S3Service.MAX_FILE_SIZE + 1));

        assertThatThrownBy(() -> postService.createPost(MEMBER_ID, createDto(List.of(MEMBER_1_KEY))))
                .isInstanceOf(PostServiceException.class)
                .extracting("code")
                .isEqualTo(PostServiceErrorCode.POST_IMAGE_TOO_LARGE);

        verify(s3Service).deleteFilesByKeys(List.of(MEMBER_1_KEY));
        verify(postRepository, never()).save(any());
    }

    @Test
    @DisplayName("생성: 본인 키이고 10MB 이하면 저장된다")
    void createSavesValidKeys() {
        given(s3Service.findObjectSize(anyString())).willReturn(Optional.of(S3Service.MAX_FILE_SIZE));

        postService.createPost(MEMBER_ID, createDto(List.of(MEMBER_1_KEY, MEMBER_1_KEY_2)));

        verify(s3Service).findObjectSize(MEMBER_1_KEY);
        verify(s3Service).findObjectSize(MEMBER_1_KEY_2);
        ArgumentCaptor<Post> savedPost = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(savedPost.capture());
        // 저장한 게시글의 통계 슬롯 8행을 함께 만든다(슬롯이 없으면 좋아요·댓글이 실패한다)
        ArgumentCaptor<List<PostStats>> slots = ArgumentCaptor.captor();
        verify(postStatsRepository).saveAll(slots.capture());
        assertThat(slots.getValue()).hasSize(PostStats.SLOT_COUNT).allSatisfy(slot -> assertThat(slot.getPost())
                .isSameAs(savedPost.getValue()));
        assertThat(slots.getValue()).extracting(slot -> slot.getId().getSlot()).containsExactly(0, 1, 2, 3, 4, 5, 6, 7);
    }

    @Test
    @DisplayName("수정: 기존 키 유지 + 새 키 1개면 headObject는 새 키에 대해 1회만 호출된다")
    void updateChecksOnlyNewKey() {
        Post post = post(MEMBER_ID);
        post.setImages(List.of(MEMBER_1_KEY));
        given(postRepository.findByIdWithDetails(POST_ID)).willReturn(Optional.of(post));
        given(s3Service.findObjectSize(MEMBER_1_KEY_2)).willReturn(Optional.of(1024L));

        postService.updatePost(POST_ID, MEMBER_ID, updateDto(List.of(MEMBER_1_KEY, MEMBER_1_KEY_2)));

        verify(s3Service, times(1)).findObjectSize(anyString());
        verify(s3Service).findObjectSize(MEMBER_1_KEY_2);
        assertThat(post.getImages()).extracting(PostImage::getImageKey).containsExactly(MEMBER_1_KEY, MEMBER_1_KEY_2);
        verify(postMapper, never()).toImageDeleteEvent(any(), any());
    }

    @Test
    @DisplayName("수정: 이미지를 빼기만 하면 S3 조회 없이 삭제 이벤트가 발행된다")
    void updateRemovingImageSkipsHeadObject() {
        Post post = post(MEMBER_ID);
        post.setImages(List.of(MEMBER_1_KEY, MEMBER_1_KEY_2));
        given(postRepository.findByIdWithDetails(POST_ID)).willReturn(Optional.of(post));
        PostEvent.ImagesDelete event = new PostEvent.ImagesDelete(null);
        given(postMapper.toImageDeleteEvent(POST_ID, List.of(MEMBER_1_KEY_2))).willReturn(event);

        postService.updatePost(POST_ID, MEMBER_ID, updateDto(List.of(MEMBER_1_KEY)));

        verifyNoInteractions(s3Service);
        verify(eventPublisher).publishEvent(event);
    }

    @Test
    @DisplayName("수정: 검증 후 트랜잭션 시작 전에 이미지가 바뀌어 검증하지 않은 키가 생기면 거부된다")
    void updateRejectsKeyChangedAfterValidation() {
        Post beforeValidation = post(MEMBER_ID);
        beforeValidation.setImages(List.of(MEMBER_1_KEY));
        Post inTransaction = post(MEMBER_ID);
        given(postRepository.findByIdWithDetails(POST_ID))
                .willReturn(Optional.of(beforeValidation))
                .willReturn(Optional.of(inTransaction));

        assertThatThrownBy(() -> postService.updatePost(POST_ID, MEMBER_ID, updateDto(List.of(MEMBER_1_KEY))))
                .isInstanceOf(PostServiceException.class)
                .extracting("code")
                .isEqualTo(PostServiceErrorCode.POST_IMAGE_INVALID_KEY);

        assertThat(inTransaction.getImages()).isEmpty();
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("수정: 타인 게시글이면 이미지 검증 전에 거부된다")
    void updateRejectsNonOwnerBeforeImageCheck() {
        given(postRepository.findByIdWithDetails(eq(POST_ID))).willReturn(Optional.of(post(2L)));

        assertThatThrownBy(() -> postService.updatePost(POST_ID, MEMBER_ID, updateDto(List.of(MEMBER_1_KEY))))
                .isInstanceOf(PostServiceException.class);

        verifyNoInteractions(s3Service, transactionTemplate);
    }

    @Test
    @DisplayName("배치 삭제: 공용 영구 삭제 경로를 쓰고 반환된 이미지 키로 삭제 이벤트를 발행한다")
    void deleteBatchUsesHardDeleter() {
        List<Long> ids = List.of(POST_ID, 11L);
        PostEvent.ImagesDeleteBatch event = new PostEvent.ImagesDeleteBatch(null);
        given(postHardDeleter.hardDelete(ids)).willReturn(List.of(MEMBER_1_KEY));
        given(postMapper.toImageDeleteBatchEvent(POST_ID, List.of(MEMBER_1_KEY)))
                .willReturn(event);

        postService.deleteBatch(ids);

        verify(postHardDeleter).hardDelete(ids);
        verify(postRepository, never()).hardDeletePostsByIds(any());
        verify(eventPublisher).publishEvent(event);
    }

    @Test
    @DisplayName("배치 삭제: 이미지가 없으면 이미지 삭제 이벤트를 발행하지 않는다")
    void deleteBatchWithoutImagesPublishesNothing() {
        given(postHardDeleter.hardDelete(List.of(POST_ID))).willReturn(List.of());

        postService.deleteBatch(List.of(POST_ID));

        verify(eventPublisher, never()).publishEvent(any());
    }

    private static Post post(Long memberId) {
        return Post.builder()
                .id(POST_ID)
                .memberId(memberId)
                .travelPlace(TravelPlace.builder().build())
                .build();
    }

    private static PostRequest.CreateDTO createDto(List<String> images) {
        return new PostRequest.CreateDTO("title", "content", "place", "address", Category.FOOD, Region.SEOUL, images);
    }

    private static PostRequest.UpdateDTO updateDto(List<String> images) {
        return new PostRequest.UpdateDTO("title", "content", "place", "address", Category.FOOD, Region.SEOUL, images);
    }
}
