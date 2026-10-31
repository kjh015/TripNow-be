package com.traveler.post.domain.post.service;

import static com.traveler.post.testsupport.fixture.PostFixture.MEMBER_1_KEY;
import static com.traveler.post.testsupport.fixture.PostFixture.MEMBER_1_KEY_2;
import static com.traveler.post.testsupport.fixture.PostFixture.MEMBER_1_TEMP_KEY;
import static com.traveler.post.testsupport.fixture.PostFixture.MEMBER_1_TEMP_KEY_2;
import static com.traveler.post.testsupport.fixture.PostFixture.MEMBER_2_KEY;
import static com.traveler.post.testsupport.fixture.PostFixture.MEMBER_2_TEMP_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.traveler.post.domain.post.mapper.PostImageMapper;
import com.traveler.post.domain.post.support.PostImageKey;
import com.traveler.post.global.exception.PostServiceException;
import com.traveler.post.global.exception.code.PostServiceErrorCode;
import com.traveler.post.global.s3.ImageType;
import com.traveler.post.global.s3.S3Service;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

@ExtendWith(MockitoExtension.class)
class PostImageServiceTest {

    private static final Long MEMBER_ID = 1L;

    @Mock
    private S3Service s3Service;

    @Mock
    private PostImageMapper postImageMapper;

    @InjectMocks
    private PostImageService postImageService;

