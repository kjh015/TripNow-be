package com.traveler.member.domain.member.service.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.traveler.member.domain.auth.repository.RefreshTokenRepository;
import com.traveler.member.domain.auth.repository.TokenBlacklistRepository;
import com.traveler.member.domain.auth.support.AuthTokenRevoker;
import com.traveler.member.domain.auth.support.JwtTokenProvider;
import com.traveler.member.domain.member.dto.response.MemberResponse;
import com.traveler.member.domain.member.entity.Member;
import com.traveler.member.domain.member.mapper.MemberMapper;
import com.traveler.member.domain.member.repository.MemberRepository;
import com.traveler.member.global.util.TokenHashUtil;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class MemberCommandServiceTest {

    private static final Long MEMBER_ID = 7L;
    private static final String ACCESS_TOKEN = "access-token";
    private static final long REMAINING_MILLIS = 60_000L;

    private final MemberRepository memberRepository = mock(MemberRepository.class);
    private final MemberMapper memberMapper = mock(MemberMapper.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
    private final TokenBlacklistRepository tokenBlacklistRepository = mock(TokenBlacklistRepository.class);
    private final JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);

    private final MemberCommandService memberCommandService = new MemberCommandService(
            memberRepository,
            memberMapper,
            passwordEncoder,
            new AuthTokenRevoker(refreshTokenRepository, tokenBlacklistRepository, jwtTokenProvider));

    private Member member;

    @BeforeEach
    void setUp() {
        member = Member.builder().loginId("traveler").nickname("여행자").build();
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
        given(memberMapper.toWithdrawDTO(any())).willAnswer(invocation -> {
            Member m = invocation.getArgument(0);
            return new MemberResponse.WithdrawDTO(m.getId(), m.getDeletedAt());
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
}
