package com.traveler.member.domain.auth.support;

import com.traveler.common.core.auth.AuthConstants;
import com.traveler.member.domain.member.enums.RoleType;
import com.traveler.member.global.exception.MemberServiceException;
import com.traveler.member.global.exception.code.MemberServiceErrorCode;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Jwk;
import io.jsonwebtoken.security.Jwks;
import io.jsonwebtoken.security.PrivateJwk;
import io.jsonwebtoken.security.SignatureException;
import java.security.Key;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Slf4j
public class JwtTokenProvider {
    private final JwtParser jwtParser;
    private final PrivateKey privateKey;
    private final String keyId;
    private final long accessTokenExpireTime;
    private final long refreshTokenExpireTime;

    /**
     * @param privateJwk 서명에 쓰는 현재 개인 JWK(JSON). kid가 public-jwks에 있어야 한다
     * @param publicJwks 검증에 쓰는 공개 JWK Set(JSON). 키 로테이션 중에는 옛 키와 새 키를 함께 넣는다
     */
    public JwtTokenProvider(
            @Value("${app.jwt.private-jwk}") String privateJwk,
            @Value("${app.jwt.public-jwks}") String publicJwks,
            @Value("${app.jwt.access-expiration}") long accessTokenExpireTime,
            @Value("${app.jwt.refresh-expiration}") long refreshTokenExpireTime) {
        PrivateJwk<?, ?, ?> jwk = parsePrivateJwk(privateJwk);
        Map<String, PublicKey> publicKeys = parsePublicKeys(publicJwks);
        // 새로 발급한 토큰을 스스로 검증하지 못하는 설정은 기동 시점에 막는다
        if (!jwk.toKeyPair().getPublic().equals(publicKeys.get(jwk.getId()))) {
            throw new IllegalStateException(
                    "app.jwt.public-jwks 에 app.jwt.private-jwk 와 같은 kid의 공개키가 없거나 키가 다릅니다. kid=" + jwk.getId());
        }

        this.privateKey = jwk.toKeyPair().getPrivate();
        this.keyId = jwk.getId();
        // 토큰 헤더의 kid로 검증 키를 고르므로 키 교체 전에 발급한 토큰도 검증할 수 있다
        this.jwtParser = Jwts.parser()
                .keyLocator(new LocatorAdapter<Key>() {
                    @Override
                    protected Key locate(JwsHeader header) {
                        String kid = header.getKeyId();
                        PublicKey key = kid == null ? null : publicKeys.get(kid);
                        if (key == null) {
                            throw new SignatureException("신뢰하는 공개키 중 kid와 일치하는 키가 없습니다. kid=" + kid);
                        }
                        return key;
                    }
                })
                .build();
        this.accessTokenExpireTime = accessTokenExpireTime;
        this.refreshTokenExpireTime = refreshTokenExpireTime;
    }

    private static PrivateJwk<?, ?, ?> parsePrivateJwk(String json) {
        Jwk<?> jwk = Jwks.parser().build().parse(json);
        if (!(jwk instanceof PrivateJwk<?, ?, ?> privateJwk)) {
            throw new IllegalStateException("app.jwt.private-jwk 에 개인키(d)가 없습니다. 공개 JWK를 설정하지 않았는지 확인하세요.");
        }
        if (!StringUtils.hasText(privateJwk.getId())) {
            throw new IllegalStateException("app.jwt.private-jwk 에 kid가 없습니다. public-jwks와 같은 kid를 지정하세요.");
        }
        return privateJwk;
    }

    private static Map<String, PublicKey> parsePublicKeys(String publicJwks) {
        if (!StringUtils.hasText(publicJwks)) {
            throw new IllegalStateException("app.jwt.public-jwks 를 설정하세요.");
        }
        Map<String, PublicKey> publicKeys = new HashMap<>();
        for (Jwk<?> jwk : Jwks.setParser().build().parse(publicJwks).getKeys()) {
            String kid = jwk.getId();
            if (!StringUtils.hasText(kid)) {
                throw new IllegalStateException("공개 JWK에 kid가 없습니다. 모든 키에 kid를 지정하세요.");
            }
            if (!(jwk.toKey() instanceof PublicKey publicKey)) {
                throw new IllegalStateException("app.jwt.public-jwks 에는 공개 JWK만 넣으세요. kid=" + kid);
            }
            if (publicKeys.putIfAbsent(kid, publicKey) != null) {
                throw new IllegalStateException("공개 JWK의 kid가 중복됩니다. kid=" + kid);
            }
        }
        return Map.copyOf(publicKeys);
    }

