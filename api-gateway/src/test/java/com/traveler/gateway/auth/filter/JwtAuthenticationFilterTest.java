package com.traveler.gateway.auth.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.traveler.common.core.auth.AuthConstants;
import com.traveler.gateway.auth.support.AuthContextManager;
import com.traveler.gateway.auth.support.JwtTokenProvider;
import com.traveler.gateway.auth.support.TokenBlacklistValidator;
import com.traveler.gateway.exception.ApiGatewayNoStackException;
import com.traveler.gateway.exception.code.ApiGatewayErrorCode;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Jwks;
import java.security.KeyPair;
import java.security.interfaces.ECPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

class JwtAuthenticationFilterTest {

    private static final KeyPair KEY_PAIR = Jwts.SIG.ES256.keyPair().build();

    private final GatewayFilter filter = createFilter();
    private final AtomicReference<HttpHeaders> downstream = new AtomicReference<>();
    private final GatewayFilterChain chain = exchange -> {
        downstream.set(exchange.getRequest().getHeaders());
        return Mono.empty();
    };

    private static GatewayFilter createFilter() {
        String publicJwk =
                Jwks.json(Jwks.builder().key((ECPublicKey) KEY_PAIR.getPublic()).build());
        TokenBlacklistValidator blacklistValidator = mock(TokenBlacklistValidator.class);
        given(blacklistValidator.checkBlacklist(anyString())).willReturn(Mono.empty());
        return new JwtAuthenticationFilter(
                        new JwtTokenProvider(publicJwk), new AuthContextManager(), blacklistValidator)
                .apply(new JwtAuthenticationFilter.Config());
    }

    @Test
    @DisplayName("액세스 토큰은 통과하고, 위조 X-User-Id 대신 토큰의 sub가 전달된다")
    void accessTokenOverridesForgedUserId() {
        String token = token(AuthConstants.TOKEN_TYPE_ACCESS, List.of("ROLE_USER"));
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/v1/posts")
                .header(AuthConstants.AUTHORIZATION_HEADER, AuthConstants.BEARER_PREFIX + token)
                .header(AuthConstants.X_USER_ID, "999"));

        filter.filter(exchange, chain).block();

        assertThat(downstream.get().get(AuthConstants.X_USER_ID)).containsExactly("42");
        assertThat(downstream.get().get(AuthConstants.X_USER_ROLES)).containsExactly("ROLE_USER");
        assertThat(downstream.get().get(AuthConstants.X_ACCESS_TOKEN)).containsExactly(token);
    }

    @Test
    @DisplayName("헤더 제거 필터와 함께 실행되면 권한이 없는 토큰에 위조 X-User-Roles가 남지 않는다")
    void forgedRolesRemovedWhenTokenHasNoRoles() {
        String token = token(AuthConstants.TOKEN_TYPE_ACCESS, List.of());
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/v1/posts")
                .header(AuthConstants.AUTHORIZATION_HEADER, AuthConstants.BEARER_PREFIX + token)
                .header(AuthConstants.X_USER_ROLES, "ROLE_ADMIN"));

        new InternalHeaderStripFilter()
                .filter(exchange, stripped -> filter.filter(stripped, chain))
                .block();

        assertThat(downstream.get().get(AuthConstants.X_USER_ID)).containsExactly("42");
        assertThat(downstream.get()).doesNotContainKey(AuthConstants.X_USER_ROLES);
    }

    @Test
    @DisplayName("리프레시 토큰으로 인증 라우트를 호출하면 INVALID_TOKEN_TYPE")
    void refreshTokenRejected() {
        String token = token(AuthConstants.TOKEN_TYPE_REFRESH, List.of("ROLE_USER"));

        assertErrorCode(bearer(token), ApiGatewayErrorCode.INVALID_TOKEN_TYPE);
    }

    @Test
    @DisplayName("type 클레임이 없는 토큰은 INVALID_TOKEN_TYPE")
    void tokenWithoutTypeRejected() {
        String token = token(null, List.of("ROLE_USER"));

        assertErrorCode(bearer(token), ApiGatewayErrorCode.INVALID_TOKEN_TYPE);
    }

    @Test
    @DisplayName("Authorization 헤더가 없으면 JWT_NOT_FOUND")
    void missingAuthorizationHeader() {
        assertErrorCode(MockServerHttpRequest.get("/api/v1/posts"), ApiGatewayErrorCode.JWT_NOT_FOUND);
    }

    private void assertErrorCode(MockServerHttpRequest.BaseBuilder<?> request, ApiGatewayErrorCode expected) {
        assertThatThrownBy(() -> filter.filter(exchange(request), chain).block())
                .isInstanceOfSatisfying(ApiGatewayNoStackException.class, e -> assertThat(e.getCode())
                        .isEqualTo(expected));
        assertThat(downstream.get()).isNull();
    }

    private static MockServerHttpRequest.BaseBuilder<?> bearer(String token) {
        return MockServerHttpRequest.get("/api/v1/posts")
                .header(AuthConstants.AUTHORIZATION_HEADER, AuthConstants.BEARER_PREFIX + token);
    }

    private static MockServerWebExchange exchange(MockServerHttpRequest.BaseBuilder<?> request) {
        return MockServerWebExchange.from(request);
    }

    private static String token(String type, List<String> roles) {
        Instant now = Instant.now();
        JwtBuilder builder = Jwts.builder()
                .subject("42")
                .claim(AuthConstants.CLAIM_ROLES, roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(600)))
                .signWith(KEY_PAIR.getPrivate(), Jwts.SIG.ES256);
        if (type != null) {
            builder.claim(AuthConstants.CLAIM_TOKEN_TYPE, type);
        }
        return builder.compact();
    }
}
