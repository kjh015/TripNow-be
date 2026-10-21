package com.traveler.gateway.testsupport;

import io.jsonwebtoken.security.Jwks;
import java.security.KeyPair;
import java.security.interfaces.ECPublicKey;

/** 테스트용 JWT 공개키를 설정값(JSON) 형태로 만드는 도우미. 키 자체는 테스트마다 Jwts.SIG.ES256.keyPair()로 새로 만든다. */
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
}
