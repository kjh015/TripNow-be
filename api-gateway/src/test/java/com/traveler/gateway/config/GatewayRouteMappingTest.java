package com.traveler.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.traveler.gateway.testsupport.IntegrationTestSupport;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

/** 경로·메서드별로 처음 맞는 라우트(= 거치는 필터 묶음)를 확인한다. 라우트는 application.yml 나열 순서로 고른다. */
class GatewayRouteMappingTest extends IntegrationTestSupport {

    @Autowired
    private RouteLocator routeLocator;

    @ParameterizedTest(name = "{0} {1} → {2}")
    @DisplayName("Rate Limiting 대상은 전용 라우트로, 나머지는 기존 라우트로 간다")
    @CsvSource({
        "POST,   /api/v1/members,                       web-api-service-signup",
        "POST,   /api/v1/auth/login,                    web-api-service-login",
        "GET,    /api/v1/search/posts,                  web-api-service-search",
        "GET,    /api/v1/search/posts/autocomplete,     web-api-service-search",
        "GET,    /api/v1/search/comments/me,            web-api-service-search-me",
        "POST,   /api/v1/posts,                         web-api-service-write",
        "PATCH,  /api/v1/posts/1,                       web-api-service-write",
        "DELETE, /api/v1/posts/1,                       web-api-service-write",
        "POST,   /api/v1/comments,                      web-api-service-write",
        "DELETE, /api/v1/likes,                         web-api-service-write",
        "GET,    /api/v1/posts/images/presigned-url,    web-api-service-user",
        "PATCH,  /api/v1/members/me,                    web-api-service-user",
        "POST,   /api/v1/auth/logout,                   web-api-service-user",
        "POST,   /api/v1/auth/tokens/refresh,           web-api-service-guest",
        "POST,   /api/v1/auth/oauth2/tokens,            web-api-service-guest",
        "GET,    /api/v1/members/availability/email,    web-api-service-guest",
        "GET,    /api/v1/rankings/live,                 web-api-service-guest",
        "GET,    /api/v1/admin/members,                 web-api-service-admin",
        "DELETE, /api/v1/admin/posts/1,                 web-api-service-admin",
    })
    void matchesRoute(String method, String path, String expectedRouteId) {
        MockServerWebExchange exchange =
                MockServerWebExchange.from(MockServerHttpRequest.method(HttpMethod.valueOf(method), path));

        List<String> matched = routeLocator
                .getRoutes()
                .filterWhen(route -> route.getPredicate().apply(exchange))
                .map(Route::getId)
                .collectList()
                .block();

        assertThat(matched).isNotEmpty();
        assertThat(matched.getFirst()).isEqualTo(expectedRouteId);
    }
}
