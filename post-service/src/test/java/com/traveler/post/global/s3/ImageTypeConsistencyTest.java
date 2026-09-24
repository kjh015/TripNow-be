package com.traveler.post.global.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.traveler.post.domain.post.mapper.PostImageMapper;
import com.traveler.post.domain.post.service.PostImageService;
import com.traveler.post.global.exception.PostServiceException;
import com.traveler.post.global.exception.code.PostServiceErrorCode;
import com.traveler.post.global.s3.validation.AllowedImageContentTypeValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** 허용 이미지 형식(D1: gif 불허)이 요청 검증 · 발급 서비스 · S3 요청 생성 세 계층에서 같게 처리되는지 확인한다. */
class ImageTypeConsistencyTest {

    private final AllowedImageContentTypeValidator validator = new AllowedImageContentTypeValidator();
    private final S3Service s3Service = mock(S3Service.class);
    private final PostImageService postImageService = new PostImageService(s3Service, mock(PostImageMapper.class));
    private final S3Service realS3Service = new S3Service(mock(S3Client.class), mock(S3Presigner.class));

    @Test
    @DisplayName("gif는 세 계층 모두에서 거부된다")
    void gifRejectedEverywhere() {
        assertThat(validator.isValid("image/gif", null)).isFalse();

        assertThatThrownBy(() -> postImageService.getPresignedUrl(1L, "photo.gif", "image/gif"))
                .isInstanceOf(PostServiceException.class)
                .extracting("code")
                .isEqualTo(PostServiceErrorCode.S3_INVALID_FILE_EXTENSION);
        verifyNoInteractions(s3Service);

        assertThatThrownBy(() -> realS3Service.generatePresignedUrl("posts/1/a.gif", "image/gif"))
                .isInstanceOf(PostServiceException.class)
                .extracting("code")
                .isEqualTo(PostServiceErrorCode.S3_INVALID_CONTENT_TYPE);
    }

    @ParameterizedTest
    @EnumSource(ImageType.class)
    @DisplayName("허용 형식은 요청 검증을 통과하고 발급 키의 확장자가 된다")
    void allowedTypesAccepted(ImageType type) {
        assertThat(validator.isValid(type.getContentType(), null)).isTrue();

        postImageService.getPresignedUrl(1L, "photo." + type.getExtension().toUpperCase(), type.getContentType());

        verify(s3Service)
                .generatePresignedUrl(
                        matches("^posts/1/[0-9a-f-]{36}\\." + type.getExtension() + "$"), eq(type.getContentType()));
    }

    @Test
    @DisplayName("확장자와 MIME 타입이 짝이 맞지 않으면 거부된다")
    void mismatchedExtensionAndContentTypeRejected() {
        assertThatThrownBy(() -> postImageService.getPresignedUrl(1L, "photo.png", "image/jpeg"))
                .isInstanceOf(PostServiceException.class)
                .extracting("code")
                .isEqualTo(PostServiceErrorCode.S3_INVALID_FILE_TYPE);
    }
}
