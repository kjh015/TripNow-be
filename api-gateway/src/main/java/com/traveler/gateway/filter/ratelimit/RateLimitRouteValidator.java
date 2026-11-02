package com.traveler.gateway.filter.ratelimit;

import com.traveler.gateway.config.RateLimitProperties;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.stereotype.Component;

/**
 * 라우트의 RateLimitFilter가 쓰는 정책 이름이 app.rate-limit.policies에 있는지 기동할 때 확인한다.
 * 필터 생성 때 실패하면 그 라우트만 빠진 채 기동되어, 쓰기 요청이 아래 라우트로 내려가 제한 없이 통과한다.
 */
@Component
@RequiredArgsConstructor
public class RateLimitRouteValidator implements InitializingBean {

    private static final String FILTER_NAME = "RateLimitFilter";

    private final GatewayProperties gatewayProperties;
    private final RateLimitProperties rateLimitProperties;

    @Override
    public void afterPropertiesSet() {
        List<String> unknown = gatewayProperties.getRoutes().stream()
                .flatMap(route -> route.getFilters().stream()
                        .filter(filter -> FILTER_NAME.equals(filter.getName()))
                        .filter(filter -> !hasKnownPolicy(filter))
                        .map(filter -> route.getId() + "=" + filter.getArgs().values()))
                .toList();
        if (!unknown.isEmpty()) {
            throw new IllegalStateException("없는 Rate limit 정책을 쓰는 라우트가 있다: " + unknown + " (app.rate-limit.policies: "
                    + rateLimitProperties.policies().keySet() + ")");
        }
    }

    // 값 없이 "- RateLimitFilter"만 쓰면 args가 비어 containsAll이 참이 되므로 빈 값도 실패로 본다
    private boolean hasKnownPolicy(FilterDefinition filter) {
        return !filter.getArgs().isEmpty()
                && rateLimitProperties
                        .policies()
                        .keySet()
                        .containsAll(filter.getArgs().values());
    }
}
