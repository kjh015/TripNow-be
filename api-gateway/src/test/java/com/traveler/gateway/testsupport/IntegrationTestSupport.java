package com.traveler.gateway.testsupport;

import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;

/**
 * Redis(토큰 블랙리스트) 컨테이너를 띄우는 통합 테스트의 공통 설정.
 * JWT 검증 키는 실행마다 새로 만든 테스트용 공개키를 쓴다.
 */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
public abstract class IntegrationTestSupport {

    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static {
        REDIS.start();
    }

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        String publicJwk = TestJwks.publicJwk(Jwts.SIG.ES256.keyPair().build(), "test-v1");
        registry.add("app.jwt.public-jwks", () -> TestJwks.jwkSet(publicJwk));
    }
}
