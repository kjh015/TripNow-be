package com.traveler.member.global.exception.code;

import com.traveler.common.core.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum MemberServiceErrorCode implements BaseErrorCode {
    // Domain - Member
    MEMBER_BAD_REQUEST(400, "MEMBER400_1", "잘못된 입력 값입니다."),
    MEMBER_ALREADY_DELETED(400, "MEMBER400_2", "이미 삭제된 회원입니다."),
    INVALID_PASSWORD(400, "MEMBER400_3", "비밀번호가 일치하지 않습니다."),
    PASSWORD_SAME_AS_OLD(400, "MEMBER400_4", "새 비밀번호는 기존 비밀번호와 다르게 설정해야 합니다."),
    MEMBER_NOT_FOUND(404, "MEMBER404_1", "요청한 회원을 찾을 수 없습니다."),
    MEMBER_EXISTS_LOGINID(409, "MEMBER409_1", "이미 존재하는 아이디입니다."),
    MEMBER_EXISTS_EMAIL(409, "MEMBER409_2", "이미 존재하는 이메일입니다."),
    MEMBER_EXISTS_NICKNAME(409, "MEMBER409_3", "이미 존재하는 닉네임입니다."),
    MEMBER_ALREADY_EXISTS(409, "MEMBER409_4", "이미 존재하는 회원 정보입니다."),

    // JWT
    EXPIRED_JWT(401, "AUTH401_1", "만료된 JWT 토큰입니다."),
    UNSUPPORTED_JWT(401, "AUTH401_2", "지원되지 않는 JWT 토큰입니다."),
    SIGNATURE_INVALID_JWT(401, "AUTH401_3", "유효하지 않은 JWT 시그니처입니다."),
    JWT_NOT_FOUND(401, "AUTH401_4", "JWT 토큰을 찾을 수 없습니다."),
    AUTHENTICATION_FAILED(401, "AUTH401_5", "인증에 실패했습니다."),
    INVALID_TOKEN_TYPE(401, "AUTH401_6", "토큰 타입이 일치하지 않거나 비어있습니다."),
    MALFORMED_JWT(401, "AUTH401_7", "잘못된 구조의 JWT 토큰입니다."),
    BLACKLISTED_TOKEN(401, "AUTH401_8", "로그아웃된 토큰입니다. 다시 로그인해주세요."),
    TOKEN_REISSUE_FAILED(401, "AUTH401_9", "토큰 재발급에 실패했습니다."),
    REFRESH_TOKEN_NOT_FOUND(404, "AUTH404_2", "존재하지 않거나 만료된 리프레시 토큰입니다."),

    // Infra - Kafka
    TOPIC_NOT_CONFIGURED(500, "EVENT500_1", "해당 이벤트 타입에 대한 Kafka 토픽 설정이 누락되었습니다."),
    ;

    private final int status;
    private final String code;
    private final String message;
}
