package com.traveler.member.domain.member.dto.request;

import com.traveler.member.domain.member.enums.Provider;
import com.traveler.member.domain.member.enums.RoleType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

public final class AdminMemberRequest {
    private AdminMemberRequest() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    @Schema(description = "관리자 회원 검색 조건 (비운 조건은 적용하지 않음)")
    public record SearchDTO(
            @Schema(description = "로그인 ID·닉네임·이메일 접두 일치 검색어", example = "travel") String keyword,
            @Schema(description = "가입 경로", example = "KAKAO") Provider provider,
            @Schema(description = "보유 권한", example = "ROLE_ADMIN") RoleType role,
            @Schema(description = "탈퇴 여부", example = "false") Boolean deleted,
            @Schema(description = "가입일 시작 (한국 시간, 포함)", example = "2026-10-01")
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate joinedFrom,
            @Schema(description = "가입일 끝 (한국 시간, 포함)", example = "2026-10-31")
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate joinedTo) {}
}
