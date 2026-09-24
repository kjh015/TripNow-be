package com.traveler.gateway.auth.filter;

import com.traveler.common.core.auth.AuthConstants;
import com.traveler.common.core.auth.UserContext;
import com.traveler.gateway.auth.support.AuthContextManager;
import com.traveler.gateway.auth.support.JwtTokenProvider;
import com.traveler.gateway.auth.support.TokenBlacklistValidator;
import com.traveler.gateway.exception.ApiGatewayNoStackException;
import com.traveler.gateway.exception.code.ApiGatewayErrorCode;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class JwtAuthenticationFilter extends AbstractGatewayFilterFactory<JwtAuthenticationFilter.Config> {

    private final JwtTokenProvider jwtTokenProvider;
    private final AuthContextManager authContextManager;
    private final TokenBlacklistValidator tokenBlacklistValidator;

    public JwtAuthenticationFilter(
            JwtTokenProvider jwtTokenProvider,
            AuthContextManager authContextManager,
            TokenBlacklistValidator tokenBlacklistValidator) {
        super(Config.class);
        this.jwtTokenProvider = jwtTokenProvider;
        this.authContextManager = authContextManager;
        this.tokenBlacklistValidator = tokenBlacklistValidator;
    }

    @Data
    public static class Config {}

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> extractToken(exchange.getRequest())
                .flatMap(this::validateNotBlacklisted)
                .flatMap(this::toUserContext)
                .flatMap(authUser -> authenticate(exchange, chain, authUser));
    }

    /** 요청 헤더에서 토큰을 꺼낸다. 없으면 인증 실패로 처리한다. */
    private Mono<String> extractToken(ServerHttpRequest request) {
        return Mono.justOrEmpty(resolveToken(request))
                .switchIfEmpty(Mono.error(new ApiGatewayNoStackException(ApiGatewayErrorCode.JWT_NOT_FOUND)));
    }

    private String resolveToken(ServerHttpRequest request) {
        String bearerToken = request.getHeaders().getFirst(AuthConstants.AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(AuthConstants.BEARER_PREFIX)) {
            return bearerToken.substring(AuthConstants.BEARER_PREFIX.length());
        }
        return null;
    }

    /** 블랙리스트(로그아웃 처리된) 토큰인지 확인한다. */
    private Mono<String> validateNotBlacklisted(String token) {
        return tokenBlacklistValidator.checkBlacklist(token).then(Mono.just(token));
    }

    /** 검증된 토큰의 클레임으로 인증 사용자 정보를 조립한다. 액세스 토큰만 허용한다. */
    private Mono<UserContext> toUserContext(String token) {
        return jwtTokenProvider.validateToken(token).map(claims -> {
            if (!AuthConstants.TOKEN_TYPE_ACCESS.equals(jwtTokenProvider.getTokenType(claims))) {
                throw new ApiGatewayNoStackException(ApiGatewayErrorCode.INVALID_TOKEN_TYPE);
            }
            return UserContext.of(jwtTokenProvider.getUserId(claims), jwtTokenProvider.getRoles(claims), token);
        });
    }

    /** 인증 컨텍스트를 저장하고 헤더가 주입된 exchange로 필터 체인을 이어간다. */
    private Mono<Void> authenticate(ServerWebExchange exchange, GatewayFilterChain chain, UserContext authUser) {
        authContextManager.storeAuthenticatedUser(exchange, authUser);
        return chain.filter(authContextManager.prepareAuthorizedExchange(exchange, authUser));
    }
}
