package com.traveler.member.domain.auth.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.traveler.member.domain.member.enums.RoleType;
import com.traveler.member.global.exception.MemberServiceException;
import com.traveler.member.global.exception.code.MemberServiceErrorCode;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Jwks;
import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

    private static final long ONE_HOUR = 3_600_000L;

    private static final KeyPair OLD_KEY = Jwts.SIG.ES256.keyPair().build();
    private static final KeyPair NEW_KEY = Jwts.SIG.ES256.keyPair().build();
    private static final KeyPair UNTRUSTED_KEY = Jwts.SIG.ES256.keyPair().build();

    private static final String JWK_SET = jwkSet(publicJwk(OLD_KEY, "v1"), publicJwk(NEW_KEY, "v2"));

    @Test
    @DisplayName("발급한 토큰 헤더에 개인 JWK의 kid가 들어간다")
    void putsKidInHeader() {
        JwtTokenProvider provider = provider(privateJwk(NEW_KEY, "v2"), JWK_SET);

        String token = provider.createAccessToken(42L, List.of(RoleType.ROLE_USER));

        assertThat(Jwts.parser()
                        .verifyWith(NEW_KEY.getPublic())
                        .build()
                        .parseSignedClaims(token)
                        .getHeader()
                        .getKeyId())
                .isEqualTo("v2");
    }

    @Test
    @DisplayName("서명 키를 교체한 뒤에도 옛 키로 발급한 토큰을 검증한다")
    void verifiesTokenIssuedBeforeRotation() {
        String oldToken =
                provider(privateJwk(OLD_KEY, "v1"), JWK_SET).createRefreshToken(42L, List.of(RoleType.ROLE_USER));
        JwtTokenProvider rotated = provider(privateJwk(NEW_KEY, "v2"), JWK_SET);

        assertThat(rotated.validateToken(oldToken).getSubject()).isEqualTo("42");
        assertThat(rotated.getRemainingExpirationTime(oldToken)).isPositive();
    }

    @Test
    @DisplayName("Set에 없는 kid로 서명한 토큰은 검증에 실패한다")
    void rejectsUnknownKid() {
        String token = provider(privateJwk(UNTRUSTED_KEY, "v3"), jwkSet(publicJwk(UNTRUSTED_KEY, "v3")))
                .createAccessToken(42L, List.of(RoleType.ROLE_USER));
        JwtTokenProvider provider = provider(privateJwk(NEW_KEY, "v2"), JWK_SET);

        assertThatThrownBy(() -> provider.validateToken(token))
                .isInstanceOfSatisfying(MemberServiceException.class, e -> assertThat(e.getCode())
                        .isEqualTo(MemberServiceErrorCode.SIGNATURE_INVALID_JWT));
    }

    @Test
    @DisplayName("개인 JWK의 kid가 Set에 없으면 기동에 실패한다")
    void requiresSigningKidInSet() {
        assertThatThrownBy(() -> provider(privateJwk(UNTRUSTED_KEY, "v3"), JWK_SET))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("개인 JWK와 Set의 같은 kid 공개키가 다르면 기동에 실패한다")
    void requiresMatchingPublicKey() {
        assertThatThrownBy(() -> provider(privateJwk(UNTRUSTED_KEY, "v1"), JWK_SET))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("개인 JWK에 kid가 없으면 기동에 실패한다")
    void requiresKidOnPrivateJwk() {
        assertThatThrownBy(() -> provider(privateJwk(OLD_KEY, null), JWK_SET))
                .isInstanceOf(IllegalStateException.class);
    }

    private static JwtTokenProvider provider(String privateJwk, String publicJwks) {
        return new JwtTokenProvider(privateJwk, publicJwks, ONE_HOUR, ONE_HOUR);
    }

    private static String jwkSet(String... jwks) {
        return "{\"keys\":[" + String.join(",", jwks) + "]}";
    }

    private static String publicJwk(KeyPair keyPair, String kid) {
        return Jwks.json(
                Jwks.builder().key((ECPublicKey) keyPair.getPublic()).id(kid).build());
    }

    private static String privateJwk(KeyPair keyPair, String kid) {
        var builder =
                Jwks.builder().key((ECPrivateKey) keyPair.getPrivate()).publicKey((ECPublicKey) keyPair.getPublic());
        if (kid != null) {
            builder.id(kid);
        }
        return Jwks.UNSAFE_JSON(builder.build());
    }
}
