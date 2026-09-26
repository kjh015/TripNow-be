package com.traveler.member.domain.member.service.command;

import com.traveler.common.core.code.ErrorCode;
import com.traveler.member.domain.auth.support.AuthTokenRevoker;
import com.traveler.member.domain.member.dto.request.MemberRequest;
import com.traveler.member.domain.member.dto.response.MemberResponse;
import com.traveler.member.domain.member.entity.Member;
import com.traveler.member.domain.member.enums.RoleType;
import com.traveler.member.domain.member.mapper.MemberMapper;
import com.traveler.member.domain.member.repository.MemberRepository;
import com.traveler.member.global.exception.MemberServiceException;
import com.traveler.member.global.exception.code.MemberServiceErrorCode;
import com.traveler.member.global.util.DuplicateKeyUtil;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class MemberCommandService {
    private final MemberRepository memberRepository;
    private final MemberMapper memberMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokenRevoker authTokenRevoker;

    public MemberResponse.SignUpDTO signUp(MemberRequest.SignUpDTO dto) {
        if (memberRepository.existsByLoginIdAndIsDeletedFalse(dto.loginId())) {
            throw new MemberServiceException(MemberServiceErrorCode.MEMBER_EXISTS_LOGINID);
        }

        if (memberRepository.existsByEmailAndIsDeletedFalse(dto.email())) {
            throw new MemberServiceException(MemberServiceErrorCode.MEMBER_EXISTS_EMAIL);
        }

        if (memberRepository.existsByNicknameAndIsDeletedFalse(dto.nickname())) {
            throw new MemberServiceException(MemberServiceErrorCode.MEMBER_EXISTS_NICKNAME);
        }

        String encodedPassword = passwordEncoder.encode(dto.password());

        Member member = memberMapper.toCreateEntity(dto, encodedPassword);
        member.addRole(RoleType.ROLE_USER);
        try {
            // DB 강제 플러시 저장
            memberRepository.saveAndFlush(member);
        } catch (DataIntegrityViolationException e) {
            if (DuplicateKeyUtil.isDuplicateKeyError(e)) {
                throw new MemberServiceException(MemberServiceErrorCode.MEMBER_ALREADY_EXISTS, e);
            }
            // 중복 외의 무결성 위반 (Not Null 제약 위반, 데이터 잘림 등)
            throw new MemberServiceException(ErrorCode.DATABASE_ERROR, e);
        }
        return memberMapper.toSignUpDTO(member);
    }

    public MemberResponse.WithdrawDTO withdraw(Long memberId, String accessToken) {
        Member member = memberRepository
                .findById(memberId)
                .orElseThrow(() -> new MemberServiceException(MemberServiceErrorCode.MEMBER_NOT_FOUND));
        member.delete();

        // Redis 실패로 탈퇴를 막지 않는다. reissue가 탈퇴 회원을 거부하므로 남은 액세스 토큰은 만료 시까지만 유효
        try {
            authTokenRevoker.revokeAll(memberId, accessToken);
        } catch (DataAccessException e) {
            log.warn("탈퇴 회원 토큰 무효화 실패 memberId={}", memberId, e);
        }
        return memberMapper.toWithdrawDTO(member);
    }

    public MemberResponse.UpdateDTO updateMember(MemberRequest.UpdateDTO dto, Long memberId) {
        Member member = memberRepository
                .findById(memberId)
                .orElseThrow(() -> new MemberServiceException(MemberServiceErrorCode.MEMBER_NOT_FOUND));

        if (!Objects.equals(member.getNickname(), dto.nickname())
                && memberRepository.existsByNicknameAndIsDeletedFalse(dto.nickname())) {
            throw new MemberServiceException(MemberServiceErrorCode.MEMBER_EXISTS_NICKNAME);
        }

        member.update(dto.nickname(), dto.gender(), dto.birthDate());
        return memberMapper.toUpdateDTO(member);
    }

    public MemberResponse.UpdatePasswordDTO updatePassword(MemberRequest.UpdatePasswordDTO dto, Long memberId) {
        Member member = memberRepository
                .findById(memberId)
                .orElseThrow(() -> new MemberServiceException(MemberServiceErrorCode.MEMBER_NOT_FOUND));

        if (!passwordEncoder.matches(dto.curPassword(), member.getPassword())) {
            throw new MemberServiceException(MemberServiceErrorCode.INVALID_PASSWORD);
        }

        if (passwordEncoder.matches(dto.newPassword(), member.getPassword())) {
            throw new MemberServiceException(MemberServiceErrorCode.PASSWORD_SAME_AS_OLD);
        }

        String encodedNewPassword = passwordEncoder.encode(dto.newPassword());
        member.updatePassword(encodedNewPassword);

        return memberMapper.toUpdatePasswordDTO(member);
    }
}
