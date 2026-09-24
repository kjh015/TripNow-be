package com.traveler.post.global.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class S3ServiceTest {

    private static final String KEY = "posts/1/3f2b8c1e-5d4a-4e6b-9c7d-1a2b3c4d5e6f.png";

    private final S3Client s3Client = mock(S3Client.class);
    private final S3Service s3Service = new S3Service(s3Client, mock(S3Presigner.class));

    @Test
    @DisplayName("객체가 있으면 contentLength를 돌려준다")
    void returnsContentLength() {
        given(s3Client.headObject(any(HeadObjectRequest.class)))
                .willReturn(HeadObjectResponse.builder().contentLength(2048L).build());

        assertThat(s3Service.findObjectSize(KEY)).contains(2048L);
    }

    @Test
    @DisplayName("NoSuchKeyException이면 빈 값을 돌려준다")
    void noSuchKeyReturnsEmpty() {
        given(s3Client.headObject(any(HeadObjectRequest.class)))
                .willThrow(NoSuchKeyException.builder().statusCode(404).build());

        assertThat(s3Service.findObjectSize(KEY)).isEmpty();
    }

    @Test
    @DisplayName("본문 없는 404 S3Exception이면 빈 값을 돌려준다")
    void notFoundStatusReturnsEmpty() {
        given(s3Client.headObject(any(HeadObjectRequest.class)))
                .willThrow(S3Exception.builder().statusCode(404).build());

        assertThat(s3Service.findObjectSize(KEY)).isEmpty();
    }

    @Test
    @DisplayName("404가 아닌 S3 오류는 그대로 전파한다")
    void otherErrorsPropagate() {
        given(s3Client.headObject(any(HeadObjectRequest.class)))
                .willThrow(S3Exception.builder().statusCode(403).build());

        assertThatThrownBy(() -> s3Service.findObjectSize(KEY)).isInstanceOf(S3Exception.class);
    }
}
