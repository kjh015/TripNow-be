package com.traveler.gateway.filter.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.traveler.common.core.auth.UserContext;
import com.traveler.common.core.code.ErrorCode;
import com.traveler.gateway.exception.ApiGatewayNoStackException;
import com.traveler.gateway.support.auth.AuthContextManager;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

class RoleAuthorizationFilterTest {

    private static final String ADMIN = "ROLE_ADMIN";

    private final AuthContextManager authContextManager = new AuthContextManager();
    private final RoleAuthorizationFilter filterFactory = new RoleAuthorizationFilter(authContextManager);

    private final AtomicBoolean passed = new AtomicBoolean(false);
    private final GatewayFilterChain chain = exchange -> {
        passed.set(true);
        return Mono.empty();
    };

    @Test
    @DisplayName("요구 권한을 가진 사용자는 다음 필터로 넘어간다")
    void allowsRequiredRole() {
        MockServerWebExchange exchange = exchangeWith(List.of("ROLE_USER", ADMIN));

        filter(ADMIN).filter(exchange, chain).block();

        assertThat(passed).isTrue();
    }

    @Test
    @DisplayName("요구 권한이 없으면 403(FORBIDDEN)으로 막고 다음 필터를 호출하지 않는다")
    void forbidsMissingRole() {
        MockServerWebExchange exchange = exchangeWith(List.of("ROLE_USER"));

        assertThatThrownBy(() -> filter(ADMIN).filter(exchange, chain).block())
                .satisfies(error -> assertErrorCode(error, ErrorCode.FORBIDDEN, 403));

        assertThat(passed).isFalse();
    }

    @Test
    @DisplayName("인증 필터를 거치지 않아 사용자 컨텍스트가 없으면 401(UNAUTHORIZED)로 막는다")
    void rejectsMissingContext() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/admin/members"));

        assertThatThrownBy(() -> filter(ADMIN).filter(exchange, chain).block())
                .satisfies(error -> assertErrorCode(error, ErrorCode.UNAUTHORIZED, 401));

        assertThat(passed).isFalse();
    }

    @Test
    @DisplayName("라우트 설정에 requiredRole이 없으면 권한과 관계없이 500으로 막는다")
    void rejectsMisconfiguredRoute() {
        MockServerWebExchange exchange = exchangeWith(List.of(ADMIN));

        assertThatThrownBy(() -> filter(" ").filter(exchange, chain).block())
                .satisfies(error -> assertErrorCode(error, ErrorCode.INTERNAL_SERVER_ERROR, 500));

        assertThat(passed).isFalse();
    }

    private GatewayFilter filter(String requiredRole) {
        RoleAuthorizationFilter.Config config = new RoleAuthorizationFilter.Config();
        config.setRequiredRole(requiredRole);
        return filterFactory.apply(config);
    }

    private MockServerWebExchange exchangeWith(List<String> roles) {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/admin/members"));
        authContextManager.storeAuthenticatedUser(exchange, UserContext.of(1L, roles, "token"));
        return exchange;
    }

    private static void assertErrorCode(Throwable error, ErrorCode expected, int status) {
        assertThat(error).isInstanceOf(ApiGatewayNoStackException.class);
        assertThat(((ApiGatewayNoStackException) error).getCode()).isEqualTo(expected);
        assertThat(expected.getStatus()).isEqualTo(status);
    }
}
