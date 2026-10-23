package com.traveler.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.traveler.gateway.filter.auth.InternalHeaderStripFilter;
import com.traveler.gateway.filter.logging.AccessLogFilter;
import com.traveler.gateway.filter.tracing.TraceIdResponseHeaderFilter;
import com.traveler.gateway.support.auth.AuthContextManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GatewayFilterOrderTest {

    @Test
    @DisplayName("위조 헤더 제거 → 접근 로그 → X-Trace-Id 순서로 실행되고, 모두 라우트 필터(1부터)보다 먼저다")
    void globalFiltersRunInDeclaredOrder() {
        int strip = new InternalHeaderStripFilter().getOrder();
        int accessLog = new AccessLogFilter(new AuthContextManager()).getOrder();
        int traceId = new TraceIdResponseHeaderFilter().getOrder();

        assertThat(strip).isEqualTo(GatewayFilterOrder.INTERNAL_HEADER_STRIP);
        assertThat(accessLog).isEqualTo(GatewayFilterOrder.ACCESS_LOG);
        assertThat(traceId).isEqualTo(GatewayFilterOrder.TRACE_ID_RESPONSE_HEADER);
        assertThat(strip).isLessThan(accessLog);
        assertThat(accessLog).isLessThan(traceId);
        assertThat(traceId).isLessThan(1);
    }
}
