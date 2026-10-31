package com.traveler.member.domain.member.entity;

import com.traveler.common.db.entity.BaseEntity;
import com.traveler.member.domain.member.enums.ContentDeletionStatus;
import com.traveler.member.domain.member.enums.WithdrawalReason;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 회원 탈퇴 기록과 탈퇴 Saga의 진행 상태. 탈퇴와 같은 트랜잭션에서 만들어지고(MemberEventListener), post-service의 콘텐츠 삭제
 * 완료를 받으면 DONE이 된다. 회원 행이 탈퇴 처리 상태를 갖지 않게 따로 둔다. 탈퇴와 같은 트랜잭션에서 만들어지므로 createdAt이 탈퇴 시각이다.
 */
@SuperBuilder
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "member_withdrawal",
        indexes = {@Index(name = "idx_member_withdrawal_status", columnList = "content_deletion_status, created_at")},
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_member_withdrawal_member_id",
                    columnNames = {"member_id"})
        })
public class MemberWithdrawal extends BaseEntity {

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WithdrawalReason reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_deletion_status", nullable = false, length = 20)
    private ContentDeletionStatus contentDeletionStatus;

    private Instant contentDeletedAt;

    public static MemberWithdrawal pending(Long memberId, WithdrawalReason reason) {
        return MemberWithdrawal.builder()
                .memberId(memberId)
                .reason(reason)
                .contentDeletionStatus(ContentDeletionStatus.PENDING)
                .build();
    }
}
