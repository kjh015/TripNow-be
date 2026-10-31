package com.traveler.post.domain.post.facade;

import static com.traveler.post.testsupport.fixture.PostFixture.MEMBER_1_KEY;
import static com.traveler.post.testsupport.fixture.PostFixture.MEMBER_1_TEMP_KEY;
import static com.traveler.post.testsupport.fixture.PostFixture.MEMBER_1_TEMP_KEY_2;
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

import com.traveler.common.core.code.ErrorCode;
import com.traveler.post.domain.post.dto.request.PostRequest;
import com.traveler.post.domain.post.enums.Category;
import com.traveler.post.domain.post.enums.Region;
import com.traveler.post.domain.post.mapper.PostImageMapper;
import com.traveler.post.domain.post.service.PostImageService;
import com.traveler.post.domain.post.service.PostService;
import com.traveler.post.global.exception.PostServiceException;
import com.traveler.post.global.s3.S3Service;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

class PostFacadeTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long POST_ID = 10L;

    private final PostService postService = mock(PostService.class);
    private final S3Service s3Service = mock(S3Service.class);
    private final PostFacade facade =
            new PostFacade(postService, new PostImageService(s3Service, mock(PostImageMapper.class)));

    @BeforeEach
    void setUp() {
        given(s3Service.headObject(anyString()))
                .willReturn(HeadObjectResponse.builder().contentLength(1024L).build());
    }

    @Test
    @DisplayName("생성: 확정한 posts/ 키로 저장한다")
    void createSavesConfirmedKeys() {
        PostRequest.CreateDTO dto = createDto(List.of(MEMBER_1_TEMP_KEY));

        facade.createPost(MEMBER_ID, dto);

        List<String> copiedKeys = copiedKeys(1);
        verify(postService).createPost(MEMBER_ID, dto, copiedKeys);
        verify(s3Service, never()).deleteFilesByKeys(any());
    }

    @Test
    @DisplayName("생성: 저장이 실패하면 복사한 키만 지우고 temp/ 원본은 지우지 않는다")
    void createFailureDeletesOnlyCopies() {
        PostRequest.CreateDTO dto = createDto(List.of(MEMBER_1_TEMP_KEY, MEMBER_1_TEMP_KEY_2));
        given(postService.createPost(any(), any(), any())).willThrow(new IllegalStateException("db"));

        assertThatThrownBy(() -> facade.createPost(MEMBER_ID, dto)).isInstanceOf(IllegalStateException.class);

        List<String> copiedKeys = copiedKeys(2);
        verify(s3Service).deleteFilesByKeys(copiedKeys);
    }

    @Test
    @DisplayName("수정: 저장이 실패하면 이번 요청에서 복사한 키만 지운다")
    void updateFailureKeepsAttachedKeys() {
        PostRequest.UpdateDTO dto = updateDto(List.of(MEMBER_1_KEY, MEMBER_1_TEMP_KEY_2));
        given(postService.getOwnedImageKeys(POST_ID, MEMBER_ID)).willReturn(List.of(MEMBER_1_KEY));
        given(postService.updatePost(any(), any(), any(), any(), any())).willThrow(new IllegalStateException("db"));

        assertThatThrownBy(() -> facade.updatePost(POST_ID, MEMBER_ID, dto)).isInstanceOf(IllegalStateException.class);

        List<String> copiedKeys = copiedKeys(1);
        verify(s3Service).deleteFilesByKeys(copiedKeys);
    }

    @Test
    @DisplayName("수정: 복사한 키를 함께 넘긴다")
    void updatePassesCopiedKeys() {
        PostRequest.UpdateDTO dto = updateDto(List.of(MEMBER_1_KEY, MEMBER_1_TEMP_KEY_2));
        given(postService.getOwnedImageKeys(POST_ID, MEMBER_ID)).willReturn(List.of(MEMBER_1_KEY));

        facade.updatePost(POST_ID, MEMBER_ID, dto);

        String copiedKey = copiedKeys(1).getFirst();
        verify(postService)
                .updatePost(
                        eq(POST_ID),
                        eq(MEMBER_ID),
                        eq(dto),
                        eq(List.of(MEMBER_1_KEY, copiedKey)),
                        eq(List.of(copiedKey)));
    }

    @Test
    @DisplayName("수정: 타인 게시글이면 S3를 호출하지 않는다")
    void updateRejectsNonOwnerBeforeS3() {
        given(postService.getOwnedImageKeys(POST_ID, MEMBER_ID))
                .willThrow(new PostServiceException(ErrorCode.FORBIDDEN));

        assertThatThrownBy(() -> facade.updatePost(POST_ID, MEMBER_ID, updateDto(List.of(MEMBER_1_TEMP_KEY))))
                .isInstanceOf(PostServiceException.class);

        verify(s3Service, never()).copyObject(any(), any());
        verify(postService, never()).updatePost(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("수정: images가 null이면 S3를 호출하지 않고 null을 넘긴다")
    void updateWithoutImagesSkipsS3() {
        PostRequest.UpdateDTO dto = updateDto(null);
        given(postService.getOwnedImageKeys(POST_ID, MEMBER_ID)).willReturn(List.of(MEMBER_1_KEY));

        facade.updatePost(POST_ID, MEMBER_ID, dto);

        verify(postService).updatePost(POST_ID, MEMBER_ID, dto, null, List.of());
        verifyNoInteractions(s3Service);
    }

    // 보관 키는 확정마다 새로 만들어지므로 S3에 복사한 대상 키로 비교한다
    private List<String> copiedKeys(int count) {
        ArgumentCaptor<String> destination = ArgumentCaptor.forClass(String.class);
        verify(s3Service, times(count)).copyObject(anyString(), destination.capture());
        return destination.getAllValues();
    }

    private static PostRequest.CreateDTO createDto(List<String> images) {
        return new PostRequest.CreateDTO("title", "content", "place", "address", Category.FOOD, Region.SEOUL, images);
    }

    private static PostRequest.UpdateDTO updateDto(List<String> images) {
        return new PostRequest.UpdateDTO("title", "content", "place", "address", Category.FOOD, Region.SEOUL, images);
    }
}
