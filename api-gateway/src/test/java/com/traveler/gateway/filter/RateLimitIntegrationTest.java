package com.traveler.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.traveler.gateway.config.RateLimitProperties;
import com.traveler.gateway.config.RateLimitProperties.KeyType;
import com.traveler.gateway.config.RateLimitProperties.Policy;
import com.traveler.gateway.filter.ratelimit.RateLimitFilter;
import com.traveler.gateway.testsupport.RateLimitTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.factory.RequestRateLimiterGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.EntityExchangeResult;

/**
 * 로그인 라우트의 Rate Limiting(IP당 분당 10회)을 실제 Redis로 확인한다.
 * 버킷이 IP별이므로 테스트마다 다른 IP를 써서 서로 영향을 주지 않게 한다.
 */
class RateLimitIntegrationTest extends RateLimitTestSupport {

    private static final int LOGIN_LIMIT = 10;
    private static final int LOGIN_REQUESTED_TOKENS = 6;

    @Autowired
    private RequestRateLimiterGatewayFilterFactory rateLimiterFilterFactory;

    @Autowired
    private RateLimitFilter rateLimitFilter;

    @Autowired
    private RateLimitProperties rateLimitProperties;

    @Test
    @DisplayName("한도 안에서는 하위 서비스로 보내고 남은 토큰 헤더를 붙인다. 한도를 넘으면 429")
    void rejectsAfterLimit() {
        for (int i = 0; i < LOGIN_LIMIT; i++) {
            EntityExchangeResult<byte[]> result = login("10.1.0.1");
            assertThat(result.getStatus()).isEqualTo(HttpStatus.OK);
            assertThat(result.getResponseHeaders().getFirst("X-RateLimit-Remaining"))
                    .isNotNull();
        }

        EntityExchangeResult<byte[]> rejected = login("10.1.0.1");
        assertThat(rejected.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        // 보내는 동안 초당 1토큰이 다시 채워질 수 있어 0으로 고정하지 않는다. 요청 1건에 필요한 6개보다 적으면 거절이 맞다
        assertThat(Integer.parseInt(rejected.getResponseHeaders().getFirst("X-RateLimit-Remaining")))
                .isLessThan(LOGIN_REQUESTED_TOKENS);
    }

    @Test
    @DisplayName("X-Forwarded-For 앞쪽 값을 바꿔 보내도 ALB가 붙인 마지막 값이 같으면 같은 한도로 센다")
    void spoofedForwardedForDoesNotBypass() {
        for (int i = 0; i < LOGIN_LIMIT; i++) {
            assertThat(login("6.6.6." + i + ", 10.2.0.1").getStatus()).isEqualTo(HttpStatus.OK);
        }

        assertThat(login("7.7.7.7, 10.2.0.1").getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    @DisplayName("한 IP가 한도를 넘어도 다른 IP는 막히지 않는다")
    void limitsPerIp() {
        for (int i = 0; i <= LOGIN_LIMIT; i++) {
            login("10.3.0.1");
        }

        assertThat(login("10.3.0.2").getStatus()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("회원가입과 로그인은 한도를 따로 센다 (로그인을 다 써도 가입은 된다)")
    void signupAndLoginCountedSeparately() {
        for (int i = 0; i < LOGIN_LIMIT; i++) {
            assertThat(login("10.4.0.1").getStatus()).isEqualTo(HttpStatus.OK);
        }
        assertThat(login("10.4.0.1").getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        assertThat(post(SIGNUP_PATH, "10.4.0.1").getStatus()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("local 외 환경은 빈 키를 막는다(deny-empty-key 기본값 true)")
    void deniesEmptyKeyByDefault() {
        assertThat(rateLimiterFilterFactory.isDenyEmptyKey()).isTrue();
    }

    @Test
    @DisplayName("yml의 정책 4개가 기준·한도 그대로 바인딩된다")
    void bindsPolicies() {
        assertThat(rateLimitProperties.policies())
                .containsOnlyKeys("signup", "login", "write", "search")
                .containsEntry("signup", new Policy(KeyType.IP, 1, 60, 6))
                .containsEntry("login", new Policy(KeyType.IP, 1, 60, 6))
                .containsEntry("write", new Policy(KeyType.USER, 5, 10, 1))
                .containsEntry("search", new Policy(KeyType.IP, 20, 20, 1));
    }

    @Test
    @DisplayName("없는 정책 이름이면 필터를 만들 때 실패한다 (그 라우트는 빠지고 GatewayRouteMappingTest가 잡는다)")
    void unknownPolicyFails() {
        RateLimitFilter.Config config = new RateLimitFilter.Config();
        config.setPolicy("wirte");

        assertThatThrownBy(() -> rateLimitFilter.apply(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("wirte");
    }
}
