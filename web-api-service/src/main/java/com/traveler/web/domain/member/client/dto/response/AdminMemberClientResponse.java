package com.traveler.web.domain.member.client.dto.response;

import com.traveler.web.domain.member.enums.ContentDeletionStatus;
import com.traveler.web.domain.member.enums.Gender;
import com.traveler.web.domain.member.enums.RoleType;
import com.traveler.web.domain.member.enums.WithdrawalReason;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class AdminMemberClientResponse {
    private AdminMemberClientResponse() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public record GrantAdminDTO(Long memberId, List<RoleType> roles) {}

    public record DeleteDTO(Long memberId, Instant deletedAt) {}

    public record ListDTO(
            Long memberId,
            String loginId,
            String email,
            String nickname,
            Gender gender,
            LocalDate birthDate,
            List<RoleType> roles,
            Instant createdAt) {}

    public record DetailDTO(
            Long memberId,
            String loginId,
            String email,
            String nickname,
            Gender gender,
            LocalDate birthDate,
            List<RoleType> roles,
            Instant createdAt,
            Instant updatedAt,
            boolean isDeleted,
            Instant deletedAt,
            ContentDeletionStatus contentDeletionStatus,
            Instant contentDeletedAt) {}

    public record PendingWithdrawalDTO(
            Long memberId, Instant withdrawnAt, WithdrawalReason reason, ContentDeletionStatus contentDeletionStatus) {}
}
