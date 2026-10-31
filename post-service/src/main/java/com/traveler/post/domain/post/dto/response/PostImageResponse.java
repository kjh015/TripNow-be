package com.traveler.post.domain.post.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public final class PostImageResponse {
    private PostImageResponse() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    @Schema(name = "PostPresignedUrlResponse", description = "Presigned URL 발급 응답")
    public record PresignedUrlDTO(
            @Schema(
                            description = "S3 업로드용 URL",
                            example = "https://travel-bucket.s3.ap-northeast-2.amazonaws.com/temp/...")
                    String url,
            @Schema(
                            description = "S3 객체 키 (게시글 생성/수정 API 요청 시 images 리스트에 담아 전송)",
                            example = "images/2026/05/11/a1b2c3d4-e5f6.png")
                    String imageKey) {}
}
