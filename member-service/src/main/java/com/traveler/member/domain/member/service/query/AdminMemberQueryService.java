package com.traveler.member.domain.member.service.query;

import com.traveler.common.api.converter.PageConverter;
import com.traveler.common.core.response.PageResponse;
import com.traveler.member.domain.member.dto.response.AdminMemberResponse;
import com.traveler.member.domain.member.entity.Member;
import com.traveler.member.domain.member.entity.MemberWithdrawal;
import com.traveler.member.domain.member.enums.ContentDeletionStatus;
import com.traveler.member.domain.member.mapper.MemberMapper;
import com.traveler.member.domain.member.repository.MemberRepository;
import com.traveler.member.domain.member.repository.MemberWithdrawalRepository;
import com.traveler.member.global.exception.MemberServiceException;
import com.traveler.member.global.exception.code.MemberServiceErrorCode;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminMemberQueryService {
    // 재시도·DLT를 거쳐도 이 시간 안에 끝나지 않으면 정리가 멈춘 것으로 본다
    private static final Duration CONTENT_DELETION_TIMEOUT = Duration.ofMinutes(30);

    private final MemberRepository memberRepository;
    private final MemberWithdrawalRepository memberWithdrawalRepository;
    private final MemberMapper memberMapper;

    public PageResponse<AdminMemberResponse.ListDTO> getMembers(Pageable pageable) {
        Page<Member> members = memberRepository.findAll(pageable);
        return PageConverter.toPageResponse(members, memberMapper::toListDTO);
    }

    public AdminMemberResponse.DetailDTO getMember(Long memberId) {
        Member member = memberRepository
                .findByIdWithRoles(memberId)
                .orElseThrow(() -> new MemberServiceException(MemberServiceErrorCode.MEMBER_NOT_FOUND));

        MemberWithdrawal withdrawal =
                memberWithdrawalRepository.findByMemberId(memberId).orElse(null);
        return memberMapper.toDetailDTO(member, withdrawal);
    }

    // 탈퇴 후 기준 시간이 지나도 콘텐츠 삭제가 끝나지 않은 탈퇴 건. 처리가 DLT로 갔거나 메시지가 도착하지 않은 경우
    public PageResponse<AdminMemberResponse.PendingWithdrawalDTO> getPendingWithdrawals(Pageable pageable) {
        Page<MemberWithdrawal> withdrawals = memberWithdrawalRepository.findByContentDeletionStatusAndCreatedAtBefore(
                ContentDeletionStatus.PENDING, Instant.now().minus(CONTENT_DELETION_TIMEOUT), pageable);
        return PageConverter.toPageResponse(withdrawals, memberMapper::toPendingWithdrawalDTO);
    }
}
