package com.traveler.member.domain.member.service.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.traveler.member.domain.member.mapper.MemberMapper;
import com.traveler.member.domain.member.repository.MemberRepository;
import com.traveler.member.domain.member.repository.MemberWithdrawalRepository;
import com.traveler.member.global.exception.MemberServiceException;
import com.traveler.member.global.exception.code.MemberServiceErrorCode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class AdminMemberQueryServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private MemberWithdrawalRepository memberWithdrawalRepository;

    @Mock
    private MemberMapper memberMapper;

    @InjectMocks
    private AdminMemberQueryService adminMemberQueryService;

    @Test
    @DisplayName("탈퇴 정리 미완료 조회는 허용한 정렬 필드를 리포지토리로 넘긴다")
    void pendingWithdrawalsAllowedSort() {
        Pageable pageable = PageRequest.of(0, 20, Sort.by("createdAt", "memberId"));
        given(memberWithdrawalRepository.findByContentDeletionStatusAndCreatedAtBefore(any(), any(), any()))
                .willReturn(new PageImpl<>(List.of(), pageable, 0));

        assertThat(adminMemberQueryService.getPendingWithdrawals(pageable).content())
                .isEmpty();
    }

    @Test
    @DisplayName("탈퇴 정리 미완료 조회의 허용하지 않은 정렬 필드는 조회 없이 거부한다")
    void pendingWithdrawalsRejectsUnknownSort() {
        Pageable pageable = PageRequest.of(0, 20, Sort.by("unknown"));

        assertThatThrownBy(() -> adminMemberQueryService.getPendingWithdrawals(pageable))
                .isInstanceOfSatisfying(MemberServiceException.class, e -> assertThat(e.getCode())
                        .isEqualTo(MemberServiceErrorCode.MEMBER_INVALID_SORT));
        verifyNoInteractions(memberWithdrawalRepository);
    }
}
