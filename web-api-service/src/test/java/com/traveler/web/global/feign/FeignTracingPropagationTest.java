package com.traveler.web.global.feign;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.traveler.web.domain.member.adapter.MemberClientAdapter;
import com.traveler.web.domain.member.cache.MemberNicknameCache;
import com.traveler.web.domain.member.client.MemberClient;
import com.traveler.web.global.resilience.DownstreamCallExecutor;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.observation.ObservationAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.tracing.BraveAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.tracing.MicrometerTracingAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * feign-micrometer가 Feign 호출에 observation을 걸어, 요청 처리 중인 trace를 traceparent 헤더로 하위 서비스에 넘기는지 확인한다.
 * 샘플링은 logging.yml과 같은 0이다. 하위 서비스는 이 헤더로 같은 traceId를 이어 받는다.
 */
// @SpringBootTest는 기본으로 tracing을 끈다(전파 헤더가 비는 Propagator). 추적 동작을 보려면 다시 켠다
@AutoConfigureObservability(metrics = false)
@SpringBootTest(
        classes = FeignTracingPropagationTest.FeignTracingTestConfig.class,
        properties = "management.tracing.sampling.probability=0.0")
class FeignTracingPropagationTest {

    private static final WireMockServer WIRE_MOCK = new WireMockServer(options().dynamicPort());

    static {
        WIRE_MOCK.start();
    }

    @Configuration
    @ImportAutoConfiguration({
        JacksonAutoConfiguration.class,
        HttpMessageConvertersAutoConfiguration.class,
        ObservationAutoConfiguration.class,
        MicrometerTracingAutoConfiguration.class,
        BraveAutoConfiguration.class,
        FeignAutoConfiguration.class,
        CircuitBreakerAutoConfiguration.class
    })
    @EnableFeignClients(clients = MemberClient.class)
    @Import({MemberClientAdapter.class, DownstreamCallExecutor.class, MemberNicknameCache.class})
    static class FeignTracingTestConfig {}

    @DynamicPropertySource
    static void feignProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.openfeign.client.config.MemberClient.url", WIRE_MOCK::baseUrl);
    }

    @Autowired
    private MemberClientAdapter memberClientAdapter;

    @Autowired
    private ObservationRegistry observationRegistry;

    @Autowired
    private Tracer tracer;

    @AfterAll
    static void stopWireMock() {
        WIRE_MOCK.stop();
    }

    @Test
    @DisplayName("요청 처리 중 Feign 호출에는 같은 traceId의 traceparent 헤더가 실린다")
    void propagatesTraceparentToDownstream() {
        WIRE_MOCK.stubFor(
                get(urlPathEqualTo("/v1/members/1"))
                        .willReturn(
                                okJson(
                                        "{\"success\":true,\"code\":\"COMMON200\",\"message\":\"OK\",\"result\":{\"memberId\":1,\"nickname\":\"여행자\"}}")));
        AtomicReference<String> traceId = new AtomicReference<>();

        // 서블릿 요청 observation(ServerHttpObservationFilter)이 열려 있는 상황
        Observation.createNotStarted("http.server.requests", observationRegistry)
                .observe(() -> {
                    traceId.set(tracer.currentSpan().context().traceId());
                    memberClientAdapter.getMemberNickname(1L);
                });

        assertThat(traceId.get()).isNotBlank();
        WIRE_MOCK.verify(getRequestedFor(urlPathEqualTo("/v1/members/1"))
                .withHeader("traceparent", matching("00-" + traceId.get() + "-[0-9a-f]{16}-0[01]")));
    }
}
