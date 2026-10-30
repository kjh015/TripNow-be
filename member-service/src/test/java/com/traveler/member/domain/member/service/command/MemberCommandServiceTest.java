package com.traveler.member.domain.member.service.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.traveler.common.core.code.ErrorCode;
import com.traveler.member.domain.auth.repository.RefreshTokenRepository;
import com.traveler.member.domain.auth.repository.TokenBlacklistRepository;
import com.traveler.member.domain.auth.support.AuthTokenRevoker;
import com.traveler.member.domain.auth.support.JwtTokenProvider;
import com.traveler.member.domain.member.dto.event.MemberEvent;
import com.traveler.member.domain.member.dto.message.MemberMessage;
import com.traveler.member.domain.member.dto.request.MemberRequest;
import com.traveler.member.domain.member.dto.response.MemberResponse;
import com.traveler.member.domain.member.entity.Member;
import com.traveler.member.domain.member.enums.Gender;
import com.traveler.member.domain.member.enums.RoleType;
import com.traveler.member.domain.member.enums.WithdrawalReason;
import com.traveler.member.domain.member.mapper.MemberMapper;
import com.traveler.member.domain.member.repository.MemberRepository;
import com.traveler.member.global.exception.MemberServiceException;
import com.traveler.member.global.exception.code.MemberServiceErrorCode;
import com.traveler.member.global.util.TokenHashUtil;
import com.traveler.member.testsupport.fixture.MemberFixture;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;

class MemberCommandServiceTest {

    private static final Long MEMBER_ID = 7L;
    private static final String ACCESS_TOKEN = "access-token";
    private static final long REMAINING_MILLIS = 60_000L;
    private static final MemberRequest.SignUpDTO SIGN_UP = new MemberRequest.SignUpDTO(
            "newbie", "password1!", "newbie@example.com", "새내기", Gender.MALE, LocalDate.of(1995, 5, 5));

