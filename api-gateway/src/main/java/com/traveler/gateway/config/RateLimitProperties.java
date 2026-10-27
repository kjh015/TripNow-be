package com.traveler.gateway.config;

import java.util.Map;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 게이트웨이 Rate Limiting 설정 (app.rate-limit). 라우트는 RateLimitFilter=<정책 이름>으로 정책을 고른다.
 *
 * @param enabled false면 제한하지 않는다 (local 프로필)
 * @param policies 정책 이름 → 제한 기준과 토큰 버킷 한도
 */
@ConfigurationProperties("app.rate-limit")
public record RateLimitProperties(boolean enabled, Map<String, Policy> policies) {

    public RateLimitProperties {
        policies = policies == null ? Map.of() : Map.copyOf(policies);
    }

    /**
     * @param key 횟수를 세는 기준
     * @param replenishRate 초당 채워지는 토큰 수
     * @param burstCapacity 버킷 크기
     * @param requestedTokens 요청 1건이 쓰는 토큰 수
     */
    public record Policy(KeyType key, int replenishRate, int burstCapacity, int requestedTokens) {

        /** 잘못된 값이면 바인딩에서 실패시켜 게이트웨이가 기동하지 않게 한다 (필터 생성 때 실패하면 그 라우트만 빠진 채 기동된다) */
        public Policy {
            Objects.requireNonNull(key, "app.rate-limit.policies.*.key는 필수다");
            if (replenishRate < 1 || burstCapacity < 1 || requestedTokens < 1) {
                throw new IllegalArgumentException("replenish-rate, burst-capacity, requested-tokens는 1 이상이어야 한다");
            }
            // 버킷의 토큰은 burst-capacity를 넘지 않으므로 요청 1건이 쓰는 토큰이 더 크면 모든 요청이 거절된다
            if (requestedTokens > burstCapacity) {
                throw new IllegalArgumentException("requested-tokens는 burst-capacity를 넘을 수 없다");
            }
        }
    }

    public enum KeyType {
        /** 클라이언트 IP (ipKeyResolver) */
        IP,
        /** 인증된 사용자, 없으면 IP (userOrIpKeyResolver) */
        USER
    }
}
