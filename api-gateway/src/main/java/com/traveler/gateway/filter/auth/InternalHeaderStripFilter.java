package com.traveler.gateway.filter.auth;

import com.traveler.common.core.auth.AuthConstants;
import com.traveler.gateway.filter.GatewayFilterOrder;
import com.traveler.gateway.support.ClientIpResolver;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 클라이언트가 보낸 내부 보안 헤더를 모든 라우트에서 제거한다.
 * 인증 필터가 없는 공개 라우트에서도 위조 헤더가 다운스트림으로 전달되지 않게 한다.
 */
@Slf4j
@Component
public class InternalHeaderStripFilter implements GlobalFilter, Ordered {

    private static final List<String> INTERNAL_HEADERS =
            List.of(AuthConstants.X_USER_ID, AuthConstants.X_USER_ROLES, AuthConstants.X_ACCESS_TOKEN);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        warnIfForged(exchange);
        ServerWebExchange stripped = exchange.mutate()
                .request(r -> r.headers(headers -> INTERNAL_HEADERS.forEach(headers::remove)))
                .build();
        return chain.filter(stripped);
    }

    /** 클라이언트가 내부 헤더를 직접 보냈다면 위조 시도로 보고 헤더 이름만 남긴다(값은 남기지 않는다). */
    private void warnIfForged(ServerWebExchange exchange) {
        HttpHeaders headers = exchange.getRequest().getHeaders();
        List<String> forged =
                INTERNAL_HEADERS.stream().filter(headers::containsKey).toList();
        if (!forged.isEmpty()) {
            log.warn(
                    "[Auth] Forged internal header stripped. headers={} path={} clientIp={}",
                    forged,
                    exchange.getRequest().getPath().value(),
                    ClientIpResolver.resolve(exchange.getRequest()));
        }
    }

    @Override
    public int getOrder() {
        return GatewayFilterOrder.INTERNAL_HEADER_STRIP;
    }
}
