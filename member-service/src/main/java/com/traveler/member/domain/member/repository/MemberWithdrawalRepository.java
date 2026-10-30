package com.traveler.member.domain.member.repository;

import com.traveler.member.domain.member.entity.MemberWithdrawal;
import com.traveler.member.domain.member.enums.ContentDeletionStatus;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface MemberWithdrawalRepository extends JpaRepository<MemberWithdrawal, Long> {

    Optional<MemberWithdrawal> findByMemberId(Long memberId);

    Page<MemberWithdrawal> findByContentDeletionStatusAndCreatedAtBefore(
            ContentDeletionStatus status, Instant createdBefore, Pageable pageable);

    // PENDING일 때만 DONE으로 바꾼다. 완료 메시지를 다시 받으면 0을 돌려준다
    @Modifying(clearAutomatically = true)
    @Query(
            "UPDATE MemberWithdrawal w SET w.contentDeletionStatus = com.traveler.member.domain.member.enums.ContentDeletionStatus.DONE,"
                    + " w.contentDeletedAt = :deletedAt"
                    + " WHERE w.memberId = :memberId AND w.contentDeletionStatus = com.traveler.member.domain.member.enums.ContentDeletionStatus.PENDING")
    int markContentDeleted(Long memberId, Instant deletedAt);
}
