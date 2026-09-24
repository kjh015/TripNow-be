package com.traveler.gateway.auth.support;

import com.traveler.common.core.auth.AuthConstants;
import com.traveler.gateway.exception.ApiGatewayNoStackException;
import com.traveler.gateway.exception.code.ApiGatewayErrorCode;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Jwk;
import io.jsonwebtoken.security.Jwks;
import io.jsonwebtoken.security.SignatureException;
import java.security.Key;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
public class JwtTokenProvider {

    private final JwtParser jwtParser; // 파서 재사용

    /**
     * @param publicJwks 공개 JWK Set(JSON). 키 로테이션 중에는 옛 키와 새 키를 함께 넣는다
     */
    public JwtTokenProvider(@Value("${app.jwt.public-jwks}") String publicJwks) {
        // 게이트웨이는 검증만 하므로 공개키만 보유하고, 토큰 헤더의 kid로 검증 키를 고른다
        Map<String, PublicKey> publicKeys = parsePublicKeys(publicJwks);
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
    }

    private static Map<String, PublicKey> parsePublicKeys(String publicJwks) {
        if (!StringUtils.hasText(publicJwks)) {
            throw new IllegalStateException("app.jwt.public-jwks 를 설정하세요.");
        }
        Collection<? extends Jwk<?>> jwks =
                Jwks.setParser().build().parse(publicJwks).getKeys();

        Map<String, PublicKey> publicKeys = new HashMap<>();
        for (Jwk<?> jwk : jwks) {
            String kid = jwk.getId();
            if (!StringUtils.hasText(kid)) {
                throw new IllegalStateException("공개 JWK에 kid가 없습니다. 모든 키에 kid를 지정하세요.");
            }
            if (publicKeys.putIfAbsent(kid, toPublicKey(jwk)) != null) {
                throw new IllegalStateException("공개 JWK의 kid가 중복됩니다. kid=" + kid);
            }
        }
        if (publicKeys.isEmpty()) {
            throw new IllegalStateException("app.jwt.public-jwks 에 사용할 수 있는 공개 JWK가 없습니다.");
        }
        return Map.copyOf(publicKeys);
    }

    private static PublicKey toPublicKey(Jwk<?> jwk) {
        Key key = jwk.toKey();
        if (key instanceof PrivateKey) {
            throw new IllegalStateException("app.jwt.public-jwks 에 개인키(d)가 포함되어 있습니다. 게이트웨이에는 공개 JWK만 설정하세요.");
        }
        if (key instanceof PublicKey publicKey) {
            return publicKey;
        }
        throw new IllegalStateException("올바른 공개 JWK가 아닙니다. kid=" + jwk.getId());
    }

    public Mono<Claims> validateToken(String token) {
        return Mono.fromCallable(() -> jwtParser.parseSignedClaims(token).getPayload())
                // I/O는 없지만 ES256 서명 검증 비용이 커서 이벤트 루프에서 분리한다
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(e -> {
                    ApiGatewayErrorCode errorCode = determineErrorCode(e);
                    // 스택 트레이스 없이 에러 시그널만 전파
                    return Mono.error(new ApiGatewayNoStackException(errorCode));
                });
    }

    public Long getUserId(Claims claims) {
        try {
            return Long.valueOf(claims.getSubject());
        } catch (NumberFormatException | NullPointerException e) {
            throw new ApiGatewayNoStackException(ApiGatewayErrorCode.INVALID_TOKEN_TYPE);
        }
    }

    public List<String> getRoles(Claims claims) {
        Object roles = claims.get(AuthConstants.CLAIM_ROLES);
        if (roles instanceof List<?> list) {
            return list.stream()
                    .filter(obj -> obj instanceof String)
                    .map(String.class::cast)
                    .toList();
        }
        return Collections.emptyList();
    }

    public String getTokenType(Claims claims) {
        Object type = claims.get(AuthConstants.CLAIM_TOKEN_TYPE);
        return type instanceof String tokenType ? tokenType : null;
    }

    private ApiGatewayErrorCode determineErrorCode(Throwable e) {
        if (e instanceof SignatureException) return ApiGatewayErrorCode.SIGNATURE_INVALID_JWT;
        if (e instanceof ExpiredJwtException) return ApiGatewayErrorCode.EXPIRED_JWT;
        if (e instanceof UnsupportedJwtException) return ApiGatewayErrorCode.UNSUPPORTED_JWT;
        if (e instanceof MalformedJwtException) return ApiGatewayErrorCode.MALFORMED_JWT;
        return ApiGatewayErrorCode.INVALID_TOKEN_TYPE;
    }
}
