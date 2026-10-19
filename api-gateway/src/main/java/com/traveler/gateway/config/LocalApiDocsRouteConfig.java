package com.traveler.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 로컬에서만 하위 서비스의 API 문서(springdoc)를 게이트웨이로 노출한다.
 * yml의 routes는 리스트라 local 프로필에서 덮어쓰면 기본 라우트가 모두 사라지므로, 코드로 라우트를 추가한다.
 */
@Configuration
@Profile("local")
public class LocalApiDocsRouteConfig {

    @Bean
    public RouteLocator apiDocsRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("post-service-docs", r -> r.path("/v1/posts/v3/api-docs/**")
                        .uri("lb://post-service"))
                .route("search-service-docs", r -> r.path("/v1/search/v3/api-docs/**")
                        .uri("lb://search-service"))
                .route("member-service-docs", r -> r.path("/v1/members/v3/api-docs/**")
                        .uri("lb://member-service"))
                .route("user-activity-service-docs", r -> r.path("/v1/user-activity/v3/api-docs/**")
                        .uri("lb://user-activity-service"))
                .build();
    }
}
