package com.traveler.gateway.testsupport;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

/**
 * Rate Limiting 통합 테스트의 공통 설정. 실제 포트로 게이트웨이를 띄우고,
 * 하위 서비스(web-api-service)는 항상 200을 주는 스텁 서버로 대신한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class RateLimitTestSupport extends IntegrationTestSupport {

    protected static final String LOGIN_PATH = "/api/v1/auth/login";
    protected static final String SIGNUP_PATH = "/api/v1/members";

    // 이 클래스를 상속한 테스트 클래스들이 함께 쓴다(캐시된 컨텍스트도 이 포트를 가리킨다).
    // 클래스마다 닫으면 다음 클래스에서 연결이 거부되므로 닫지 않고 테스트 JVM 종료 때 정리되게 둔다.
    private static final DisposableServer WEB_API_STUB = HttpServer.create()
            .port(0)
            .route(routes -> routes.route(request -> true, (request, response) -> response.sendString(Mono.just("ok"))))
            .bindNow();

    @Autowired
    protected WebTestClient webTestClient;

    @DynamicPropertySource
    static void webApiStub(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.cloud.discovery.client.simple.instances.web-api-service[0].uri",
                () -> "http://localhost:" + WEB_API_STUB.port());
    }

    protected EntityExchangeResult<byte[]> login(String forwardedFor) {
        return post(LOGIN_PATH, forwardedFor);
    }

    protected EntityExchangeResult<byte[]> post(String path, String forwardedFor) {
        return webTestClient
                .post()
                .uri(path)
                .header("X-Forwarded-For", forwardedFor)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectBody()
                .returnResult();
    }
}
