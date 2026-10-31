package com.traveler.member.domain.member.dto.response;

import com.traveler.member.domain.member.enums.ContentDeletionStatus;
import com.traveler.member.domain.member.enums.Gender;
import com.traveler.member.domain.member.enums.RoleType;
import com.traveler.member.domain.member.enums.WithdrawalReason;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class AdminMemberResponse {
    private AdminMemberResponse() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    @Schema(description = "관리자 권한 부여 응답 DTO")
    public record GrantAdminDTO(
            @Schema(description = "대상 회원 식별자", example = "1") Long memberId,
            @Schema(description = "부여 후 최종 권한 목록", example = "[\"ROLE_USER\", \"ROLE_ADMIN\"]") List<RoleType> roles) {}

    @Schema(description = "회원 강제 탈퇴 응답 DTO")
    public record DeleteDTO(
            @Schema(description = "강제 탈퇴된 회원 식별자", example = "1") Long memberId,
            @Schema(description = "탈퇴 처리 일시", example = "2023-11-01T10:00:00Z") Instant deletedAt) {}

    @Schema(description = "관리자용 회원 목록 조회 응답 DTO")
    public record ListDTO(
            @Schema(description = "회원 식별자", example = "1") Long memberId,
            @Schema(description = "로그인 ID", example = "traveler123") String loginId,
            @Schema(description = "이메일", example = "traveler@example.com") String email,
            @Schema(description = "닉네임", example = "트래블러") String nickname,
            @Schema(description = "성별", example = "MALE") Gender gender,
            @Schema(description = "생년월일", example = "1995-05-05") LocalDate birthDate,
            @Schema(description = "보유 권한", example = "[\"ROLE_USER\"]") List<RoleType> roles,
            @Schema(description = "가입 일시", example = "2023-10-01T10:00:00Z") Instant createdAt) {}

    @Schema(description = "관리자용 회원 상세 조회 응답 DTO")
    public record DetailDTO(
            @Schema(description = "회원 식별자", example = "1") Long memberId,
            @Schema(description = "로그인 ID", example = "traveler123") String loginId,
            @Schema(description = "이메일", example = "traveler@example.com") String email,
            @Schema(description = "닉네임", example = "트래블러") String nickname,
            @Schema(description = "성별", example = "MALE") Gender gender,
            @Schema(description = "생년월일", example = "1995-05-05") LocalDate birthDate,
            @Schema(description = "보유 권한", example = "[\"ROLE_USER\", \"ROLE_ADMIN\"]") List<RoleType> roles,
            @Schema(description = "가입 일시", example = "2023-10-01T10:00:00Z") Instant createdAt,
            @Schema(description = "마지막 수정 일시", example = "2023-10-15T10:00:00Z") Instant updatedAt,
            @Schema(description = "탈퇴 여부", example = "false") boolean isDeleted,
            @Schema(description = "탈퇴 일시 (미탈퇴 시 null)", example = "null") Instant deletedAt,
            @Schema(description = "탈퇴 후 콘텐츠 정리 상태 (PENDING: 정리 대기, DONE: 정리 완료, 미탈퇴 시 null)", example = "null")
                    ContentDeletionStatus contentDeletionStatus,
            @Schema(description = "콘텐츠 정리 완료 일시 (미완료 시 null)", example = "null") Instant contentDeletedAt) {}

    @Schema(description = "콘텐츠 정리가 끝나지 않은 탈퇴 회원 응답 DTO")
    public record PendingWithdrawalDTO(
            @Schema(description = "회원 식별자", example = "1") Long memberId,
            @Schema(description = "탈퇴 일시", example = "2023-11-01T10:00:00Z") Instant withdrawnAt,
            @Schema(description = "탈퇴 사유 (USER: 본인, ADMIN: 관리자)", example = "USER") WithdrawalReason reason,
            @Schema(description = "콘텐츠 정리 상태", example = "PENDING") ContentDeletionStatus contentDeletionStatus) {}
}
