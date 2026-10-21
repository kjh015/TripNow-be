package com.traveler.member.testsupport;

import io.jsonwebtoken.security.Jwks;
import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;

/** 테스트용 JWT 키를 설정값(JSON) 형태로 만드는 도우미. 키 자체는 테스트마다 Jwts.SIG.ES256.keyPair()로 새로 만든다. */
public final class TestJwks {

    private TestJwks() {}

    /** 공개 JWK 여러 개를 app.jwt.public-jwks 형식({"keys":[...]})으로 묶는다. */
    public static String jwkSet(String... jwks) {
        return "{\"keys\":[" + String.join(",", jwks) + "]}";
    }

    /** 공개키만 담은 JWK */
    public static String publicJwk(KeyPair keyPair, String kid) {
        return Jwks.json(
                Jwks.builder().key((ECPublicKey) keyPair.getPublic()).id(kid).build());
    }

    /**
     * 개인키를 담은 JWK (app.jwt.private-jwk 형식). kid가 null이면 kid 없이 만든다.
     * Jwks.json()은 개인키 값을 가려서 출력하므로 UNSAFE_JSON으로 직렬화한다.
     */
    public static String privateJwk(KeyPair keyPair, String kid) {
        var builder =
                Jwks.builder().key((ECPrivateKey) keyPair.getPrivate()).publicKey((ECPublicKey) keyPair.getPublic());
        if (kid != null) {
            builder.id(kid);
        }
        return Jwks.UNSAFE_JSON(builder.build());
    }
}
