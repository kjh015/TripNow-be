package com.traveler.gateway.auth.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.traveler.gateway.exception.ApiGatewayNoStackException;
import com.traveler.gateway.exception.code.ApiGatewayErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Jwks;
import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

    private static final KeyPair OLD_KEY = Jwts.SIG.ES256.keyPair().build();
    private static final KeyPair NEW_KEY = Jwts.SIG.ES256.keyPair().build();
    private static final KeyPair UNTRUSTED_KEY = Jwts.SIG.ES256.keyPair().build();

    private static final String JWK_SET =
            "{\"keys\":[" + publicJwk(OLD_KEY, "v1") + "," + publicJwk(NEW_KEY, "v2") + "]}";

    @Test
    @DisplayName("JWK Set의 두 키로 각각 서명한 토큰이 모두 검증된다")
    void verifiesTokensSignedByEachKeyInSet() {
        JwtTokenProvider provider = new JwtTokenProvider(JWK_SET);

        Claims oldClaims = provider.validateToken(token(OLD_KEY, "v1")).block();
        Claims newClaims = provider.validateToken(token(NEW_KEY, "v2")).block();

        assertThat(oldClaims.getSubject()).isEqualTo("42");
        assertThat(newClaims.getSubject()).isEqualTo("42");
    }

    @Test
    @DisplayName("Set에 없는 kid는 검증에 실패한다")
    void rejectsUnknownKid() {
        JwtTokenProvider provider = new JwtTokenProvider(JWK_SET);

        assertSignatureInvalid(provider, token(UNTRUSTED_KEY, "v3"));
    }

    @Test
    @DisplayName("kid가 없는 토큰은 검증에 실패한다")
    void rejectsMissingKid() {
        JwtTokenProvider provider = new JwtTokenProvider(JWK_SET);

        assertSignatureInvalid(provider, token(OLD_KEY, null));
    }

    @Test
    @DisplayName("kid는 맞지만 다른 키로 서명한 토큰은 검증에 실패한다")
    void rejectsKidSignedByAnotherKey() {
        JwtTokenProvider provider = new JwtTokenProvider(JWK_SET);

        assertSignatureInvalid(provider, token(NEW_KEY, "v1"));
    }

    @Test
    @DisplayName("공개 JWK 설정이 없으면 기동에 실패한다")
    void requiresPublicKeyConfig() {
        assertThatThrownBy(() -> new JwtTokenProvider("")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("kid가 없는 공개 JWK는 기동에 실패한다")
    void requiresKidOnEveryKey() {
        String withoutKid =
                Jwks.json(Jwks.builder().key((ECPublicKey) OLD_KEY.getPublic()).build());

        assertThatThrownBy(() -> new JwtTokenProvider("{\"keys\":[" + withoutKid + "]}"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("kid가 중복되면 기동에 실패한다")
    void rejectsDuplicateKid() {
        String duplicated = "{\"keys\":[" + publicJwk(OLD_KEY, "v1") + "," + publicJwk(NEW_KEY, "v1") + "]}";

        assertThatThrownBy(() -> new JwtTokenProvider(duplicated)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("개인키가 포함된 JWK Set은 기동에 실패한다")
    void rejectsPrivateJwk() {
        String privateJwk = Jwks.UNSAFE_JSON(Jwks.builder()
                .key((ECPrivateKey) OLD_KEY.getPrivate())
                .publicKey((ECPublicKey) OLD_KEY.getPublic())
                .id("v1")
                .build());

        assertThatThrownBy(() -> new JwtTokenProvider("{\"keys\":[" + privateJwk + "]}"))
                .isInstanceOf(IllegalStateException.class);
    }

    private static void assertSignatureInvalid(JwtTokenProvider provider, String token) {
        assertThatThrownBy(() -> provider.validateToken(token).block())
                .isInstanceOfSatisfying(ApiGatewayNoStackException.class, e -> assertThat(e.getCode())
                        .isEqualTo(ApiGatewayErrorCode.SIGNATURE_INVALID_JWT));
    }

    private static String publicJwk(KeyPair keyPair, String kid) {
        return Jwks.json(
                Jwks.builder().key((ECPublicKey) keyPair.getPublic()).id(kid).build());
    }

    private static String token(KeyPair keyPair, String kid) {
        Instant now = Instant.now();
        JwtBuilder builder = Jwts.builder()
                .subject("42")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(600)))
                .signWith(keyPair.getPrivate(), Jwts.SIG.ES256);
        if (kid != null) {
            builder.header().keyId(kid);
        }
        return builder.compact();
    }
}
