package com.traveler.member.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.traveler.common.core.code.ErrorCode;
import com.traveler.member.domain.auth.dto.AuthTokens;
import com.traveler.member.domain.auth.dto.request.AuthRequest;
import com.traveler.member.domain.auth.dto.response.AuthResponse;
import com.traveler.member.domain.auth.mapper.AuthMapper;
import com.traveler.member.domain.auth.repository.RefreshTokenRepository;
import com.traveler.member.domain.auth.repository.TokenBlacklistRepository;
import com.traveler.member.domain.auth.support.JwtTokenProvider;
import com.traveler.member.domain.auth.support.SocialMemberRegistrar;
import com.traveler.member.domain.member.entity.Member;
import com.traveler.member.domain.member.enums.Provider;
import com.traveler.member.domain.member.repository.MemberRepository;
import com.traveler.member.global.exception.MemberServiceException;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class AuthServiceTest {

    private static final Long EXISTING_MEMBER_ID = 7L;
    private static final String PROVIDER_ID = "1234567890";

    private final MemberRepository memberRepository = mock(MemberRepository.class);
    private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
    private final TokenBlacklistRepository tokenBlacklistRepository = mock(TokenBlacklistRepository.class);
    private final AuthMapper authMapper = mock(AuthMapper.class);
    private final JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
    private final SocialMemberRegistrar socialMemberRegistrar = mock(SocialMemberRegistrar.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

    private final AuthService authService = new AuthService(
            memberRepository,
            refreshTokenRepository,
            tokenBlacklistRepository,
            authMapper,
            jwtTokenProvider,
            socialMemberRegistrar,
            passwordEncoder);

    private final AuthRequest.OAuthLoginDTO dto =
            new AuthRequest.OAuthLoginDTO(Provider.KAKAO, PROVIDER_ID, "traveler@example.com");

    @BeforeEach
    void setUp() {
        given(jwtTokenProvider.createAccessToken(anyLong(), any())).willReturn("access-token");
        given(jwtTokenProvider.createRefreshToken(anyLong(), any())).willReturn("refresh-token");
        given(authMapper.toLoginResultDTO(any(), any())).willAnswer(invocation -> {
            AuthTokens tokens = invocation.getArgument(0);
            Member member = invocation.getArgument(1);
            return new AuthResponse.LoginResult(
                    tokens, new AuthResponse.LoginDTO(member.getId(), member.getNickname()));
        });
    }

    @Test
    @DisplayName("동시 최초 로그인으로 저장이 중복 키로 실패하면 재조회한 회원으로 토큰을 발급한다")
    void loginOrSignup_duplicateKey_reloadsMemberAndIssuesTokens() {
        Member existing = member(EXISTING_MEMBER_ID);
        given(memberRepository.findByProviderAndProviderIdAndIsDeletedFalse(Provider.KAKAO, PROVIDER_ID))
                .willReturn(Optional.empty())
                .willReturn(Optional.of(existing));
        given(socialMemberRegistrar.register(dto)).willThrow(integrityViolation(1062, "23000"));

        AuthResponse.LoginResult result = authService.loginOrSignup(dto);

        assertThat(result.loginInfo().memberId()).isEqualTo(EXISTING_MEMBER_ID);
        assertThat(result.tokens().accessToken()).isEqualTo("access-token");
        assertThat(result.tokens().refreshToken()).isEqualTo("refresh-token");
        verify(refreshTokenRepository).save(eq(EXISTING_MEMBER_ID), anyString(), anyLong());
    }

    @Test
    @DisplayName("중복 키가 아닌 무결성 위반은 재조회하지 않고 DATABASE_ERROR로 변환한다")
    void loginOrSignup_otherIntegrityViolation_throwsDatabaseError() {
        given(memberRepository.findByProviderAndProviderIdAndIsDeletedFalse(Provider.KAKAO, PROVIDER_ID))
                .willReturn(Optional.empty());
        // MySQL 1048: Column cannot be null
        given(socialMemberRegistrar.register(dto)).willThrow(integrityViolation(1048, "23000"));

        assertThatThrownBy(() -> authService.loginOrSignup(dto))
                .isInstanceOf(MemberServiceException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.DATABASE_ERROR);
        verify(refreshTokenRepository, never()).save(anyLong(), anyString(), anyLong());
    }

    @Test
    @DisplayName("기존 회원이 없으면 새로 가입한 회원으로 토큰을 발급한다")
    void loginOrSignup_newMember_registersAndIssuesTokens() {
        Member created = member(8L);
        given(memberRepository.findByProviderAndProviderIdAndIsDeletedFalse(Provider.KAKAO, PROVIDER_ID))
                .willReturn(Optional.empty());
        given(socialMemberRegistrar.register(dto)).willReturn(created);

        AuthResponse.LoginResult result = authService.loginOrSignup(dto);

        assertThat(result.loginInfo().memberId()).isEqualTo(8L);
        verify(refreshTokenRepository).save(eq(8L), anyString(), anyLong());
    }

    private Member member(Long id) {
        Member member = Member.builder()
                .provider(Provider.KAKAO)
                .providerId(PROVIDER_ID)
                .email("traveler@example.com")
                .build();
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private DataIntegrityViolationException integrityViolation(int vendorCode, String sqlState) {
        SQLException cause = new SQLIntegrityConstraintViolationException("constraint violation", sqlState, vendorCode);
        return new DataIntegrityViolationException("could not execute statement", cause);
    }
}
