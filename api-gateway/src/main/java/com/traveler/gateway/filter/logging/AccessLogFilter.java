package com.traveler.gateway.filter.logging;

import com.traveler.common.core.auth.UserContext;
import com.traveler.gateway.filter.GatewayFilterOrder;
import com.traveler.gateway.support.ClientIpResolver;
import com.traveler.gateway.support.auth.AuthContextManager;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

/**
 * 요청 1건당 응답 완료 시점에 접근 로그 1줄을 남긴다.
 * 인증 실패처럼 에러 핸들러가 응답을 쓰는 경우도 상태 코드가 정해진 뒤(커밋 직전)에 기록한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccessLogFilter implements GlobalFilter, Ordered {

    private static final String ACTUATOR_PATH = "/actuator";

    private final AuthContextManager authContextManager;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (path.startsWith(ACTUATOR_PATH)) {
            return chain.filter(exchange);
        }

        long startNanos = System.nanoTime();
        AtomicBoolean logged = new AtomicBoolean(false);
        exchange.getResponse().beforeCommit(() -> {
            logOnce(exchange, startNanos, logged);
            return Mono.empty();
        });
        return chain.filter(exchange).doFinally(signal -> {
            // 완료·에러는 응답이 반드시 커밋되므로 beforeCommit이 기록한다.
            // 응답 없이 끝나는 취소만 여기서 기록한다.
            if (signal == SignalType.CANCEL) {
                logOnce(exchange, startNanos, logged);
            }
        });
    }

    private void logOnce(ServerWebExchange exchange, long startNanos, AtomicBoolean logged) {
        if (!logged.compareAndSet(false, true)) {
            return;
        }
        ServerHttpRequest request = exchange.getRequest();
        long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;
        HttpStatusCode status = exchange.getResponse().getStatusCode();
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        String userId = authContextManager
                .getAuthenticatedUser(exchange)
                .map(UserContext::id)
                .map(String::valueOf)
                .orElse("-");

        log.info(
                "[Access] method={} path={} status={} latencyMs={} route={} userId={} clientIp={}",
                request.getMethod(),
                request.getPath().value(),
                status != null ? status.value() : "-",
                latencyMs,
                route != null ? route.getId() : "-",
                userId,
                ClientIpResolver.resolve(request));
    }

    @Override
    public int getOrder() {
        return GatewayFilterOrder.ACCESS_LOG;
    }
}
