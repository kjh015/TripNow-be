package com.traveler.gateway.config;

import com.traveler.common.core.auth.UserContext;
import com.traveler.gateway.support.ClientIpResolver;
import com.traveler.gateway.support.auth.AuthContextManager;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

/**
 * RateLimitFilter(RequestRateLimiter + RedisRateLimiter)가 사용하는 제한 키.
 * Redis 버킷 키에 라우트 ID가 포함되므로 같은 IP라도 라우트별로 한도를 따로 적용한다.
 * 한도는 application.yml의 app.rate-limit.policies에서 설정한다(RateLimitProperties).
 * app.rate-limit.enabled가 false이면 빈 키를 반환한다.
 * 빈 키의 처리 방식은 deny-empty-key 설정에 따라 달라진다.
 * local 프로필에서는 Redis를 호출하지 않고 요청을 통과시키도록 false로 설정하며,
 * 그 외 환경에서는 기본값인 true를 적용해 빈 키를 가진 요청을 403으로 차단한다.
 */
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfig {

    private static final String IP_PREFIX = "ip:";
    private static final String USER_PREFIX = "user:";

    private final boolean enabled;

    public RateLimitConfig(RateLimitProperties properties) {
        this.enabled = properties.enabled();
    }

    /**
     * 클라이언트 IP. ALB가 붙인 값(X-Forwarded-For의 마지막 값)을 쓰므로 앞쪽 값을 위조해도 우회되지 않는다.
     * RequestRateLimiter의 기본 키이기도 하다(KeyResolver 빈이 2개라 @Primary가 필요).
     */
    @Bean
    @Primary
    public KeyResolver ipKeyResolver() {
        if (!enabled) {
            return exchange -> Mono.empty();
        }
        return exchange -> Mono.just(IP_PREFIX + ClientIpResolver.resolve(exchange.getRequest()));
    }

    /** 인증된 요청은 userId, 아니면 IP. 라우트 filters에서 JwtAuthenticationFilter 뒤에 둬야 userId를 읽는다. */
    @Bean
    public KeyResolver userOrIpKeyResolver(AuthContextManager authContextManager) {
        if (!enabled) {
            return exchange -> Mono.empty();
        }
        return exchange -> Mono.just(authContextManager
                .getAuthenticatedUser(exchange)
                .map(UserContext::id)
                .map(userId -> USER_PREFIX + userId)
                .orElseGet(() -> IP_PREFIX + ClientIpResolver.resolve(exchange.getRequest())));
    }
}
