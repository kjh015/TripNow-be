package com.traveler.gateway.auth.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.traveler.common.core.auth.AuthConstants;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

class InternalHeaderStripFilterTest {

    private final InternalHeaderStripFilter filter = new InternalHeaderStripFilter();

    @Test
    @DisplayName("공개 라우트 요청의 위조 내부 헤더는 다운스트림으로 전달되지 않는다")
    void stripsInternalHeaders() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/search/posts")
                .header(AuthConstants.X_USER_ID, "1")
                .header(AuthConstants.X_USER_ROLES, "ROLE_ADMIN")
                .header(AuthConstants.X_ACCESS_TOKEN, "forged")
                .header("X-Trace-Id", "abc"));
        AtomicReference<HttpHeaders> downstream = new AtomicReference<>();

        filter.filter(exchange, ex -> {
                    downstream.set(ex.getRequest().getHeaders());
                    return Mono.empty();
                })
                .block();

        assertThat(downstream.get())
                .doesNotContainKeys(AuthConstants.X_USER_ID, AuthConstants.X_USER_ROLES, AuthConstants.X_ACCESS_TOKEN)
                .containsEntry("X-Trace-Id", List.of("abc"));
    }

    @Test
    @DisplayName("라우트 필터보다 먼저 실행된다")
    void runsBeforeRouteFilters() {
        assertThat(filter.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    }
}
