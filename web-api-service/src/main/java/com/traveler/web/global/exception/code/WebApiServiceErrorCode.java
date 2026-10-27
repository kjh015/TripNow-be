package com.traveler.web.global.exception.code;

import com.traveler.common.core.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum WebApiServiceErrorCode implements BaseErrorCode {
    // FeignClient 통신 중 발생하는 에러
    SERVICE_RESPONSE_EMPTY(500, "WEB500_1", "하위 서비스가 응답 바디 없이 에러를 반환했습니다."),
    SERVICE_PARSE_ERROR(500, "WEB500_2", "하위 서비스의 에러 응답을 분석하는 데 실패했습니다."),

    // 서킷 브레이커가 열려 하위 서비스를 호출하지 않은 경우
    DOWNSTREAM_UNAVAILABLE(503, "WEB503_1", "일시적으로 서비스를 이용할 수 없습니다. 잠시 후 다시 시도해 주세요."),

    // JWT (Server Error)
    TOKEN_GENERATION_FAILED(500, "WEB500_3", "서버 오류로 토큰 생성 및 응답에 실패했습니다."),

    // Cookie Security (Server Error)
    COOKIE_SERIALIZE_ERROR(500, "WEB500_4", "보안 쿠키 직렬화 처리 중 서버 내부 오류가 발생했습니다."),
    COOKIE_DESERIALIZE_ERROR(500, "WEB500_5", "보안 쿠키 역직렬화 처리 중 서버 내부 오류가 발생했습니다."),

    // Oauth2 (Client Error)
    UNSUPPORTED_OAUTH_PROVIDER(400, "WEB400_1", "지원하지 않는 소셜 로그인 제공자입니다."),
    INVALID_AUTH_CODE(400, "WEB400_2", "유효하지 않거나 이미 만료된 인증 코드입니다."),
    INVALID_OAUTH_REQUEST_PARAM(400, "WEB400_3", "소셜 로그인 제공자 및 제공자 ID는 필수입니다."),

    // Oauth2 (Upstream / Server Error)
    INVALID_OAUTH_USER_INFO(502, "WEB502_1", "소셜 로그인 제공자로부터 필수 사용자 정보(ID)를 받지 못했습니다."),

    FILTER_UNSUPPORTED_NODE_TYPE(400, "FILTER400_1", "지원하지 않는 필터 노드 타입입니다."),
    FILTER_UNSUPPORTED_COMPARISON_OPERATOR(400, "FILTER400_2", "지원하지 않는 비교 연산자입니다."),
    FILTER_UNSUPPORTED_LOGICAL_OPERATOR(400, "FILTER400_3", "지원하지 않는 논리 연산자입니다."),
    FILTER_UNSUPPORTED_VALUE_TYPE(400, "FILTER400_4", "지원하지 않는 데이터 타입입니다."),
    DEDUP_UNSUPPORTED_MATCH_TYPE(400, "DEDUP400_2", "지원하지 않는 매치 타입입니다."), // 💡 신규 추가
    DEDUP_EXPIRATION_TIME_NON_POSITIVE(400, "DEDUP400_4", "중복제거 만료 시간은 0보다 커야 합니다.");

    private final int status;
    private final String code;
    private final String message;
}
