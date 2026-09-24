package com.traveler.gateway.auth.filter;

import com.traveler.common.core.auth.UserContext;
import com.traveler.common.core.code.ErrorCode;
import com.traveler.gateway.auth.support.AuthContextManager;
import com.traveler.gateway.exception.ApiGatewayNoStackException;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class RoleAuthorizationFilter extends AbstractGatewayFilterFactory<RoleAuthorizationFilter.Config> {
    private final AuthContextManager authContextManager;

    public RoleAuthorizationFilter(AuthContextManager authContextManager) {
        super(Config.class);
        this.authContextManager = authContextManager;
    }

    @Data
    public static class Config {
        private String requiredRole; // 요구되는 권한 (예: ROLE_ADMIN)
    }

    @Override
    public GatewayFilter apply(Config config) {
        String requiredRole = config.getRequiredRole();
        if (requiredRole == null || requiredRole.isBlank()) {
            return misconfiguredFilter();
        }
        return (exchange, chain) -> authContextManager
                .getAuthenticatedUser(exchange)
                .map(authUser -> authorize(exchange, chain, authUser, requiredRole))
                .orElseGet(() -> denyMissingContext(exchange));
    }

    /** 라우트 설정에 requiredRole이 누락된 경우, 해당 라우트의 모든 요청을 실패시킨다. */
    private GatewayFilter misconfiguredFilter() {
        log.error("[Auth] requiredRole is missing in RoleAuthorizationFilter config");
        return (exchange, chain) -> Mono.error(new ApiGatewayNoStackException(ErrorCode.INTERNAL_SERVER_ERROR));
    }

    /** 요구 권한 보유 여부를 확인해 필터 체인을 이어가거나 차단한다. */
    private Mono<Void> authorize(
            ServerWebExchange exchange, GatewayFilterChain chain, UserContext authUser, String requiredRole) {
        if (!authUser.roles().contains(requiredRole)) {
            log.warn("[Auth] Access Denied. Required: {}, Actual: {}", requiredRole, authUser.roles());
            return Mono.error(new ApiGatewayNoStackException(ErrorCode.FORBIDDEN));
        }
        return chain.filter(exchange);
    }

    /** 인증 필터를 거치지 않아 인증 컨텍스트 자체가 없는 경우 */
    private Mono<Void> denyMissingContext(ServerWebExchange exchange) {
        log.error(
                "[Auth] AuthenticatedUser context missing for path: {}",
                exchange.getRequest().getPath());
        return Mono.error(new ApiGatewayNoStackException(ErrorCode.UNAUTHORIZED));
    }
}