    @Test
    @DisplayName("발급 키는 temp/{memberId}/로 시작한다")
    void issuesTempKey() {
        postImageService.getPresignedUrl(MEMBER_ID, "photo.png", "image/png");

        verify(s3Service).generatePresignedUrl(startsWith("temp/1/"), anyString());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                MEMBER_2_TEMP_KEY,
                MEMBER_2_KEY,
                MEMBER_1_KEY,
                "temp/1/../2/3f2b8c1e-5d4a-4e6b-9c7d-1a2b3c4d5e6f.png",
                "temp/1/x/3f2b8c1e-5d4a-4e6b-9c7d-1a2b3c4d5e6f.png",
                "temp/10/3f2b8c1e-5d4a-4e6b-9c7d-1a2b3c4d5e6f.png",
                "temp/1/3f2b8c1e-5d4a-4e6b-9c7d-1a2b3c4d5e6f.gif",
                "temp/1/3f2b8c1e-5d4a-4e6b-9c7d-1a2b3c4d5e6f.PNG",
                "temp/1/profile.png",
                "/temp/1/3f2b8c1e-5d4a-4e6b-9c7d-1a2b3c4d5e6f.png",
                "images/2026/04/27/a1b2c3d4.png"
            })
    @DisplayName("이 회원에게 발급된 임시 키 형식이 아니면 S3 호출 없이 거부된다")
    void rejectsKeyNotIssuedForMember(String key) {
        assertThatThrownBy(() -> postImageService.confirmUploads(MEMBER_ID, List.of(key)))
                .isInstanceOf(PostServiceException.class)
                .extracting("code")
                .isEqualTo(PostServiceErrorCode.S3_KEY_FORBIDDEN);

        verifyNoInteractions(s3Service);
    }

    @Test
    @DisplayName("객체가 없으면 S3_FILE_NOT_FOUND가 그대로 전파되고 복사·삭제하지 않는다")
    void rejectsNotUploadedKey() {
        given(s3Service.headObject(MEMBER_1_TEMP_KEY))
                .willThrow(new PostServiceException(PostServiceErrorCode.S3_FILE_NOT_FOUND));

        assertThatThrownBy(() -> postImageService.confirmUploads(MEMBER_ID, List.of(MEMBER_1_TEMP_KEY)))
                .isInstanceOf(PostServiceException.class)
                .extracting("code")
                .isEqualTo(PostServiceErrorCode.S3_FILE_NOT_FOUND);

        verify(s3Service, never()).copyObject(any(), any());
        verify(s3Service, never()).deleteFilesByKeys(any());
    }

    @Test
    @DisplayName("10MB 초과면 원본을 지우고 거부한다")
    void deletesAndRejectsOversizedObject() {
        given(s3Service.headObject(MEMBER_1_TEMP_KEY)).willReturn(size(S3Service.MAX_FILE_SIZE + 1));

        assertThatThrownBy(() -> postImageService.confirmUploads(MEMBER_ID, List.of(MEMBER_1_TEMP_KEY)))
                .isInstanceOf(PostServiceException.class)
                .extracting("code")
                .isEqualTo(PostServiceErrorCode.S3_FILE_TOO_LARGE);

        verify(s3Service).deleteFilesByKeys(List.of(MEMBER_1_TEMP_KEY));
        verify(s3Service, never()).copyObject(any(), any());
    }

    @Test
    @DisplayName("정상 키는 posts/{memberId}/로 복사하고 그 키를 순서대로 돌려준다. temp/ 원본은 지우지 않는다")
    void copiesToPostPrefix() {
        given(s3Service.headObject(anyString())).willReturn(size(S3Service.MAX_FILE_SIZE));

        List<String> confirmed =
                postImageService.confirmUploads(MEMBER_ID, List.of(MEMBER_1_TEMP_KEY, MEMBER_1_TEMP_KEY_2));

        assertThat(confirmed).containsExactly(MEMBER_1_KEY, MEMBER_1_KEY_2);
        verify(s3Service).copyObject(MEMBER_1_TEMP_KEY, MEMBER_1_KEY);
        verify(s3Service).copyObject(MEMBER_1_TEMP_KEY_2, MEMBER_1_KEY_2);
        verify(s3Service, never()).deleteFilesByKeys(any());
    }

    @Test
    @DisplayName("둘째 키에서 실패하면 첫째 키의 사본만 지우고 원래 오류를 던진다")
    void rollsBackCopiesOnPartialFailure() {
        given(s3Service.headObject(MEMBER_1_TEMP_KEY)).willReturn(size(1024L));
        given(s3Service.headObject(MEMBER_1_TEMP_KEY_2))
                .willThrow(new PostServiceException(PostServiceErrorCode.S3_FILE_NOT_FOUND));

        assertThatThrownBy(() ->
                        postImageService.confirmUploads(MEMBER_ID, List.of(MEMBER_1_TEMP_KEY, MEMBER_1_TEMP_KEY_2)))
                .isInstanceOf(PostServiceException.class)
                .extracting("code")
                .isEqualTo(PostServiceErrorCode.S3_FILE_NOT_FOUND);

        verify(s3Service).deleteFilesByKeys(List.of(MEMBER_1_KEY));
    }

    @Test
    @DisplayName("수정: 이미 붙은 키는 S3 호출 없이 통과하고 새 임시 키만 복사한다")
    void updatePassesAttachedKeys() {
        given(s3Service.headObject(MEMBER_1_TEMP_KEY_2)).willReturn(size(1024L));

        List<String> confirmed = postImageService.confirmUploads(
                MEMBER_ID, List.of(MEMBER_1_KEY, MEMBER_1_TEMP_KEY_2), List.of(MEMBER_1_KEY));

        assertThat(confirmed).containsExactly(MEMBER_1_KEY, MEMBER_1_KEY_2);
        verify(s3Service, times(1)).headObject(anyString());
        verify(s3Service, times(1)).copyObject(any(), any());
    }

    @Test
    @DisplayName("수정: 이 게시글에 붙지 않은 본인의 posts/ 키도 거부된다")
    void updateRejectsPostKeyOfOtherPost() {
        assertThatThrownBy(() ->
                        postImageService.confirmUploads(MEMBER_ID, List.of(MEMBER_1_KEY_2), List.of(MEMBER_1_KEY)))
                .isInstanceOf(PostServiceException.class)
                .extracting("code")
                .isEqualTo(PostServiceErrorCode.S3_KEY_FORBIDDEN);

        verifyNoInteractions(s3Service);
    }

    @Test
    @DisplayName("수정: images가 null이면 이미지를 바꾸지 않는 요청이라 null을 돌려준다")
    void updateKeepsNullImages() {
        assertThat(postImageService.confirmUploads(MEMBER_ID, null, List.of(MEMBER_1_KEY)))
                .isNull();

        verifyNoInteractions(s3Service);
    }

    @Test
    @DisplayName("이미 붙은 보관 키가 된 경우는 새로 복사한 키에서 뺀다")
    void findCopiedKeysExcludesCurrentKeys() {
        assertThat(postImageService.findCopiedKeys(
                        List.of(MEMBER_1_KEY, MEMBER_1_KEY_2, MEMBER_1_KEY_2), Set.of(MEMBER_1_KEY)))
                .containsExactly(MEMBER_1_KEY_2);
    }

    @Test
    @DisplayName("사본 삭제가 실패해도 예외를 던지지 않아 저장 실패 원인을 가리지 않는다")
    void deleteCopiesSwallowsFailure() {
        willThrow(new PostServiceException(PostServiceErrorCode.S3_DELETE_ERROR))
                .given(s3Service)
                .deleteFilesByKeys(List.of(MEMBER_1_KEY));

        assertThatCode(() -> postImageService.deleteCopies(List.of(MEMBER_1_KEY)))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(ImageType.class)
    @DisplayName("발급된 키는 모든 허용 확장자에서 확정된다")
    void confirmsIssuedKeyForEveryImageType(ImageType type) {
        String key = PostImageKey.create(MEMBER_ID, type);
        given(s3Service.headObject(key)).willReturn(size(1024L));

        assertThat(postImageService.confirmUploads(MEMBER_ID, List.of(key)))
                .singleElement()
                .asString()
                .startsWith("posts/1/")
                .endsWith("." + type.getExtension());
    }

    private static HeadObjectResponse size(long contentLength) {
        return HeadObjectResponse.builder().contentLength(contentLength).build();
    }
}
