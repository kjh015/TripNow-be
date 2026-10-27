package com.traveler.gateway.filter.ratelimit;

import com.traveler.gateway.config.RateLimitProperties;
import com.traveler.gateway.config.RateLimitProperties.Policy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Data;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RequestRateLimiterGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * 라우트에 "- RateLimitFilter=<정책 이름>"로 Rate Limiting을 적용한다.
 * app.rate-limit.policies에 정의된 기준과 한도로 Spring Cloud Gateway의 RequestRateLimiter를 구성한다.
 * Redis 토큰 버킷, 429 응답, X-RateLimit-* 헤더, 빈 키 처리(deny-empty-key)는 RequestRateLimiter의 동작을 따른다.
 */
@Component
public class RateLimitFilter extends AbstractGatewayFilterFactory<RateLimitFilter.Config> {

    private final RequestRateLimiterGatewayFilterFactory requestRateLimiter;
    private final RateLimitProperties properties;
    private final KeyResolver ipKeyResolver;
    private final KeyResolver userOrIpKeyResolver;
    private final ApplicationContext applicationContext;

    /** 정책별 RedisRateLimiter. 라우트를 다시 만들어도(라우트 갱신) 같은 인스턴스를 쓴다 */
    private final Map<String, RedisRateLimiter> rateLimiters = new ConcurrentHashMap<>();

    public RateLimitFilter(
            RequestRateLimiterGatewayFilterFactory requestRateLimiter,
            RateLimitProperties properties,
            @Qualifier("ipKeyResolver") KeyResolver ipKeyResolver,
            @Qualifier("userOrIpKeyResolver") KeyResolver userOrIpKeyResolver,
            ApplicationContext applicationContext) {
        super(Config.class);
        this.requestRateLimiter = requestRateLimiter;
        this.properties = properties;
        this.ipKeyResolver = ipKeyResolver;
        this.userOrIpKeyResolver = userOrIpKeyResolver;
        this.applicationContext = applicationContext;
    }

    @Data
    public static class Config {
        /** app.rate-limit.policies의 키 */
        private String policy;
    }

    /** "RateLimitFilter=login"처럼 이름 없이 값만 적으면 policy로 받는다 */
    @Override
    public List<String> shortcutFieldOrder() {
        return List.of("policy");
    }

    @Override
    public GatewayFilter apply(Config config) {
        Policy policy = properties.policies().get(config.getPolicy());
        if (policy == null) {
            throw new IllegalArgumentException("Rate limit 정책이 없다: " + config.getPolicy()
                    + " (app.rate-limit.policies: " + properties.policies().keySet() + ")");
        }
        return requestRateLimiter.apply(new RequestRateLimiterGatewayFilterFactory.Config()
                .setRateLimiter(rateLimiterOf(config.getPolicy(), policy))
                .setKeyResolver(keyResolverOf(policy)));
    }

    private RedisRateLimiter rateLimiterOf(String name, Policy policy) {
        return rateLimiters.computeIfAbsent(name, ignored -> {
            RedisRateLimiter limiter =
                    new RedisRateLimiter(policy.replenishRate(), policy.burstCapacity(), policy.requestedTokens());
            // 빈이 아니라서 Redis 연결과 토큰 버킷 스크립트를 컨텍스트에서 직접 받게 한다
            limiter.setApplicationContext(applicationContext);
            return limiter;
        });
    }

    private KeyResolver keyResolverOf(Policy policy) {
        return switch (policy.key()) {
            case IP -> ipKeyResolver;
            case USER -> userOrIpKeyResolver;
        };
    }
}
