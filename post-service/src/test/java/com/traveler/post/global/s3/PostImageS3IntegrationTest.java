package com.traveler.post.global.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.traveler.post.domain.post.mapper.PostImageMapper;
import com.traveler.post.domain.post.service.PostImageService;
import com.traveler.post.domain.post.support.PostImageKey;
import com.traveler.post.global.exception.PostServiceException;
import com.traveler.post.global.exception.code.PostServiceErrorCode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** S3 API 호환 서버(LocalStack)로 발급 → 업로드 → 확정 흐름을 확인한다. 권한과 응답 시간은 AWS와 다를 수 있어 여기서 검증하지 않는다. */
@Tag("integration")
class PostImageS3IntegrationTest {

    private static final String ACCESS_KEY = "test";
    private static final String SECRET_KEY = "test";
    private static final String BUCKET = "post-image-test";
    private static final Long MEMBER_ID = 1L;
    private static final ImageType IMAGE_TYPE = ImageType.values()[0];

    private static final GenericContainer<?> LOCALSTACK = new GenericContainer<>("localstack/localstack:3.8.1")
            .withEnv("SERVICES", "s3")
            .withExposedPorts(4566)
            .waitingFor(Wait.forHttp("/_localstack/health").forPort(4566));

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4);

    private static S3Client s3Client;
    private static S3Presigner s3Presigner;
    private static S3Service s3Service;
    private static PostImageService postImageService;

    @BeforeAll
    static void startLocalStack() {
        LOCALSTACK.start();
        URI endpoint = URI.create("http://" + LOCALSTACK.getHost() + ":" + LOCALSTACK.getMappedPort(4566));
        StaticCredentialsProvider credentials =
                StaticCredentialsProvider.create(AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY));

        s3Client = S3Client.builder()
                .endpointOverride(endpoint)
                .region(Region.AP_NORTHEAST_2)
                .credentialsProvider(credentials)
                .forcePathStyle(true)
                .build();
        s3Presigner = S3Presigner.builder()
                .endpointOverride(endpoint)
                .region(Region.AP_NORTHEAST_2)
                .credentialsProvider(credentials)
                .serviceConfiguration(
                        S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
        s3Client.createBucket(builder -> builder.bucket(BUCKET));

        s3Service = new S3Service(s3Client, s3Presigner);
        ReflectionTestUtils.setField(s3Service, "bucket", BUCKET);
        ReflectionTestUtils.setField(s3Service, "presignedUrlDurationMinutes", 10);
        postImageService = new PostImageService(s3Service, mock(PostImageMapper.class), EXECUTOR);
    }

    @AfterAll
    static void stopLocalStack() {
        EXECUTOR.shutdown();
        s3Client.close();
        s3Presigner.close();
        LOCALSTACK.stop();
    }

    @Test
    @DisplayName("Pre-signed URL로 올린 파일을 확정하면 posts/에 같은 내용과 Content-Type으로 복사되고 temp/ 원본은 남는다")
    void confirmsUploadedFile() throws Exception {
        byte[] content = {1, 2, 3, 4};
        String tempKey = upload(content);

        String postKey =
                postImageService.confirmUploads(MEMBER_ID, List.of(tempKey)).getFirst();

        assertThat(postKey).startsWith("posts/1/").endsWith("." + IMAGE_TYPE.getExtension());
        ResponseBytes<GetObjectResponse> copied = read(postKey);
        assertThat(copied.asByteArray()).isEqualTo(content);
        assertThat(copied.response().contentType()).isEqualTo(IMAGE_TYPE.getContentType());
        assertThat(read(tempKey).asByteArray()).isEqualTo(content);
    }

    @Test
    @DisplayName("10장을 병렬로 확정해도 요청 순서대로 키를 돌려주고 내용이 섞이지 않는다")
    void confirmsTenFilesInParallelKeepingOrder() throws Exception {
        List<String> tempKeys = new ArrayList<>();
        for (byte i = 0; i < 10; i++) {
            tempKeys.add(upload(new byte[] {i}));
        }

        List<String> postKeys = postImageService.confirmUploads(MEMBER_ID, tempKeys);

        assertThat(postKeys).hasSize(10).doesNotHaveDuplicates();
        for (byte i = 0; i < 10; i++) {
            assertThat(read(postKeys.get(i)).asByteArray()).containsExactly(i);
        }
    }

    @Test
    @DisplayName("같은 임시 키를 두 번 확정해도 보관 객체를 공유하지 않아, 한쪽을 지워도 다른 쪽이 남는다")
    void sameTempKeyDoesNotShareObject() throws Exception {
        byte[] content = {5, 6};
        String tempKey = upload(content);

        String first =
                postImageService.confirmUploads(MEMBER_ID, List.of(tempKey)).getFirst();
        String second =
                postImageService.confirmUploads(MEMBER_ID, List.of(tempKey)).getFirst();
        postImageService.deleteCopies(List.of(second));

        assertThat(first).isNotEqualTo(second);
        assertThat(read(first).asByteArray()).isEqualTo(content);
        assertThatThrownBy(() -> read(second)).isInstanceOf(NoSuchKeyException.class);
    }

    @Test
    @DisplayName("올리지 않은 키는 S3_FILE_NOT_FOUND로 거부된다")
    void rejectsNotUploadedKey() {
        String tempKey = PostImageKey.create(MEMBER_ID, IMAGE_TYPE);

        assertThatThrownBy(() -> postImageService.confirmUploads(MEMBER_ID, List.of(tempKey)))
                .isInstanceOf(PostServiceException.class)
                .extracting("code")
                .isEqualTo(PostServiceErrorCode.S3_FILE_NOT_FOUND);
    }

    @Test
    @DisplayName("10MB를 넘는 파일은 temp/ 원본을 지우고 거부된다")
    void deletesOversizedUpload() throws Exception {
        String tempKey = upload(new byte[(int) S3Service.MAX_FILE_SIZE + 1]);

        assertThatThrownBy(() -> postImageService.confirmUploads(MEMBER_ID, List.of(tempKey)))
                .isInstanceOf(PostServiceException.class)
                .extracting("code")
                .isEqualTo(PostServiceErrorCode.S3_FILE_TOO_LARGE);

        assertThatThrownBy(() -> read(tempKey)).isInstanceOf(NoSuchKeyException.class);
    }

    private static String upload(byte[] content) throws Exception {
        String tempKey = PostImageKey.create(MEMBER_ID, IMAGE_TYPE);
        String url = s3Service.generatePresignedUrl(tempKey, IMAGE_TYPE.getContentType());

        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<Void> response = client.send(
                    HttpRequest.newBuilder(URI.create(url))
                            .header("Content-Type", IMAGE_TYPE.getContentType())
                            .PUT(HttpRequest.BodyPublishers.ofByteArray(content))
                            .build(),
                    HttpResponse.BodyHandlers.discarding());
            assertThat(response.statusCode()).isEqualTo(200);
        }
        return tempKey;
    }

    private static ResponseBytes<GetObjectResponse> read(String key) {
        return s3Client.getObjectAsBytes(builder -> builder.bucket(BUCKET).key(key));
    }
}
