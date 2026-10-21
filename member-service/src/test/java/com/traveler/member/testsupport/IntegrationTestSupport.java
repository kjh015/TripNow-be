package com.traveler.member.testsupport;

import io.jsonwebtoken.Jwts;
import java.security.KeyPair;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.lifecycle.Startables;

/**
 * MySQL·Redis 컨테이너를 띄우는 통합 테스트의 공통 설정.
 *
 * <p>컨테이너는 JVM당 한 번만 띄우고 모든 하위 테스트가 공유한다(같은 설정이면 Spring 컨텍스트도 캐시된다).
 * JWT 서명 키는 실행마다 새로 만든 테스트용 키를 쓴다.
 */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
public abstract class IntegrationTestSupport {

    private static final String JWT_KID = "test-v1";
    private static final KeyPair JWT_KEY_PAIR = Jwts.SIG.ES256.keyPair().build();

    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static {
        Startables.deepStart(MYSQL, REDIS).join();
    }

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("app.jwt.private-jwk", () -> TestJwks.privateJwk(JWT_KEY_PAIR, JWT_KID));
        registry.add("app.jwt.public-jwks", () -> TestJwks.jwkSet(TestJwks.publicJwk(JWT_KEY_PAIR, JWT_KID)));
    }
}
