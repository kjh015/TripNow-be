package com.traveler.member.domain.auth.service;

import com.traveler.common.core.auth.AuthConstants;
import com.traveler.common.core.code.ErrorCode;
import com.traveler.member.domain.auth.dto.AuthTokens;
import com.traveler.member.domain.auth.dto.request.AuthRequest;
import com.traveler.member.domain.auth.dto.response.AuthResponse;
import com.traveler.member.domain.auth.mapper.AuthMapper;
import com.traveler.member.domain.auth.repository.RefreshTokenRepository;
import com.traveler.member.domain.auth.support.AuthTokenRevoker;
import com.traveler.member.domain.auth.support.JwtTokenProvider;
import com.traveler.member.domain.auth.support.SocialMemberRegistrar;
import com.traveler.member.domain.member.entity.Member;
import com.traveler.member.domain.member.enums.RoleType;
import com.traveler.member.domain.member.repository.MemberRepository;
import com.traveler.member.global.exception.MemberServiceException;
import com.traveler.member.global.exception.code.MemberServiceErrorCode;
import com.traveler.member.global.util.DuplicateKeyUtil;
import com.traveler.member.global.util.TokenHashUtil;
import io.jsonwebtoken.Claims;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {
    private final MemberRepository memberRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthTokenRevoker authTokenRevoker;
    private final AuthMapper authMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final SocialMemberRegistrar socialMemberRegistrar;
    private final PasswordEncoder passwordEncoder;

    public AuthResponse.LoginResult login(AuthRequest.LoginDTO dto) {
        Member member = memberRepository
                .findActiveByLoginIdWithRoles(dto.loginId())
                .orElseThrow(() -> new MemberServiceException(MemberServiceErrorCode.MEMBER_NOT_FOUND));

        validatePassword(dto.password(), member.getPassword());

        AuthTokens tokens = createAuthTokens(member);

        saveRefreshToken(member.getId(), tokens.refreshToken());

        return authMapper.toLoginResultDTO(tokens, member);
    }

    public void logout(Long memberId, String accessToken) {
        authTokenRevoker.revokeAll(memberId, accessToken);
    }

    public AuthResponse.LoginResult reissue(String refreshToken) {
        // Refresh Token 유효성 검증
        Claims claims = jwtTokenProvider.validateToken(refreshToken);

        String tokenType = jwtTokenProvider.getTokenType(claims);
        if (!AuthConstants.TOKEN_TYPE_REFRESH.equals(tokenType)) {
            throw new MemberServiceException(MemberServiceErrorCode.INVALID_TOKEN_TYPE);
        }

        Long userId = jwtTokenProvider.getUserId(claims);

        // 사용자 및 저장된 토큰 확인 (탈퇴 회원이면 남은 리프레시 토큰을 지우고 재발급 거부)
        Member member = memberRepository.findActiveByIdWithRoles(userId).orElseThrow(() -> {
            refreshTokenRepository.deleteByMemberId(userId);
            return new MemberServiceException(MemberServiceErrorCode.TOKEN_REISSUE_FAILED);
        });

        validateStoredRefreshToken(member.getId(), refreshToken);

        // 새로운 토큰 생성 (Rotation)
        AuthTokens tokens = createAuthTokens(member);

        // Redis 갱신
        saveRefreshToken(member.getId(), tokens.refreshToken());

        return authMapper.toLoginResultDTO(tokens, member);
    }

    // READ_COMMITTED: 동시 가입으로 저장이 실패한 뒤 재조회할 때 다른 트랜잭션이 커밋한 회원을 볼 수 있어야 함
    // (MySQL 기본값 REPEATABLE_READ는 첫 조회 시점 스냅샷을 유지해 재조회가 빈 결과를 반환)
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AuthResponse.LoginResult loginOrSignup(AuthRequest.OAuthLoginDTO dto) {

        Member member = memberRepository
                .findByProviderAndProviderIdAndIsDeletedFalse(dto.provider(), dto.providerId())
                .orElseGet(() -> signupSocialMember(dto));

        // 내부 JWT 발급
        AuthTokens tokens = createAuthTokens(member);
        saveRefreshToken(member.getId(), tokens.refreshToken());

        return authMapper.toLoginResultDTO(tokens, member);
    }

    private Member signupSocialMember(AuthRequest.OAuthLoginDTO dto) {
        try {
            // 신규 회원가입 (별도 트랜잭션)
            return socialMemberRegistrar.register(dto);
        } catch (DataIntegrityViolationException e) {
            if (!DuplicateKeyUtil.isDuplicateKeyError(e)) {
                // 중복 외의 무결성 위반 (Not Null 제약 위반, 데이터 잘림 등)
                throw new MemberServiceException(ErrorCode.DATABASE_ERROR, e);
            }
            // 같은 회원의 동시 최초 로그인: 먼저 저장된 회원으로 진행
            return memberRepository
                    .findByProviderAndProviderIdAndIsDeletedFalse(dto.provider(), dto.providerId())
                    .orElseThrow(() -> new MemberServiceException(MemberServiceErrorCode.MEMBER_ALREADY_EXISTS, e));
        }
    }

    private AuthTokens createAuthTokens(Member member) {
        List<RoleType> roles = member.getRoleTypes();
        String at = jwtTokenProvider.createAccessToken(member.getId(), roles);
        String rt = jwtTokenProvider.createRefreshToken(member.getId(), roles);
        return new AuthTokens(at, rt);
    }

    private void validatePassword(String rawPassword, String encodedPassword) {
        if (!passwordEncoder.matches(rawPassword, encodedPassword)) {
            throw new MemberServiceException(MemberServiceErrorCode.INVALID_PASSWORD);
        }
    }

    private void validateStoredRefreshToken(Long memberId, String requestToken) {
        String savedToken = refreshTokenRepository
                .findByMemberId(memberId)
                .orElseThrow(() -> new MemberServiceException(MemberServiceErrorCode.INVALID_TOKEN_TYPE));

        if (!savedToken.equals(TokenHashUtil.hash(requestToken))) {
            refreshTokenRepository.deleteByMemberId(memberId);
            throw new MemberServiceException(MemberServiceErrorCode.INVALID_TOKEN_TYPE);
        }
    }

    private void saveRefreshToken(Long memberId, String refreshToken) {
        String hashedToken = TokenHashUtil.hash(refreshToken);
        refreshTokenRepository.save(memberId, hashedToken, jwtTokenProvider.getRefreshTokenExpireTime());
    }
}
