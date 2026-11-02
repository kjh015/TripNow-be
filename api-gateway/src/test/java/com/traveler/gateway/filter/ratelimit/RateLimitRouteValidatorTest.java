package com.traveler.gateway.filter.ratelimit;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.traveler.gateway.config.RateLimitProperties;
import com.traveler.gateway.config.RateLimitProperties.KeyType;
import com.traveler.gateway.config.RateLimitProperties.Policy;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;

class RateLimitRouteValidatorTest {

    private final RateLimitProperties properties =
            new RateLimitProperties(true, Map.of("write", new Policy(KeyType.USER, 5, 10, 1)));

    @Test
    @DisplayName("라우트가 있는 정책을 쓰면 통과한다")
    void passesWhenPolicyExists() {
        RateLimitRouteValidator validator = validatorOf("RateLimitFilter=write");

        assertThatCode(validator::afterPropertiesSet).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("없는 정책 이름이면 라우트와 값을 알려 주며 기동을 실패시킨다")
    void failsWhenPolicyIsUnknown() {
        RateLimitRouteValidator validator = validatorOf("RateLimitFilter=wirte");

        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("test-route")
                .hasMessageContaining("wirte");
    }

    @Test
    @DisplayName("정책 이름 없이 필터만 쓰면 기동을 실패시킨다")
    void failsWhenPolicyIsMissing() {
        RateLimitRouteValidator validator = validatorOf("RateLimitFilter");

        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("test-route");
    }

    @Test
    @DisplayName("name/args 형식으로 policy 키에 있는 정책을 쓰면 통과한다")
    void passesWithNamedPolicyArg() {
        RateLimitRouteValidator validator = validatorOf(namedFilter("policy", "write"));

        assertThatCode(validator::afterPropertiesSet).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("name/args 형식에서 키를 잘못 쓰면 값이 있는 정책이어도 기동을 실패시킨다")
    void failsWhenPolicyArgKeyIsWrong() {
        RateLimitRouteValidator validator = validatorOf(namedFilter("polciy", "write"));

        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("test-route")
                .hasMessageContaining("polciy");
    }

    @Test
    @DisplayName("다른 필터의 인자는 검사하지 않는다")
    void ignoresOtherFilters() {
        RateLimitRouteValidator validator = validatorOf("StripPrefix=1");

        assertThatCode(validator::afterPropertiesSet).doesNotThrowAnyException();
    }

    private static FilterDefinition namedFilter(String key, String value) {
        FilterDefinition filter = new FilterDefinition();
        filter.setName("RateLimitFilter");
        filter.setArgs(Map.of(key, value));
        return filter;
    }

    private RateLimitRouteValidator validatorOf(String filter) {
        return validatorOf(new FilterDefinition(filter));
    }

    private RateLimitRouteValidator validatorOf(FilterDefinition filter) {
        RouteDefinition route = new RouteDefinition();
        route.setId("test-route");
        route.setFilters(List.of(filter));
        GatewayProperties gatewayProperties = new GatewayProperties();
        gatewayProperties.setRoutes(List.of(route));
        return new RateLimitRouteValidator(gatewayProperties, properties);
    }
}
