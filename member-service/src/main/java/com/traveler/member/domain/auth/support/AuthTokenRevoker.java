package com.traveler.member.domain.auth.support;

import com.traveler.member.domain.auth.repository.RefreshTokenRepository;
import com.traveler.member.domain.auth.repository.TokenBlacklistRepository;
import com.traveler.member.global.util.TokenHashUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// Redis 작업만 하므로 트랜잭션을 걸지 않는다.
// 호출 측 트랜잭션 안에서 예외가 나도 그 트랜잭션을 rollback-only로 만들지 않도록 프록시 밖에 둔다.
@Component
@RequiredArgsConstructor
public class AuthTokenRevoker {
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenBlacklistRepository tokenBlacklistRepository;
    private final JwtTokenProvider jwtTokenProvider;

    // 리프레시 토큰 삭제 + 현재 액세스 토큰을 남은 유효 시간만큼 블랙리스트에 등록
    public void revokeAll(Long memberId, String accessToken) {
        refreshTokenRepository.deleteByMemberId(memberId);

        long remainingTime = jwtTokenProvider.getRemainingExpirationTime(accessToken);
        if (remainingTime > 0) {
            tokenBlacklistRepository.save(TokenHashUtil.hash(accessToken), remainingTime);
        }
    }

    // 액세스 토큰을 알 수 없을 때(관리자 강제 탈퇴 등): 재발급만 막는다
    public void revokeRefreshToken(Long memberId) {
        refreshTokenRepository.deleteByMemberId(memberId);
    }
}
