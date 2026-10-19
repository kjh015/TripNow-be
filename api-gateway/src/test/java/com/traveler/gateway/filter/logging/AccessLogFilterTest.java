package com.traveler.gateway.filter.logging;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.traveler.common.core.auth.UserContext;
import com.traveler.common.core.code.ErrorCode;
import com.traveler.gateway.exception.ApiGatewayNoStackException;
import com.traveler.gateway.handler.GlobalErrorWebExceptionHandler;
import com.traveler.gateway.support.auth.AuthContextManager;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

@ExtendWith(OutputCaptureExtension.class)
class AccessLogFilterTest {

    private final AuthContextManager authContextManager = new AuthContextManager();
    private final AccessLogFilter filter = new AccessLogFilter(authContextManager);

    @Test
    @DisplayName("응답이 끝나면 쿼리스트링을 뺀 경로와 상태·라우트·사용자·ALB가 붙인 IP를 한 줄로 남긴다")
    void logsOneLinePerRequest(CapturedOutput output) {
        MockServerWebExchange exchange =
                MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/posts/3?token=secret")
                        .header("X-Forwarded-For", "1.1.1.1, 10.0.0.5"));
        exchange.getAttributes()
                .put(
                        ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR,
                        Route.async()
                                .id("web-api-service-user")
                                .uri(URI.create("lb://web-api-service"))
                                .predicate(ex -> true)
                                .build());

        filter.filter(exchange, ex -> {
                    // 라우트 필터(JwtAuthenticationFilter)가 인증 컨텍스트를 저장한 상황
                    authContextManager.storeAuthenticatedUser(ex, UserContext.of(42L, List.of("ROLE_USER"), "at"));
                    ex.getResponse().setStatusCode(HttpStatus.OK);
                    return ex.getResponse().setComplete();
                })
                .block();

        List<String> lines = output.getAll()
                .lines()
                .filter(line -> line.contains("[Access]"))
                .toList();
        assertThat(lines).hasSize(1);
        assertThat(lines.getFirst())
                .contains("method=GET path=/api/v1/posts/3 status=200")
                .contains("route=web-api-service-user userId=42 clientIp=10.0.0.5")
                .doesNotContain("secret");
    }

    @Test
    @DisplayName("에러 핸들러가 응답을 쓰는 경우에도 최종 상태 코드로 한 줄만 남긴다")
    void logsErrorStatusSetBeforeCommit(CapturedOutput output) {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/admin/members"));

        filter.filter(exchange, ex -> {
                    ex.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                    return ex.getResponse().setComplete();
                })
                .block();

        assertThat(output.getAll().lines().filter(line -> line.contains("[Access]")))
                .singleElement()
                .asString()
                .contains("status=403")
                .contains("userId=-");
    }

    @Test
    @DisplayName("필터 체인에서 예외가 나면 에러 핸들러가 정한 상태 코드로 한 줄만 남긴다")
    void logsStatusDecidedByErrorHandler(CapturedOutput output) {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/posts/me"));
        GlobalErrorWebExceptionHandler errorHandler = new GlobalErrorWebExceptionHandler(new ObjectMapper());

        // 실제 WebFlux는 필터 체인 바깥에서 에러 핸들러를 실행하고 응답을 비동기로 쓴다.
        // delay로 "에러 신호가 필터를 지나간 뒤에 응답이 커밋되는" 순서를 재현한다.
        filter.filter(exchange, ex -> Mono.error(new ApiGatewayNoStackException(ErrorCode.UNAUTHORIZED)))
                .onErrorResume(e -> Mono.delay(Duration.ofMillis(20)).then(errorHandler.handle(exchange, e)))
                .block();

        assertThat(output.getAll().lines().filter(line -> line.contains("[Access]")))
                .singleElement()
                .asString()
                .contains("status=401");
    }

    @Test
    @DisplayName("응답 없이 취소되면(클라이언트 연결 종료) 상태 없이 한 줄을 남긴다")
    void logsCancelledRequest(CapturedOutput output) {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/posts/3"));

        Disposable subscription = filter.filter(exchange, ex -> Mono.never()).subscribe();
        subscription.dispose();

        assertThat(output.getAll().lines().filter(line -> line.contains("[Access]")))
                .singleElement()
                .asString()
                .contains("status=-");
    }

    @Test
    @DisplayName("actuator 경로는 접근 로그를 남기지 않는다")
    void skipsActuator(CapturedOutput output) {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/actuator/health"));

        filter.filter(exchange, ex -> ex.getResponse().setComplete()).block();

        assertThat(output.getAll()).doesNotContain("[Access]");
    }
}