    public String createAccessToken(Long userId, List<RoleType> roles) {
        return createToken(userId, roles, AuthConstants.TOKEN_TYPE_ACCESS, accessTokenExpireTime);
    }

    public String createRefreshToken(Long userId, List<RoleType> roles) {
        return createToken(userId, roles, AuthConstants.TOKEN_TYPE_REFRESH, refreshTokenExpireTime);
    }

    private String createToken(Long userId, List<RoleType> roles, String tokenType, long validity) {
        Instant now = Instant.now();
        List<String> roleNames = roles.stream().map(RoleType::name).toList();

        JwtBuilder builder = Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(AuthConstants.CLAIM_ROLES, roleNames)
                .claim(AuthConstants.CLAIM_TOKEN_TYPE, tokenType)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(validity)))
                .signWith(privateKey, Jwts.SIG.ES256);

        builder.header().keyId(keyId); // 게이트웨이가 kid로 검증 키를 고른다 (키 로테이션 대비)
        return builder.compact();
    }

    public Claims validateToken(String token) {
        try {
            return jwtParser.parseSignedClaims(token).getPayload();
        } catch (ExpiredJwtException e) {
            log.warn("Expired JWT token: {}", e.getMessage());
            throw new MemberServiceException(MemberServiceErrorCode.EXPIRED_JWT);
        } catch (JwtException | IllegalArgumentException e) {
            log.error("Invalid JWT token: {}", e.getMessage());
            throw new MemberServiceException(determineErrorCode(e));
        }
    }

    public Long getUserId(Claims claims) {
        try {
            return Long.valueOf(claims.getSubject());
        } catch (NumberFormatException | NullPointerException e) {
            throw new MemberServiceException(MemberServiceErrorCode.INVALID_TOKEN_TYPE);
        }
    }

    public List<RoleType> getRoles(Claims claims) {
        Object roles = claims.get(AuthConstants.CLAIM_ROLES);

        if (roles instanceof List<?> list) {
            return list.stream()
                    .filter(String.class::isInstance)
                    .map(obj -> {
                        try {
                            return RoleType.valueOf((String) obj);
                        } catch (IllegalArgumentException e) {
                            log.error("Invalid role name in JWT claim: {}", obj);
                            throw new MemberServiceException(MemberServiceErrorCode.INVALID_TOKEN_TYPE);
                        }
                    })
                    .toList();
        }
        return Collections.emptyList();
    }

    public String getTokenType(Claims claims) {
        return claims.get(AuthConstants.CLAIM_TOKEN_TYPE, String.class);
    }

    public long getRemainingExpirationTime(String token) {
        try {
            Claims claims = jwtParser.parseSignedClaims(token).getPayload();
            long expirationTime = claims.getExpiration().getTime();
            long currentTime = System.currentTimeMillis();
            return Math.max(0, expirationTime - currentTime);
        } catch (JwtException | IllegalArgumentException e) {
            return 0;
        }
    }

    private MemberServiceErrorCode determineErrorCode(Throwable e) {
        if (e instanceof SignatureException) return MemberServiceErrorCode.SIGNATURE_INVALID_JWT;
        if (e instanceof UnsupportedJwtException) return MemberServiceErrorCode.UNSUPPORTED_JWT;
        if (e instanceof MalformedJwtException) return MemberServiceErrorCode.MALFORMED_JWT;
        return MemberServiceErrorCode.INVALID_TOKEN_TYPE;
    }

    public long getRefreshTokenExpireTime() {
        return this.refreshTokenExpireTime;
    }
}
