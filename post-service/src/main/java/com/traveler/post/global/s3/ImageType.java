package com.traveler.post.global.s3;

import java.util.Arrays;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 게시글 이미지로 허용하는 파일 형식(확장자·MIME 쌍)의 단일 출처.
 * Presigned URL 발급 요청 검증, S3 업로드 요청 생성, 게시글 저장 시 키 검증이 모두 이 목록을 참조한다.
 */
@Getter
@RequiredArgsConstructor
public enum ImageType {
    JPG("jpg", "image/jpeg"),
    JPEG("jpeg", "image/jpeg"),
    PNG("png", "image/png"),
    WEBP("webp", "image/webp");

    private final String extension;
    private final String contentType;

    public static Optional<ImageType> fromExtension(String extension) {
        return Arrays.stream(values())
                .filter(type -> type.extension.equals(extension))
                .findFirst();
    }

    public static boolean isAllowedContentType(String contentType) {
        return Arrays.stream(values()).anyMatch(type -> type.contentType.equals(contentType));
    }
}
