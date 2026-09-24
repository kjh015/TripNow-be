package com.traveler.post.domain.post.service;

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
import com.traveler.post.domain.post.entity.TravelPlace;
import com.traveler.post.domain.post.enums.Category;
import com.traveler.post.domain.post.enums.Region;
import com.traveler.post.domain.post.mapper.PostMapper;
import com.traveler.post.domain.post.mapper.TravelPlaceMapper;
import com.traveler.post.domain.post.repository.PostRepository;
import com.traveler.post.domain.post.support.PostHardDeleter;
import com.traveler.post.domain.post.support.PostImageKeyValidator;
import com.traveler.post.global.exception.PostServiceException;
import com.traveler.post.global.exception.code.PostServiceErrorCode;
import com.traveler.post.global.s3.S3Service;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

class PostServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long POST_ID = 10L;
    private static final String OWN_KEY = "posts/1/3f2b8c1e-5d4a-4e6b-9c7d-1a2b3c4d5e6f.png";
    private static final String OWN_KEY_2 = "posts/1/0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d.jpg";
    private static final String OTHER_KEY = "posts/2/3f2b8c1e-5d4a-4e6b-9c7d-1a2b3c4d5e6f.png";

    private final PostRepository postRepository = mock(PostRepository.class);
    private final PostMapper postMapper = mock(PostMapper.class);
    private final TravelPlaceMapper travelPlaceMapper = mock(TravelPlaceMapper.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final S3Service s3Service = mock(S3Service.class);
    private final TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
    private final PostHardDeleter postHardDeleter = mock(PostHardDeleter.class);

    private final PostService postService = new PostService(
            postRepository,
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
        assertThatThrownBy(() -> postService.createPost(MEMBER_ID, createDto(List.of(OWN_KEY, OTHER_KEY))))
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
        post.setImages(List.of(OWN_KEY));
        given(postRepository.findByIdWithDetails(POST_ID)).willReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.updatePost(POST_ID, MEMBER_ID, updateDto(List.of(OTHER_KEY))))
                .isInstanceOf(PostServiceException.class)
                .extracting("code")
                .isEqualTo(PostServiceErrorCode.POST_IMAGE_INVALID_KEY);

        verifyNoInteractions(s3Service, eventPublisher, transactionTemplate);
        assertThat(post.getImages()).extracting(PostImage::getImageKey).containsExactly(OWN_KEY);
    }

    @Test
    @DisplayName("생성: S3에 없는 키는 거부된다")
    void createRejectsNotUploadedKey() {
        given(s3Service.findObjectSize(OWN_KEY)).willReturn(Optional.empty());

        assertThatThrownBy(() -> postService.createPost(MEMBER_ID, createDto(List.of(OWN_KEY))))
                .isInstanceOf(PostServiceException.class)
                .extracting("code")
                .isEqualTo(PostServiceErrorCode.POST_IMAGE_NOT_UPLOADED);

        verify(postRepository, never()).save(any());
        verify(s3Service, never()).deleteFilesByKeys(any());
    }

    @Test
    @DisplayName("생성: 10MB 초과 객체는 삭제 후 거부된다")
    void createRejectsAndDeletesOversizedObject() {
        given(s3Service.findObjectSize(OWN_KEY)).willReturn(Optional.of(S3Service.MAX_FILE_SIZE + 1));

        assertThatThrownBy(() -> postService.createPost(MEMBER_ID, createDto(List.of(OWN_KEY))))
                .isInstanceOf(PostServiceException.class)
                .extracting("code")
                .isEqualTo(PostServiceErrorCode.POST_IMAGE_TOO_LARGE);

        verify(s3Service).deleteFilesByKeys(List.of(OWN_KEY));
        verify(postRepository, never()).save(any());
    }

    @Test
    @DisplayName("생성: 본인 키이고 10MB 이하면 저장된다")
    void createSavesValidKeys() {
        given(s3Service.findObjectSize(anyString())).willReturn(Optional.of(S3Service.MAX_FILE_SIZE));

        postService.createPost(MEMBER_ID, createDto(List.of(OWN_KEY, OWN_KEY_2)));

        verify(s3Service).findObjectSize(OWN_KEY);
        verify(s3Service).findObjectSize(OWN_KEY_2);
        verify(postRepository).save(any());
    }

    @Test
    @DisplayName("수정: 기존 키 유지 + 새 키 1개면 headObject는 새 키에 대해 1회만 호출된다")
    void updateChecksOnlyNewKey() {
        Post post = post(MEMBER_ID);
        post.setImages(List.of(OWN_KEY));
        given(postRepository.findByIdWithDetails(POST_ID)).willReturn(Optional.of(post));
        given(s3Service.findObjectSize(OWN_KEY_2)).willReturn(Optional.of(1024L));

        postService.updatePost(POST_ID, MEMBER_ID, updateDto(List.of(OWN_KEY, OWN_KEY_2)));

        verify(s3Service, times(1)).findObjectSize(anyString());
        verify(s3Service).findObjectSize(OWN_KEY_2);
        assertThat(post.getImages()).extracting(PostImage::getImageKey).containsExactly(OWN_KEY, OWN_KEY_2);
        verify(postMapper, never()).toImageDeleteEvent(any(), any());
    }

    @Test
    @DisplayName("수정: 이미지를 빼기만 하면 S3 조회 없이 삭제 이벤트가 발행된다")
    void updateRemovingImageSkipsHeadObject() {
        Post post = post(MEMBER_ID);
        post.setImages(List.of(OWN_KEY, OWN_KEY_2));
        given(postRepository.findByIdWithDetails(POST_ID)).willReturn(Optional.of(post));
        PostEvent.ImagesDelete event = new PostEvent.ImagesDelete(null);
        given(postMapper.toImageDeleteEvent(POST_ID, List.of(OWN_KEY_2))).willReturn(event);

        postService.updatePost(POST_ID, MEMBER_ID, updateDto(List.of(OWN_KEY)));

        verifyNoInteractions(s3Service);
        verify(eventPublisher).publishEvent(event);
    }

    @Test
    @DisplayName("수정: 검증 후 트랜잭션 시작 전에 이미지가 바뀌어 검증하지 않은 키가 생기면 거부된다")
    void updateRejectsKeyChangedAfterValidation() {
        Post beforeValidation = post(MEMBER_ID);
        beforeValidation.setImages(List.of(OWN_KEY));
        Post inTransaction = post(MEMBER_ID);
        given(postRepository.findByIdWithDetails(POST_ID))
                .willReturn(Optional.of(beforeValidation))
                .willReturn(Optional.of(inTransaction));

        assertThatThrownBy(() -> postService.updatePost(POST_ID, MEMBER_ID, updateDto(List.of(OWN_KEY))))
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

        assertThatThrownBy(() -> postService.updatePost(POST_ID, MEMBER_ID, updateDto(List.of(OWN_KEY))))
                .isInstanceOf(PostServiceException.class);

        verifyNoInteractions(s3Service, transactionTemplate);
    }

    @Test
    @DisplayName("배치 삭제: 공용 영구 삭제 경로를 쓰고 반환된 이미지 키로 삭제 이벤트를 발행한다")
    void deleteBatchUsesHardDeleter() {
        List<Long> ids = List.of(POST_ID, 11L);
        PostEvent.ImagesDeleteBatch event = new PostEvent.ImagesDeleteBatch(null);
        given(postHardDeleter.hardDelete(ids)).willReturn(List.of(OWN_KEY));
        given(postMapper.toImageDeleteBatchEvent(POST_ID, List.of(OWN_KEY))).willReturn(event);

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
