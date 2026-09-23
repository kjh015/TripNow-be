package com.traveler.post.global.exception.code;

import com.traveler.common.core.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum PostServiceErrorCode implements BaseErrorCode {
    // Domain - Post
    POST_BAD_REQUEST(400, "POST400_1", "잘못된 입력 값입니다."),
    POST_NOT_FOUND(404, "POST404_1", "요청한 게시물을 찾을 수 없습니다."),
    POST_IMAGE_DUPLICATE(400, "POST400_2", "중복된 이미지 URL이 포함되어 있습니다."),
    POST_ALREADY_DELETED(400, "POST400_3", "이미 삭제된 게시물입니다."),
    POST_NOT_DELETED(400, "POST400_4", "삭제되지 않은 게시물입니다."),
    POST_IMAGE_INVALID_KEY(400, "POST400_5", "유효하지 않거나 본인이 업로드하지 않은 이미지 키입니다."),
    POST_IMAGE_NOT_UPLOADED(400, "POST400_6", "업로드되지 않은 이미지가 포함되어 있습니다."),
    POST_IMAGE_TOO_LARGE(400, "POST400_7", "이미지 크기는 10MB를 초과할 수 없습니다."),

    // Domain - Comment
    COMMENT_BAD_REQUEST(400, "COMMENT400_1", "잘못된 입력 값입니다."),
    COMMENT_NOT_FOUND(404, "COMMENT404_1", "요청한 댓글을 찾을 수 없습니다."),
    COMMENT_ALREADY_DELETED(400, "COMMENT400_2", "이미 삭제된 댓글입니다."),
    COMMENT_NOT_DELETED(400, "COMMENT400_3", "삭제되지 않은 댓글입니다."),
    COMMENT_PARENT_POST_DELETED(400, "COMMENT400_4", "삭제된 게시물의 댓글은 복구할 수 없습니다."),

    // Infra - Kafka
    TOPIC_NOT_CONFIGURED(500, "EVENT500_1", "해당 이벤트 타입에 대한 Kafka 토픽 설정이 누락되었습니다."),

    // Infra - S3
    S3_INVALID_URL(400, "S3400_1", "유효하지 않은 S3 URL 형식입니다."),
    S3_INVALID_FILE_TYPE(400, "S3400_2", "지원하지 않는 MIME 타입입니다."),
    S3_INVALID_FILE_EXTENSION(400, "S3400_3", "허용되지 않는 파일 확장자입니다."),
    S3_INVALID_KEY(400, "S3400_4", "유효하지 않은 S3 객체 키입니다."),
    S3_INVALID_CONTENT_TYPE(400, "S3400_5", "지원하지 않거나 잘못된 Content-Type입니다."),
    S3_FILE_NOT_FOUND(404, "S3404_1", "S3에서 해당 파일을 찾을 수 없습니다."),
    S3_ACCESS_DENIED(403, "S3403_1", "S3 버킷에 접근할 권한이 없습니다."),
    S3_UPLOAD_ERROR(500, "S3500_1", "S3 파일 업로드 중 오류가 발생했습니다."),
    S3_DELETE_ERROR(500, "S3500_2", "S3 파일 삭제 중 오류가 발생했습니다."),
    S3_SERVER_ERROR(500, "S3500_3", "AWS S3 서버 자체 오류가 발생했습니다."),
    S3_OPERATION_ERROR(500, "S3500_4", "S3 작업 수행 중 예상치 못한 오류가 발생했습니다.");

    private final int status;
    private final String code;
    private final String message;
}
