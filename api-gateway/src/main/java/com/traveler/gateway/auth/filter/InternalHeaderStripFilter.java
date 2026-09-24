package com.traveler.gateway.auth.filter;

import com.traveler.common.core.auth.AuthConstants;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 클라이언트가 보낸 내부 보안 헤더를 모든 라우트에서 제거한다.
 * 인증 필터가 없는 공개 라우트에서도 위조 헤더가 다운스트림으로 전달되지 않게 한다.
 */
@Component
public class InternalHeaderStripFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerWebExchange stripped = exchange.mutate()
                .request(r -> r.headers(headers -> {
                    headers.remove(AuthConstants.X_USER_ID);
                    headers.remove(AuthConstants.X_USER_ROLES);
                    headers.remove(AuthConstants.X_ACCESS_TOKEN);
                }))
                .build();
        return chain.filter(stripped);
    }

    @Override
    public int getOrder() {
        // 라우트 필터(인증 필터의 헤더 주입)보다 먼저 실행한다
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
