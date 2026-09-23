package com.traveler.post.domain.post.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.traveler.post.global.exception.PostServiceException;
import com.traveler.post.global.exception.code.PostServiceErrorCode;
import com.traveler.post.global.s3.ImageType;
import com.traveler.post.global.s3.S3Service;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PostImageKeyValidatorTest {

    private static final Long MEMBER_ID = 1L;
    private static final String OWN_KEY = "posts/1/3f2b8c1e-5d4a-4e6b-9c7d-1a2b3c4d5e6f.png";

    private final S3Service s3Service = mock(S3Service.class);
    private final PostImageKeyValidator validator = new PostImageKeyValidator(s3Service);

    @ParameterizedTest
    @ValueSource(
            strings = {
                "posts/2/3f2b8c1e-5d4a-4e6b-9c7d-1a2b3c4d5e6f.png",
                "posts/1/../2/3f2b8c1e-5d4a-4e6b-9c7d-1a2b3c4d5e6f.png",
                "posts/1/x/3f2b8c1e-5d4a-4e6b-9c7d-1a2b3c4d5e6f.png",
                "posts/10/3f2b8c1e-5d4a-4e6b-9c7d-1a2b3c4d5e6f.png",
                "posts/1/3f2b8c1e-5d4a-4e6b-9c7d-1a2b3c4d5e6f.gif",
                "posts/1/3f2b8c1e-5d4a-4e6b-9c7d-1a2b3c4d5e6f.PNG",
                "posts/1/profile.png",
                "/posts/1/3f2b8c1e-5d4a-4e6b-9c7d-1a2b3c4d5e6f.png",
                "images/2026/04/27/a1b2c3d4.png"
            })
    @DisplayName("발급 형식(posts/{memberId}/{UUID}.{허용 확장자})이 아닌 키는 S3 조회 없이 거부된다")
    void rejectsKeyNotIssuedForMember(String key) {
        assertThatThrownBy(() -> validator.validateNewKeys(MEMBER_ID, List.of(key)))
                .isInstanceOf(PostServiceException.class)
                .extracting("code")
                .isEqualTo(PostServiceErrorCode.POST_IMAGE_INVALID_KEY);

        verifyNoInteractions(s3Service);
    }

    @Test
    @DisplayName("형식이 틀린 키가 하나라도 있으면 어떤 키도 S3에 조회하지 않는다")
    void checksFormatOfAllKeysBeforeCallingS3() {
        assertThatThrownBy(() -> validator.validateNewKeys(
                        MEMBER_ID, List.of(OWN_KEY, "posts/2/3f2b8c1e-5d4a-4e6b-9c7d-1a2b3c4d5e6f.png")))
                .isInstanceOf(PostServiceException.class);

        verifyNoInteractions(s3Service);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("키가 없으면 아무 것도 하지 않는다")
    void doesNothingWithoutKeys(List<String> keys) {
        assertThatCode(() -> validator.validateNewKeys(MEMBER_ID, keys)).doesNotThrowAnyException();

        verifyNoInteractions(s3Service);
    }

    @Test
    @DisplayName("S3에 없는 키는 거부하고 삭제도 하지 않는다")
    void rejectsNotUploadedKey() {
        given(s3Service.findObjectSize(OWN_KEY)).willReturn(Optional.empty());

        assertThatThrownBy(() -> validator.validateNewKeys(MEMBER_ID, List.of(OWN_KEY)))
                .isInstanceOf(PostServiceException.class)
                .extracting("code")
                .isEqualTo(PostServiceErrorCode.POST_IMAGE_NOT_UPLOADED);

        verify(s3Service, never()).deleteFilesByKeys(any());
    }

    @Test
    @DisplayName("10MB 초과 객체는 삭제하고 거부한다")
    void deletesAndRejectsOversizedObject() {
        given(s3Service.findObjectSize(OWN_KEY)).willReturn(Optional.of(S3Service.MAX_FILE_SIZE + 1));

        assertThatThrownBy(() -> validator.validateNewKeys(MEMBER_ID, List.of(OWN_KEY)))
                .isInstanceOf(PostServiceException.class)
                .extracting("code")
                .isEqualTo(PostServiceErrorCode.POST_IMAGE_TOO_LARGE);

        verify(s3Service).deleteFilesByKeys(List.of(OWN_KEY));
    }

    @Test
    @DisplayName("10MB 경계값(정확히 10MB)은 통과한다")
    void allowsExactlyMaxFileSize() {
        given(s3Service.findObjectSize(OWN_KEY)).willReturn(Optional.of(S3Service.MAX_FILE_SIZE));

        assertThatCode(() -> validator.validateNewKeys(MEMBER_ID, List.of(OWN_KEY)))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(ImageType.class)
    @DisplayName("발급된 키는 모든 허용 확장자에서 통과한다")
    void allowsIssuedKeyForEveryImageType(ImageType type) {
        String key = PostImageKey.create(MEMBER_ID, type);
        given(s3Service.findObjectSize(key)).willReturn(Optional.of(1024L));

        assertThat(PostImageKey.isIssuedFor(MEMBER_ID, key)).isTrue();
        assertThatCode(() -> validator.validateNewKeys(MEMBER_ID, List.of(key))).doesNotThrowAnyException();
    }
}
