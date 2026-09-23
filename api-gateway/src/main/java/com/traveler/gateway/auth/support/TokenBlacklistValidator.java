package com.traveler.gateway.auth.support;

import com.traveler.common.core.auth.AuthConstants;
import com.traveler.gateway.exception.ApiGatewayException;
import com.traveler.gateway.exception.ApiGatewayNoStackException;
import com.traveler.gateway.exception.code.ApiGatewayErrorCode;
import com.traveler.gateway.util.TokenHashUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class TokenBlacklistValidator {
    private final ReactiveRedisTemplate<String, String> redisTemplate;

    public Mono<Void> checkBlacklist(String token) {
        String hashedToken = TokenHashUtil.hash(token);
        String redisKey = AuthConstants.REDIS_BLACKLIST_PREFIX + hashedToken;
        return redisTemplate
                .hasKey(redisKey)
                // fail-closed: 조회 실패 시 로그아웃된 토큰이 통과하지 않도록 503으로 차단한다
                .onErrorMap(e -> new ApiGatewayException(ApiGatewayErrorCode.AUTH_STORE_UNAVAILABLE, e))
                .flatMap(isBlacklisted -> Boolean.TRUE.equals(isBlacklisted)
                        ? Mono.error(new ApiGatewayNoStackException(ApiGatewayErrorCode.BLACKLISTED_TOKEN))
                        : Mono.empty());
    }
}
