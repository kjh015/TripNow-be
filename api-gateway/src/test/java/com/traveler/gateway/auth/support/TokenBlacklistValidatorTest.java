package com.traveler.gateway.auth.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.traveler.gateway.exception.ApiGatewayException;
import com.traveler.gateway.exception.ApiGatewayNoStackException;
import com.traveler.gateway.exception.code.ApiGatewayErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import reactor.core.publisher.Mono;

class TokenBlacklistValidatorTest {

    @SuppressWarnings("unchecked")
    private final ReactiveRedisTemplate<String, String> redisTemplate = mock(ReactiveRedisTemplate.class);

    private final TokenBlacklistValidator validator = new TokenBlacklistValidator(redisTemplate);

    @Test
    @DisplayName("블랙리스트에 없으면 통과한다")
    void passesWhenNotBlacklisted() {
        given(redisTemplate.hasKey(anyString())).willReturn(Mono.just(false));

        assertThatCode(() -> validator.checkBlacklist("token").block()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("블랙리스트에 있으면 BLACKLISTED_TOKEN")
    void rejectsBlacklisted() {
        given(redisTemplate.hasKey(anyString())).willReturn(Mono.just(true));

        assertThatThrownBy(() -> validator.checkBlacklist("token").block())
                .isInstanceOfSatisfying(ApiGatewayNoStackException.class, e -> assertThat(e.getCode())
                        .isEqualTo(ApiGatewayErrorCode.BLACKLISTED_TOKEN));
    }

    @Test
    @DisplayName("Redis 연결 실패 시 통과시키지 않고 AUTH_STORE_UNAVAILABLE(503)")
    void failsClosedOnConnectionFailure() {
        given(redisTemplate.hasKey(anyString()))
                .willReturn(Mono.error(new RedisConnectionFailureException("connection refused")));

        assertAuthStoreUnavailable();
    }

    @Test
    @DisplayName("Redis 명령 타임아웃 시 통과시키지 않고 AUTH_STORE_UNAVAILABLE(503)")
    void failsClosedOnTimeout() {
        given(redisTemplate.hasKey(anyString())).willReturn(Mono.error(new QueryTimeoutException("timeout")));

        assertAuthStoreUnavailable();
    }

    private void assertAuthStoreUnavailable() {
        assertThatThrownBy(() -> validator.checkBlacklist("token").block())
                .isInstanceOfSatisfying(ApiGatewayException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ApiGatewayErrorCode.AUTH_STORE_UNAVAILABLE);
                    assertThat(e.getCode().getStatus()).isEqualTo(503);
                });
    }
}
