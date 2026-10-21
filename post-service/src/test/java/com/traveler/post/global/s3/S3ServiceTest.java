package com.traveler.post.global.s3;

import static com.traveler.post.testsupport.fixture.PostFixture.MEMBER_1_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@ExtendWith(MockitoExtension.class)
class S3ServiceTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    @InjectMocks
    private S3Service s3Service;

    @Test
    @DisplayName("객체가 있으면 contentLength를 돌려준다")
    void returnsContentLength() {
        given(s3Client.headObject(any(HeadObjectRequest.class)))
                .willReturn(HeadObjectResponse.builder().contentLength(2048L).build());

        assertThat(s3Service.findObjectSize(MEMBER_1_KEY)).contains(2048L);
    }

    @Test
    @DisplayName("NoSuchKeyException이면 빈 값을 돌려준다")
    void noSuchKeyReturnsEmpty() {
        given(s3Client.headObject(any(HeadObjectRequest.class)))
                .willThrow(NoSuchKeyException.builder().statusCode(404).build());

        assertThat(s3Service.findObjectSize(MEMBER_1_KEY)).isEmpty();
    }

    @Test
    @DisplayName("본문 없는 404 S3Exception이면 빈 값을 돌려준다")
    void notFoundStatusReturnsEmpty() {
        given(s3Client.headObject(any(HeadObjectRequest.class)))
                .willThrow(S3Exception.builder().statusCode(404).build());

        assertThat(s3Service.findObjectSize(MEMBER_1_KEY)).isEmpty();
    }

    @Test
    @DisplayName("404가 아닌 S3 오류는 그대로 전파한다")
    void otherErrorsPropagate() {
        given(s3Client.headObject(any(HeadObjectRequest.class)))
                .willThrow(S3Exception.builder().statusCode(403).build());

        assertThatThrownBy(() -> s3Service.findObjectSize(MEMBER_1_KEY)).isInstanceOf(S3Exception.class);
    }
}
