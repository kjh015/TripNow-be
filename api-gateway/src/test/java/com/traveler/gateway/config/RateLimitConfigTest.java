package com.traveler.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.traveler.common.core.auth.UserContext;
import com.traveler.gateway.support.auth.AuthContextManager;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class RateLimitConfigTest {

    private final AuthContextManager authContextManager = new AuthContextManager();
    private final RateLimitConfig config = new RateLimitConfig(new RateLimitProperties(true, Map.of()));
    private final KeyResolver ipKeyResolver = config.ipKeyResolver();
    private final KeyResolver userOrIpKeyResolver = config.userOrIpKeyResolver(authContextManager);

    @Test
    @DisplayName("IP 키는 X-Forwarded-For의 마지막 값(ALB가 붙인 값)을 쓰고, 클라이언트가 앞에 넣은 값은 무시한다")
    void ipKeyUsesLastForwardedValue() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/login").header("X-Forwarded-For", "6.6.6.6, 10.0.0.5"));

        assertThat(ipKeyResolver.resolve(exchange).block()).isEqualTo("ip:10.0.0.5");
    }

    @Test
    @DisplayName("인증된 요청은 userId를 키로 쓴다")
    void userKeyWhenAuthenticated() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/likes").header("X-Forwarded-For", "10.0.0.5"));
        authContextManager.storeAuthenticatedUser(exchange, UserContext.of(42L, List.of("ROLE_USER"), "at"));

        assertThat(userOrIpKeyResolver.resolve(exchange).block()).isEqualTo("user:42");
    }

    @Test
    @DisplayName("인증 정보가 없으면 IP를 키로 쓴다")
    void ipKeyWhenAnonymous() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/likes").header("X-Forwarded-For", "10.0.0.5"));

        assertThat(userOrIpKeyResolver.resolve(exchange).block()).isEqualTo("ip:10.0.0.5");
    }

    @Test
    @DisplayName("켜져 있으면 인증 정보·헤더가 없어도 키가 비지 않는다 (빈 키는 local 외 환경에서 403)")
    void keyIsNeverEmptyWhenEnabled() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/likes"));

        assertThat(ipKeyResolver.resolve(exchange).block()).isNotBlank();
        assertThat(userOrIpKeyResolver.resolve(exchange).block()).isNotBlank();
    }

    @Test
    @DisplayName("꺼져 있으면 두 키 모두 비어 있다")
    void keyIsEmptyWhenDisabled() {
        RateLimitConfig disabled = new RateLimitConfig(new RateLimitProperties(false, Map.of()));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/likes").header("X-Forwarded-For", "10.0.0.5"));
        authContextManager.storeAuthenticatedUser(exchange, UserContext.of(42L, List.of("ROLE_USER"), "at"));

        assertThat(disabled.ipKeyResolver().resolve(exchange).blockOptional()).isEmpty();
        assertThat(disabled.userOrIpKeyResolver(authContextManager)
                        .resolve(exchange)
                        .blockOptional())
                .isEmpty();
    }
}