    private final MemberRepository memberRepository = mock(MemberRepository.class);
    private final MemberMapper memberMapper = mock(MemberMapper.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
    private final TokenBlacklistRepository tokenBlacklistRepository = mock(TokenBlacklistRepository.class);
    private final JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

    private final MemberCommandService memberCommandService = new MemberCommandService(
            memberRepository,
            memberMapper,
            passwordEncoder,
            new AuthTokenRevoker(refreshTokenRepository, tokenBlacklistRepository, jwtTokenProvider),
            eventPublisher);

    private Member member;

    @BeforeEach
    void setUp() {
        member = MemberFixture.member(MEMBER_ID);
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
        given(memberMapper.toWithdrawDTO(any())).willAnswer(invocation -> {
            Member m = invocation.getArgument(0);
            return new MemberResponse.WithdrawDTO(m.getId(), m.getDeletedAt());
        });
        given(memberMapper.toWithdrawnEvent(any(), any())).willAnswer(invocation -> {
            Member m = invocation.getArgument(0);
            return new MemberEvent.Withdrawn(
                    new MemberMessage.WithdrawnDTO(m.getId(), m.getDeletedAt(), invocation.getArgument(1)));
        });
        given(jwtTokenProvider.getRemainingExpirationTime(ACCESS_TOKEN)).willReturn(REMAINING_MILLIS);
    }

    @Test
    @DisplayName("탈퇴하면 리프레시 토큰을 지우고 현재 액세스 토큰을 블랙리스트에 등록한다")
    void withdraw_revokesTokens() {
        MemberResponse.WithdrawDTO result = memberCommandService.withdraw(MEMBER_ID, ACCESS_TOKEN);

        assertThat(member.isDeleted()).isTrue();
        assertThat(result.memberId()).isEqualTo(MEMBER_ID);
        verify(refreshTokenRepository).deleteByMemberId(MEMBER_ID);
        verify(tokenBlacklistRepository).save(TokenHashUtil.hash(ACCESS_TOKEN), REMAINING_MILLIS);
    }

    @Test
    @DisplayName("탈퇴하면 본인 탈퇴(USER) 이벤트를 발행한다")
    void withdraw_publishesWithdrawnEvent() {
        memberCommandService.withdraw(MEMBER_ID, ACCESS_TOKEN);

        verify(eventPublisher)
                .publishEvent(new MemberEvent.Withdrawn(
                        new MemberMessage.WithdrawnDTO(MEMBER_ID, member.getDeletedAt(), WithdrawalReason.USER)));
    }

    @Test
    @DisplayName("이미 탈퇴한 회원이 다시 탈퇴 요청하면 MEMBER_ALREADY_DELETED로 실패하고 이벤트를 다시 보내지 않는다")
    void withdraw_alreadyDeleted_fails() {
        member.delete();

        assertThatThrownBy(() -> memberCommandService.withdraw(MEMBER_ID, ACCESS_TOKEN))
                .isInstanceOf(MemberServiceException.class)
                .extracting("code")
                .isEqualTo(MemberServiceErrorCode.MEMBER_ALREADY_DELETED);
        verify(eventPublisher, never()).publishEvent(any(Object.class));
        verify(refreshTokenRepository, never()).deleteByMemberId(anyLong());
    }

    @Test
    @DisplayName("리프레시 토큰 삭제 중 Redis 예외가 나도 탈퇴는 성공한다")
    void withdraw_redisFailureOnRefreshToken_stillSucceeds() {
        willThrow(new RedisConnectionFailureException("down"))
                .given(refreshTokenRepository)
                .deleteByMemberId(MEMBER_ID);

        MemberResponse.WithdrawDTO result = memberCommandService.withdraw(MEMBER_ID, ACCESS_TOKEN);

        assertThat(member.isDeleted()).isTrue();
        assertThat(result.memberId()).isEqualTo(MEMBER_ID);
        verify(tokenBlacklistRepository, never()).save(anyString(), anyLong());
    }

    @Test
    @DisplayName("블랙리스트 등록 중 Redis 예외가 나도 탈퇴는 성공한다")
    void withdraw_redisFailureOnBlacklist_stillSucceeds() {
        willThrow(new RedisConnectionFailureException("down"))
                .given(tokenBlacklistRepository)
                .save(anyString(), anyLong());

        MemberResponse.WithdrawDTO result = memberCommandService.withdraw(MEMBER_ID, ACCESS_TOKEN);

        assertThat(member.isDeleted()).isTrue();
        assertThat(result.memberId()).isEqualTo(MEMBER_ID);
        verify(refreshTokenRepository).deleteByMemberId(MEMBER_ID);
    }

    @Test
    @DisplayName("가입 시 비밀번호를 인코딩하고 ROLE_USER를 부여해 저장한다")
    void signUp_savesEncodedPasswordWithUserRole() {
        Member newMember = Member.builder().loginId("newbie").build();
        given(passwordEncoder.encode("password1!")).willReturn("encoded");
        given(memberMapper.toCreateEntity(SIGN_UP, "encoded")).willReturn(newMember);

        memberCommandService.signUp(SIGN_UP);

        assertThat(newMember.getRoleTypes()).containsExactly(RoleType.ROLE_USER);
        verify(memberRepository).saveAndFlush(newMember);
    }

    @Test
    @DisplayName("탈퇴하지 않은 회원과 아이디·이메일·닉네임이 겹치면 각각의 오류로 거부한다")
    void signUp_rejectsDuplicates() {
        given(memberRepository.existsByLoginIdAndIsDeletedFalse(SIGN_UP.loginId()))
                .willReturn(true);
        assertSignUpFails(MemberServiceErrorCode.MEMBER_EXISTS_LOGINID);

        given(memberRepository.existsByLoginIdAndIsDeletedFalse(SIGN_UP.loginId()))
                .willReturn(false);
        given(memberRepository.existsByEmailAndIsDeletedFalse(SIGN_UP.email())).willReturn(true);
        assertSignUpFails(MemberServiceErrorCode.MEMBER_EXISTS_EMAIL);

        given(memberRepository.existsByEmailAndIsDeletedFalse(SIGN_UP.email())).willReturn(false);
        given(memberRepository.existsByNicknameAndIsDeletedFalse(SIGN_UP.nickname()))
                .willReturn(true);
        assertSignUpFails(MemberServiceErrorCode.MEMBER_EXISTS_NICKNAME);

        verify(memberRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("선검사를 통과했지만 저장 시 유니크 제약에 걸리면(동시 가입) 중복 회원 오류로 바꾼다")
    void signUp_duplicateKeyOnSave() {
        given(memberMapper.toCreateEntity(any(), any()))
                .willReturn(Member.builder().build());
        given(memberRepository.saveAndFlush(any()))
                .willThrow(new DataIntegrityViolationException(
                        "duplicate", new SQLIntegrityConstraintViolationException("Duplicate entry", "23000", 1062)));

        assertSignUpFails(MemberServiceErrorCode.MEMBER_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("중복 키가 아닌 무결성 위반은 DB 오류로 바꾼다")
    void signUp_otherIntegrityViolation() {
        given(memberMapper.toCreateEntity(any(), any()))
                .willReturn(Member.builder().build());
        given(memberRepository.saveAndFlush(any()))
                .willThrow(new DataIntegrityViolationException(
                        "not null", new SQLException("Column cannot be null", "23000", 1048)));

        assertSignUpFails(ErrorCode.DATABASE_ERROR);
    }

    private void assertSignUpFails(Object expectedCode) {
        assertThatThrownBy(() -> memberCommandService.signUp(SIGN_UP))
                .isInstanceOf(MemberServiceException.class)
                .extracting("code")
                .isEqualTo(expectedCode);
    }
}
