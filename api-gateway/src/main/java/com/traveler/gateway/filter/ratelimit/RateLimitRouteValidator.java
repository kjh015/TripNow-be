package com.traveler.gateway.filter.ratelimit;

import com.traveler.gateway.config.RateLimitProperties;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.support.NameUtils;
import org.springframework.stereotype.Component;

/**
 * 라우트의 RateLimitFilter가 쓰는 정책 이름이 app.rate-limit.policies에 있는지 기동할 때 확인한다.
 * 필터 생성 때 실패하면 그 라우트만 빠진 채 기동되어, 쓰기 요청이 아래 라우트로 내려가 제한 없이 통과한다.
 */
@Component
@RequiredArgsConstructor
public class RateLimitRouteValidator implements InitializingBean {

    private static final String FILTER_NAME = "RateLimitFilter";
    /** RateLimitFilter.Config의 필드 이름 */
    private static final String POLICY_ARG = "policy";

    private final GatewayProperties gatewayProperties;
    private final RateLimitProperties rateLimitProperties;

    @Override
    public void afterPropertiesSet() {
        List<String> unknown = gatewayProperties.getRoutes().stream()
                .flatMap(route -> route.getFilters().stream()
                        .filter(filter -> FILTER_NAME.equals(filter.getName()))
                        .filter(filter -> !hasKnownPolicy(filter))
                        .map(filter -> route.getId() + "=" + filter.getArgs()))
                .toList();
        if (!unknown.isEmpty()) {
            throw new IllegalStateException("Rate limit 정책을 잘못 쓴 라우트가 있다: " + unknown + " (app.rate-limit.policies: "
                    + rateLimitProperties.policies().keySet() + ")");
        }
    }

    /**
     * 필터가 정책을 읽을 수 있는 인자인지 본다. "RateLimitFilter=write"는 값이 자동 생성 키(_genkey_0)로,
     * name/args 형식은 policy 키로 들어온다. 인자가 없거나 키가 다르면(policy 오타 등) 필터의 policy가 null이 된다.
     */
    private boolean hasKnownPolicy(FilterDefinition filter) {
        Map<String, String> args = filter.getArgs();
        if (args.size() != 1) {
            return false;
        }
        String policy = args.containsKey(POLICY_ARG) ? args.get(POLICY_ARG) : args.get(NameUtils.generateName(0));
        return policy != null && rateLimitProperties.policies().containsKey(policy);
    }
}
