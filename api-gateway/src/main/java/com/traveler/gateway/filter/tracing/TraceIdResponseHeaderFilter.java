package com.traveler.gateway.filter.tracing;

import com.traveler.gateway.filter.GatewayFilterOrder;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.handler.TracingObservationHandler;
import java.util.Optional;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 응답 헤더 X-Trace-Id에 이 요청의 traceId를 넣는다. 프론트 버그 제보에 이 값을 받으면 Kibana에서 traceId 하나로
 * 게이트웨이부터 하위 서비스까지의 로그를 바로 찾을 수 있다.
 *
 * <p>traceId는 WebFlux가 요청마다 여는 서버 observation(exchange 속성)에서 읽는다. 하위 서비스 응답 헤더는 이 헤더 위에
 * 덧붙여지고, 에러 핸들러도 헤더를 지우지 않으므로 인증 실패 같은 에러 응답에도 남는다.
 */
@Component
public class TraceIdResponseHeaderFilter implements GlobalFilter, Ordered {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        currentTraceId(exchange)
                .ifPresent(traceId -> exchange.getResponse().getHeaders().set(TRACE_ID_HEADER, traceId));
        return chain.filter(exchange);
    }

    private static Optional<String> currentTraceId(ServerWebExchange exchange) {
        return ServerRequestObservationContext.findCurrent(exchange.getAttributes())
                .map(context -> context.<TracingObservationHandler.TracingContext>get(
                        TracingObservationHandler.TracingContext.class))
                .map(TracingObservationHandler.TracingContext::getSpan)
                .map(Span::context)
                .map(TraceContext::traceId);
    }

    @Override
    public int getOrder() {
        return GatewayFilterOrder.TRACE_ID_RESPONSE_HEADER;
    }
}
