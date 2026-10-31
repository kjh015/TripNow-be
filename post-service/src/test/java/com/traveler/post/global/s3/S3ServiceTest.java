package com.traveler.post.global.s3;

import static com.traveler.post.testsupport.fixture.PostFixture.MEMBER_1_KEY;
import static com.traveler.post.testsupport.fixture.PostFixture.MEMBER_1_TEMP_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.traveler.post.global.exception.PostServiceException;
import com.traveler.post.global.exception.code.PostServiceErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
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
    @DisplayName("객체가 있으면 메타데이터를 돌려준다")
    void returnsMetadata() {
        given(s3Client.headObject(any(HeadObjectRequest.class)))
                .willReturn(HeadObjectResponse.builder().contentLength(2048L).build());

        assertThat(s3Service.headObject(MEMBER_1_KEY).contentLength()).isEqualTo(2048L);
    }

    @Test
    @DisplayName("NoSuchKeyException이면 S3_FILE_NOT_FOUND로 바꿔 던진다")
    void noSuchKeyThrowsNotFound() {
        given(s3Client.headObject(any(HeadObjectRequest.class)))
                .willThrow(NoSuchKeyException.builder().statusCode(404).build());

        assertThatThrownBy(() -> s3Service.headObject(MEMBER_1_KEY))
                .isInstanceOf(PostServiceException.class)
                .extracting("code")
                .isEqualTo(PostServiceErrorCode.S3_FILE_NOT_FOUND);
    }

    @Test
    @DisplayName("본문 없는 404 S3Exception이면 S3_FILE_NOT_FOUND로 바꿔 던진다")
    void notFoundStatusThrowsNotFound() {
        given(s3Client.headObject(any(HeadObjectRequest.class)))
                .willThrow(S3Exception.builder().statusCode(404).build());

        assertThatThrownBy(() -> s3Service.headObject(MEMBER_1_KEY))
                .isInstanceOf(PostServiceException.class)
                .extracting("code")
                .isEqualTo(PostServiceErrorCode.S3_FILE_NOT_FOUND);
    }

    @Test
    @DisplayName("404가 아닌 S3 오류는 그대로 전파한다")
    void otherErrorsPropagate() {
        given(s3Client.headObject(any(HeadObjectRequest.class)))
                .willThrow(S3Exception.builder().statusCode(403).build());

        assertThatThrownBy(() -> s3Service.headObject(MEMBER_1_KEY)).isInstanceOf(S3Exception.class);
    }

    @Test
    @DisplayName("복사는 같은 버킷 안에서 한다")
    void copiesWithinBucket() {
        ReflectionTestUtils.setField(s3Service, "bucket", "bucket");

        s3Service.copyObject(MEMBER_1_TEMP_KEY, MEMBER_1_KEY);

        ArgumentCaptor<CopyObjectRequest> request = ArgumentCaptor.forClass(CopyObjectRequest.class);
        verify(s3Client).copyObject(request.capture());
        assertThat(request.getValue().sourceBucket()).isEqualTo("bucket");
        assertThat(request.getValue().destinationBucket()).isEqualTo("bucket");
        assertThat(request.getValue().sourceKey()).isEqualTo(MEMBER_1_TEMP_KEY);
        assertThat(request.getValue().destinationKey()).isEqualTo(MEMBER_1_KEY);
    }
}
