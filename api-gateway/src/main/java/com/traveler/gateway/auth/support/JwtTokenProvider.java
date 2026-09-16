package com.traveler.gateway.auth.support;

import com.traveler.common.core.auth.AuthConstants;
import com.traveler.gateway.exception.ApiGatewayNoStackException;
import com.traveler.gateway.exception.code.ApiGatewayErrorCode;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Jwks;
import io.jsonwebtoken.security.SignatureException;
import java.security.Key;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
public class JwtTokenProvider {

    private final JwtParser jwtParser; // 파서 재사용

    public JwtTokenProvider(@Value("${app.jwt.public-jwk}") String publicJwk) {
        // 게이트웨이는 검증만 하므로 공개키만 보유한다
        PublicKey publicKey = parsePublicJwk(publicJwk);
        this.jwtParser = Jwts.parser().verifyWith(publicKey).build();
    }

    private static PublicKey parsePublicJwk(String json) {
        Key key = Jwks.parser().build().parse(json).toKey();
        if (key instanceof PrivateKey) {
            throw new IllegalStateException("app.jwt.public-jwk 에 개인키(d)가 포함되어 있습니다. 게이트웨이에는 공개 JWK만 설정하세요.");
        }
        if (key instanceof PublicKey publicKey) {
            return publicKey;
        }
        throw new IllegalStateException("app.jwt.public-jwk 가 올바른 공개 JWK가 아닙니다.");
    }

    public Mono<Claims> validateToken(String token) {
        return Mono.fromCallable(() -> jwtParser.parseSignedClaims(token).getPayload())
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
