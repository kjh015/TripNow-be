package com.traveler.search.global.exception.code;

import com.traveler.common.core.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum SearchServiceErrorCode implements BaseErrorCode {
    // Domain - Post
    POST_BAD_REQUEST(400, "POST400_1", "잘못된 입력 값입니다."),
    POST_INVALID_CURSOR(400, "POST400_2", "유효하지 않은 cursor입니다."),
    POST_NOT_FOUND(404, "POST404_1", "요청한 게시물을 찾을 수 없습니다."),

    // Domain - Comment
    COMMENT_BAD_REQUEST(400, "COMMENT400_1", "잘못된 입력 값입니다."),
    COMMENT_NOT_FOUND(404, "COMMENT404_1", "요청한 댓글을 찾을 수 없습니다."),

    // Infra - Elasticsearch
    ES_INDEX_NOT_FOUND(404, "ES404_1", "요청한 인덱스를 찾을 수 없습니다."),
    ES_VERSION_CONFLICT(409, "ES409_1", "데이터 업데이트 중 버전 충돌이 발생했습니다."),

    // Infra - Kafka / Event
    TOPIC_NOT_CONFIGURED(500, "EVENT500_1", "해당 이벤트 타입에 대한 Kafka 토픽 설정이 누락되었습니다.");

    private final int status;
    private final String code;
    private final String message;
}
