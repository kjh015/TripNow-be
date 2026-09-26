package com.traveler.member.domain.member.service.command;

import com.traveler.member.domain.auth.support.AuthTokenRevoker;
import com.traveler.member.domain.member.dto.response.AdminMemberResponse;
import com.traveler.member.domain.member.entity.Member;
import com.traveler.member.domain.member.enums.RoleType;
import com.traveler.member.domain.member.mapper.MemberMapper;
import com.traveler.member.domain.member.repository.MemberRepository;
import com.traveler.member.global.exception.MemberServiceException;
import com.traveler.member.global.exception.code.MemberServiceErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AdminMemberCommandService {
    private final MemberRepository memberRepository;
    private final MemberMapper memberMapper;
    private final AuthTokenRevoker authTokenRevoker;

    public AdminMemberResponse.GrantAdminDTO grantAdminRole(Long memberId) {
        Member member = memberRepository
                .findByIdWithRoles(memberId)
                .orElseThrow(() -> new MemberServiceException(MemberServiceErrorCode.MEMBER_NOT_FOUND));

        member.addRole(RoleType.ROLE_ADMIN);

        return memberMapper.toGrantAdminDTO(member);
    }

    public AdminMemberResponse.DeleteDTO deleteMember(Long memberId) {
        Member member = memberRepository
                .findById(memberId)
                .orElseThrow(() -> new MemberServiceException(MemberServiceErrorCode.MEMBER_NOT_FOUND));

        member.delete();

        // 대상의 액세스 토큰은 알 수 없어 재발급만 막는다(남은 액세스 토큰은 만료 시까지 유효)
        try {
            authTokenRevoker.revokeRefreshToken(memberId);
        } catch (DataAccessException e) {
            log.warn("강제 탈퇴 회원 리프레시 토큰 삭제 실패 memberId={}", memberId, e);
        }

        return memberMapper.toDeleteDTO(member);
    }
}
