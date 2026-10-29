package com.traveler.web.global.resilience;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.traveler.common.api.auth.context.UserContextHolder;
import com.traveler.common.core.auth.AuthConstants;
import com.traveler.common.core.auth.UserContext;
import com.traveler.web.domain.member.adapter.MemberClientAdapter;
import com.traveler.web.domain.member.cache.MemberNicknameCache;
import com.traveler.web.domain.member.client.MemberClient;
import com.traveler.web.global.exception.WebApiServiceException;
import com.traveler.web.global.exception.code.WebApiServiceErrorCode;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 실제 Feign 스택과 yml의 서킷 설정(application.yml {@code resilience4j.circuitbreaker})을 함께 띄워 확인한다.
 * <ul>
 *   <li>서킷을 거친 호출에도 인증 헤더가 붙는다(호출 스레드에서 실행되므로 ThreadLocal 인증 정보가 그대로 쓰인다)</li>
 *   <li>member-service 5xx가 이어지면 서킷이 열리고, 이후에는 member-service로 요청이 나가지 않는다</li>
 * </ul>
 */
@SpringBootTest(classes = DownstreamCircuitBreakerWireMockTest.FeignTestConfig.class)
class DownstreamCircuitBreakerWireMockTest {

    private static final WireMockServer WIRE_MOCK = new WireMockServer(options().dynamicPort());

    static {
        WIRE_MOCK.start();
    }

    @Configuration
    @ImportAutoConfiguration({
        JacksonAutoConfiguration.class,
        HttpMessageConvertersAutoConfiguration.class,
        FeignAutoConfiguration.class,
        CircuitBreakerAutoConfiguration.class
    })
    @EnableFeignClients(clients = MemberClient.class)
    @Import({
        MemberClientAdapter.class,
        DownstreamCallExecutor.class,
        MemberNicknameCache.class,
        CircuitBreakerStateLogger.class
    })
    static class FeignTestConfig {}

    @DynamicPropertySource
    static void feignProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.openfeign.client.config.MemberClient.url", WIRE_MOCK::baseUrl);
        registry.add("spring.cloud.openfeign.client.config.default.connect-timeout", () -> 2000);
        registry.add("spring.cloud.openfeign.client.config.default.read-timeout", () -> 2000);
    }

    @Autowired
    private MemberClientAdapter memberClientAdapter;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void setUp() {
        WIRE_MOCK.resetAll();
        circuitBreakerRegistry.circuitBreaker(DownstreamServiceNames.MEMBER).reset();
    }

    @AfterEach
    void clearUserContext() {
        UserContextHolder.clear();
    }

    @AfterAll
    static void stopWireMock() {
        WIRE_MOCK.stop();
    }

    @Test
    @DisplayName("yml의 서킷 설정이 하위 서비스 4개에 적용된다")
    void appliesYamlConfigPerDownstreamService() {
        // yml instances는 기동 시 레지스트리에 미리 만들어진다(그래야 상태 메트릭이 첫 호출 전부터 나온다)
        assertThat(circuitBreakerRegistry.getAllCircuitBreakers())
                .extracting(CircuitBreaker::getName)
                .contains(
                        DownstreamServiceNames.MEMBER,
                        DownstreamServiceNames.POST,
                        DownstreamServiceNames.SEARCH,
                        DownstreamServiceNames.USER_ACTIVITY);

        CircuitBreakerConfig member = circuitBreakerRegistry
                .circuitBreaker(DownstreamServiceNames.MEMBER)
                .getCircuitBreakerConfig();
        assertThat(member.getSlidingWindowSize()).isEqualTo(20);
        assertThat(member.getMinimumNumberOfCalls()).isEqualTo(10);
        assertThat(member.getFailureRateThreshold()).isEqualTo(50f);
        assertThat(member.getSlowCallDurationThreshold()).isEqualTo(Duration.ofSeconds(1));
        assertThat(member.getRecordExceptionPredicate()).isInstanceOf(DownstreamFailurePredicate.class);

        CircuitBreakerConfig search = circuitBreakerRegistry
                .circuitBreaker(DownstreamServiceNames.SEARCH)
                .getCircuitBreakerConfig();
        assertThat(search.getSlowCallDurationThreshold()).isEqualTo(Duration.ofMillis(1500));
    }

    @Test
    @DisplayName("서킷을 거친 호출에도 사용자 인증 헤더가 하위 서비스로 전달된다")
    void propagatesAuthHeadersThroughCircuitBreaker() {
        WIRE_MOCK.stubFor(
                get(urlPathEqualTo("/v1/members/me"))
                        .willReturn(
                                okJson(
                                        "{\"success\":true,\"code\":\"COMMON200\",\"message\":\"OK\",\"result\":{\"memberId\":7,\"nickname\":\"여행자\"}}")));
        UserContextHolder.setContext(UserContext.of(7L, List.of("ROLE_USER"), "access-token"));

        assertThat(memberClientAdapter.getMyNickname()).isEqualTo("여행자");

        WIRE_MOCK.verify(getRequestedFor(urlPathEqualTo("/v1/members/me"))
                .withHeader(AuthConstants.X_USER_ID, equalTo("7"))
                .withHeader(AuthConstants.X_USER_ROLES, equalTo("ROLE_USER")));
    }

    @Test
    @DisplayName("5xx가 이어져 서킷이 열리면 member-service를 호출하지 않고 닉네임은 기본값, 본문 조회는 503으로 끝난다")
    void openCircuitStopsCallingDownstream() {
        WIRE_MOCK.stubFor(get(urlPathEqualTo("/v1/members/1"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"success\":false,\"code\":\"COMMON500\",\"message\":\"서버 오류\",\"result\":null}")));

        // yml 최소 호출 수(10번)를 채우면 실패율 100%로 열린다
        for (int i = 0; i < 10; i++) {
            assertThat(memberClientAdapter.getMemberNickname(1L)).isEqualTo(MemberClientAdapter.DEFAULT_NICKNAME);
        }
        assertThat(circuitBreakerRegistry
                        .circuitBreaker(DownstreamServiceNames.MEMBER)
                        .getState())
                .isEqualTo(CircuitBreaker.State.OPEN);
        WIRE_MOCK.resetRequests();

        assertThat(memberClientAdapter.getMemberNickname(1L)).isEqualTo(MemberClientAdapter.DEFAULT_NICKNAME);
        assertThatThrownBy(() -> memberClientAdapter.getMyProfile())
                .isInstanceOf(WebApiServiceException.class)
                .extracting("code")
                .isEqualTo(WebApiServiceErrorCode.DOWNSTREAM_UNAVAILABLE);
        assertThat(WIRE_MOCK.getAllServeEvents()).isEmpty();
    }

    @Test
    @DisplayName("4xx가 이어져도 서킷은 닫힌 채로 남는다")
    void clientErrorsKeepCircuitClosed() {
        WIRE_MOCK.stubFor(get(urlPathEqualTo("/v1/members/1"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                                "{\"success\":false,\"code\":\"MEMBER404_1\",\"message\":\"회원 없음\",\"result\":null}")));

        for (int i = 0; i < 12; i++) {
            assertThatThrownBy(() -> memberClientAdapter.getMemberNickname(1L))
                    .isInstanceOf(WebApiServiceException.class);
        }

        assertThat(circuitBreakerRegistry
                        .circuitBreaker(DownstreamServiceNames.MEMBER)
                        .getState())
                .isEqualTo(CircuitBreaker.State.CLOSED);
    }
}
