package com.traveler.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.traveler.gateway.testsupport.RateLimitTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.factory.RequestRateLimiterGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.EntityExchangeResult;

/** local 프로필(application-local.yml)에서는 Rate Limiting이 꺼져 Redis를 거치지 않고 통과하는지 확인한다. */
@ActiveProfiles("local")
class RateLimitLocalProfileIntegrationTest extends RateLimitTestSupport {

    @Autowired
    private RequestRateLimiterGatewayFilterFactory rateLimiterFilterFactory;

    @Test
    @DisplayName("한도(분당 10회)를 넘겨 보내도 모두 통과하고, Redis를 거치지 않아 제한 헤더가 없다")
    void passesWithoutRateLimiter() {
        for (int i = 0; i < 15; i++) {
            EntityExchangeResult<byte[]> result = login("10.9.0.1");
            assertThat(result.getStatus()).isEqualTo(HttpStatus.OK);
            assertThat(result.getResponseHeaders().getFirst("X-RateLimit-Remaining"))
                    .isNull();
        }
    }

    @Test
    @DisplayName("빈 키를 통과시키는 설정은 local 프로필에서만 켜진다")
    void allowsEmptyKeyOnlyInLocal() {
        assertThat(rateLimiterFilterFactory.isDenyEmptyKey()).isFalse();
    }
}
