package com.traveler.gateway.filter.tracing;

import static org.assertj.core.api.Assertions.assertThat;

import brave.Tracing;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.brave.bridge.BraveCurrentTraceContext;
import io.micrometer.tracing.brave.bridge.BraveTracer;
import io.micrometer.tracing.handler.TracingObservationHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class TraceIdResponseHeaderFilterTest {

    private final Tracing tracing = Tracing.newBuilder().build();
    private final BraveTracer tracer =
            new BraveTracer(tracing.tracer(), new BraveCurrentTraceContext(tracing.currentTraceContext()));
    private final TraceIdResponseHeaderFilter filter = new TraceIdResponseHeaderFilter();

    @AfterEach
    void tearDown() {
        tracing.close();
    }

    @Test
    @DisplayName("서버 observation의 traceId를 X-Trace-Id 응답 헤더에 넣고, 라우트 필터가 에러로 끝내도 남는다")
    void setsTraceIdHeaderFromServerObservation() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/posts/3"));
        Span span = tracer.nextSpan().start();
        putServerObservation(exchange, span);

        filter.filter(exchange, ex -> {
                    // JwtAuthenticationFilter가 인증 실패로 응답을 끝낸 상황
                    ex.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                    return ex.getResponse().setComplete();
                })
                .block();

        assertThat(exchange.getResponse().getHeaders().getFirst(TraceIdResponseHeaderFilter.TRACE_ID_HEADER))
                .isEqualTo(span.context().traceId());
    }

    @Test
    @DisplayName("서버 observation이 없으면 헤더를 넣지 않고 요청을 그대로 넘긴다")
    void skipsHeaderWithoutServerObservation() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/posts/3"));

        filter.filter(exchange, ex -> ex.getResponse().setComplete()).block();

        assertThat(exchange.getResponse().getHeaders()).doesNotContainKey(TraceIdResponseHeaderFilter.TRACE_ID_HEADER);
    }

    /** WebFlux(HttpWebHandlerAdapter)가 요청마다 열어 exchange 속성에 두는 서버 observation을 흉내 낸다 */
    private static void putServerObservation(MockServerWebExchange exchange, Span span) {
        ServerRequestObservationContext observationContext = new ServerRequestObservationContext(
                exchange.getRequest(), exchange.getResponse(), exchange.getAttributes());
        TracingObservationHandler.TracingContext tracingContext = new TracingObservationHandler.TracingContext();
        tracingContext.setSpan(span);
        observationContext.put(TracingObservationHandler.TracingContext.class, tracingContext);
        exchange.getAttributes()
                .put(ServerRequestObservationContext.CURRENT_OBSERVATION_CONTEXT_ATTRIBUTE, observationContext);
    }
}
