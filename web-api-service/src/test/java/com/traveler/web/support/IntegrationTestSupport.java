package com.traveler.web.support;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;

/**
 * Redis 컨테이너를 띄우는 통합 테스트의 공통 설정.
 * 하위 서비스(Feign) 호출은 기동 시점에 일어나지 않으므로 컨테이너를 두지 않는다.
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
}
