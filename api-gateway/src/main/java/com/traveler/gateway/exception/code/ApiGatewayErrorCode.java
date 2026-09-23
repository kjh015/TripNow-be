package com.traveler.gateway.exception.code;

import com.traveler.common.core.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ApiGatewayErrorCode implements BaseErrorCode {
    // Routing
    ROUTE_NOT_FOUND(404, "GATEWAY404_1", "요청하신 경로를 찾을 수 없거나 직접 호출이 제한된 경로입니다."),
    // JWT
    EXPIRED_JWT(401, "GATEWAY401_1", "만료된 JWT 토큰입니다."),
    UNSUPPORTED_JWT(401, "GATEWAY401_2", "지원되지 않는 JWT 토큰입니다."),
    SIGNATURE_INVALID_JWT(401, "GATEWAY401_3", "유효하지 않은 JWT 시그니처입니다."),
    JWT_NOT_FOUND(401, "GATEWAY401_4", "JWT 토큰을 찾을 수 없습니다."),
    INVALID_TOKEN_TYPE(401, "GATEWAY401_6", "토큰 타입이 일치하지 않거나 비어있습니다."),
    MALFORMED_JWT(401, "GATEWAY401_7", "잘못된 구조의 JWT 토큰입니다."),
    BLACKLISTED_TOKEN(401, "GATEWAY401_8", "로그아웃된 토큰입니다. 다시 로그인해주세요."),
    // Auth Store
    AUTH_STORE_UNAVAILABLE(503, "GATEWAY503_1", "인증 정보를 확인할 수 없습니다. 잠시 후 다시 시도해주세요.");

    private final int status;
    private final String code;
    private final String message;
}
