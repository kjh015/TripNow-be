package com.traveler.member.domain.auth.support;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.traveler.member.domain.auth.repository.RefreshTokenRepository;
import com.traveler.member.domain.auth.repository.TokenBlacklistRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;

@ExtendWith(MockitoExtension.class)
class AuthTokenRevokerTest {

    private static final Long MEMBER_ID = 7L;
    private static final String ACCESS_TOKEN = "access-token";

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private TokenBlacklistRepository tokenBlacklistRepository;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private AuthTokenRevoker authTokenRevoker;

    @Test
    @DisplayName("revokeAll은 Redis 예외를 그대로 전파한다 (로그아웃 실패를 알린다)")
    void revokeAll_redisFailure_propagates() {
        willThrow(new RedisConnectionFailureException("down"))
                .given(refreshTokenRepository)
                .deleteByMemberId(MEMBER_ID);

        assertThatThrownBy(() -> authTokenRevoker.revokeAll(MEMBER_ID, ACCESS_TOKEN))
                .isInstanceOf(RedisConnectionFailureException.class);
    }

    @Test
    @DisplayName("이미 만료된 액세스 토큰은 블랙리스트에 등록하지 않는다")
    void revokeAll_expiredAccessToken_skipsBlacklist() {
        given(jwtTokenProvider.getRemainingExpirationTime(ACCESS_TOKEN)).willReturn(0L);

        authTokenRevoker.revokeAll(MEMBER_ID, ACCESS_TOKEN);

        verify(refreshTokenRepository).deleteByMemberId(MEMBER_ID);
        verify(tokenBlacklistRepository, never()).save(anyString(), anyLong());
    }
}
