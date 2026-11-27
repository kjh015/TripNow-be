package com.traveler.web.global.resilience;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import com.traveler.common.api.auth.context.UserContextHolder;
import com.traveler.common.core.auth.AuthConstants;
import com.traveler.common.core.auth.UserContext;
import com.traveler.common.core.response.ApiResponse;
import com.traveler.web.domain.member.adapter.MemberClientAdapter;
import com.traveler.web.domain.member.cache.MemberNicknameCache;
import com.traveler.web.domain.member.client.MemberClient;
import com.traveler.web.domain.post.client.PostClient;
import com.traveler.web.global.exception.WebApiServiceException;
import com.traveler.web.global.feign.config.FeignClientConfig;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import io.github.resilience4j.springboot3.timelimiter.autoconfigure.TimeLimiterAutoConfiguration;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JAutoConfiguration;
import org.springframework.cloud.client.circuitbreaker.NoFallbackAvailableException;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 실제 Feign 스택, Spring Cloud CircuitBreaker의 OpenFeign 연동, yml의 서킷 설정(application.yml)을 함께 띄워 확인한다.
 * <ul>
 *   <li>모든 Feign 호출이 하위 서비스 이름의 서킷을 거치고, 서킷 설정은 yml instances의 것이 쓰인다</li>
 *   <li>서킷을 거친 호출에도 인증 헤더가 붙는다(요청 스레드에서 실행되므로 ThreadLocal 인증 정보가 그대로 쓰인다)</li>
 *   <li>5xx·타임아웃·연결 실패가 이어지면 서킷이 열리고, 이후에는 하위 서비스로 요청이 나가지 않는다</li>
 * </ul>
 * 서킷 상태를 바꾸는 테스트는 시간을 기다리지 않도록 transitionTo*State()로 상태를 옮긴다.
 */
@SpringBootTest(classes = DownstreamCircuitBreakerWireMockTest.FeignTestConfig.class)
class DownstreamCircuitBreakerWireMockTest {

    private static final WireMockServer WIRE_MOCK = new WireMockServer(options().dynamicPort());

    private static final String UNKNOWN_SERVICE = "unknown-service";

    // yml 최소 호출 수. 이만큼 실패하면 실패율 100%로 열린다
    private static final int MINIMUM_CALLS = 10;

    static {
        WIRE_MOCK.start();
    }

    /** yml instances에 없는 이름의 Feign 클라이언트. 첫 호출에서 resolver가 막는지 본다 */
    @FeignClient(name = UNKNOWN_SERVICE, contextId = "UnknownServiceClient", configuration = FeignClientConfig.class)
    interface UnknownServiceClient {
        @GetMapping("/v1/unknown")
        ApiResponse<String> call();
    }

    @Configuration
    @ImportAutoConfiguration({
        JacksonAutoConfiguration.class,
        HttpMessageConvertersAutoConfiguration.class,
        FeignAutoConfiguration.class,
        CircuitBreakerAutoConfiguration.class,
        TimeLimiterAutoConfiguration.class,
        Resilience4JAutoConfiguration.class
    })
    @EnableFeignClients(clients = {MemberClient.class, PostClient.class, UnknownServiceClient.class})
    @Import({
        MemberClientAdapter.class,
        DownstreamCircuitBreakerConfig.class,
        MemberNicknameCache.class,
        CircuitBreakerStateLogger.class
    })
    static class FeignTestConfig {}

    @DynamicPropertySource
    static void feignProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.openfeign.client.config.MemberClient.url", WIRE_MOCK::baseUrl);
        registry.add("spring.cloud.openfeign.client.config.PostClient.url", WIRE_MOCK::baseUrl);
        registry.add("spring.cloud.openfeign.client.config.UnknownServiceClient.url", WIRE_MOCK::baseUrl);
        registry.add("spring.cloud.openfeign.client.config.default.connect-timeout", () -> 2000);
        registry.add("spring.cloud.openfeign.client.config.default.read-timeout", () -> 2000);
    }

    @Autowired
    private MemberClientAdapter memberClientAdapter;

    @Autowired
    private PostClient postClient;

    @Autowired
    private UnknownServiceClient unknownServiceClient;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private MemberNicknameCache memberNicknameCache;

    @Autowired
    private ApplicationContext applicationContext;

    @BeforeEach
    void setUp() {
        WIRE_MOCK.resetAll();
        circuitBreakerRegistry.circuitBreaker(DownstreamServiceNames.MEMBER).reset();
        circuitBreakerRegistry.circuitBreaker(DownstreamServiceNames.POST).reset();
        // 컨텍스트를 테스트끼리 공유하므로 앞 테스트의 캐시가 다음 테스트의 호출을 가리지 않게 지운다
        List.of(1L, 2L, 3L).forEach(memberNicknameCache::evict);
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
    @DisplayName("Feign 연동이 켜져 서킷 Targeter가 쓰인다")
    void usesCircuitBreakerTargeter() {
        // FeignAutoConfiguration과 SCCB 자동 설정 사이에 순서 지정이 없어, 어긋나면 서킷 없이 기본 Targeter로 조용히 돈다
        assertThat(applicationContext.containsBean("circuitBreakerFeignTargeter"))
                .isTrue();
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
        assertThat(member.getMinimumNumberOfCalls()).isEqualTo(MINIMUM_CALLS);
        assertThat(member.getFailureRateThreshold()).isEqualTo(50f);
        assertThat(member.getSlowCallDurationThreshold()).isEqualTo(Duration.ofSeconds(1));
        assertThat(member.getRecordExceptionPredicate()).isInstanceOf(DownstreamFailurePredicate.class);

        CircuitBreakerConfig search = circuitBreakerRegistry
                .circuitBreaker(DownstreamServiceNames.SEARCH)
                .getCircuitBreakerConfig();
        assertThat(search.getSlowCallDurationThreshold()).isEqualTo(Duration.ofMillis(1500));
    }

    @Test
    @DisplayName("Feign 호출은 yml에 만들어 둔 하위 서비스 서킷(메서드 단위가 아닌)을 거친다")
    void feignCallUsesServiceCircuit() {
        stubNickname();
        Set<String> circuitsBefore = circuitNames();

        memberClientAdapter.getMemberNickname(1L);

        CircuitBreaker member = circuitBreakerRegistry.circuitBreaker(DownstreamServiceNames.MEMBER);
        assertThat(member.getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(1);
        // yml 설정의 서킷이 그대로 쓰인다(Feign 메서드 이름의 서킷이 새로 생기지 않는다)
        assertThat(member.getCircuitBreakerConfig().getRecordExceptionPredicate())
                .isInstanceOf(DownstreamFailurePredicate.class);
        assertThat(circuitNames()).isEqualTo(circuitsBefore);
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
        assertThat(circuitBreakerRegistry
                        .circuitBreaker(DownstreamServiceNames.MEMBER)
                        .getMetrics()
                        .getNumberOfBufferedCalls())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("5xx가 이어져 서킷이 열리면 member-service를 호출하지 않고 닉네임은 기본값, 본문 조회는 서킷 열림 예외로 끝난다")
    void openCircuitStopsCallingDownstream() {
        WIRE_MOCK.stubFor(get(urlPathEqualTo("/v1/members/1"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"success\":false,\"code\":\"COMMON500\",\"message\":\"서버 오류\",\"result\":null}")));

        // yml 최소 호출 수(10번)를 채우면 실패율 100%로 열린다
        for (int i = 0; i < MINIMUM_CALLS; i++) {
            assertThat(memberClientAdapter.getMemberNickname(1L)).isEqualTo(MemberClientAdapter.DEFAULT_NICKNAME);
        }
        assertThat(memberState()).isEqualTo(CircuitBreaker.State.OPEN);
        WIRE_MOCK.resetRequests();

        assertThat(memberClientAdapter.getMemberNickname(1L)).isEqualTo(MemberClientAdapter.DEFAULT_NICKNAME);
        // Spring Cloud CircuitBreaker가 서킷 열림도 NoFallbackAvailableException으로 감싼다. 응답은 Advice가 원인으로 503을 만든다
        assertThatThrownBy(() -> memberClientAdapter.getMyProfile())
                .isInstanceOf(NoFallbackAvailableException.class)
                .cause()
                .isInstanceOf(CallNotPermittedException.class);
        assertThat(WIRE_MOCK.getAllServeEvents()).isEmpty();
    }

    @Test
    @DisplayName("타임아웃도 실패로 세어 서킷이 열린다")
    void timeoutsOpenCircuit() {
        // read-timeout(2s)보다 길게 지연시킨다
        WIRE_MOCK.stubFor(get(urlPathEqualTo("/v1/members/1"))
                .willReturn(okJson("{\"success\":true,\"code\":\"COMMON200\",\"message\":\"OK\",\"result\":null}")
                        .withFixedDelay(2500)));
        // 시간을 줄이려고 최소 호출 수 직전까지는 상태만 채운다: 실패 9번을 기록한 뒤 실제 타임아웃 1번으로 연다
        CircuitBreaker member = circuitBreakerRegistry.circuitBreaker(DownstreamServiceNames.MEMBER);
        for (int i = 0; i < MINIMUM_CALLS - 1; i++) {
            member.onError(0, TimeUnit.MILLISECONDS, new SocketTimeoutException());
        }

        assertThat(memberClientAdapter.getMemberNickname(1L)).isEqualTo(MemberClientAdapter.DEFAULT_NICKNAME);

        assertThat(member.getMetrics().getNumberOfFailedCalls()).isEqualTo(MINIMUM_CALLS);
        assertThat(member.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    @DisplayName("연결 실패도 실패로 세어 서킷이 열린다")
    void connectionFailuresOpenCircuit() {
        WIRE_MOCK.stubFor(
                get(urlPathEqualTo("/v1/members/1")).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        for (int i = 0; i < MINIMUM_CALLS; i++) {
            assertThat(memberClientAdapter.getMemberNickname(1L)).isEqualTo(MemberClientAdapter.DEFAULT_NICKNAME);
        }

        assertThat(memberState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    @DisplayName("4xx가 이어져도 서킷은 닫힌 채로 남고, 예외는 하위 코드 그대로 전파된다")
    void clientErrorsKeepCircuitClosed() {
        WIRE_MOCK.stubFor(get(urlPathEqualTo("/v1/members/1"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                                "{\"success\":false,\"code\":\"MEMBER404_1\",\"message\":\"회원 없음\",\"result\":null}")));

        for (int i = 0; i < 12; i++) {
            assertThatThrownBy(() -> memberClientAdapter.getMemberNickname(1L))
                    .isInstanceOf(NoFallbackAvailableException.class)
                    .cause()
                    .isInstanceOf(WebApiServiceException.class)
                    .extracting("code.code")
                    .isEqualTo("MEMBER404_1");
        }

        assertThat(memberState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @DisplayName("half-open에서 허용된 호출이 성공하면 서킷이 다시 닫힌다")
    void halfOpenSuccessClosesCircuit() {
        stubNickname();
        CircuitBreaker member = circuitBreakerRegistry.circuitBreaker(DownstreamServiceNames.MEMBER);
        member.transitionToOpenState();
        // wait-duration-in-open-state가 지난 상황
        member.transitionToHalfOpenState();

        // yml permitted-number-of-calls-in-half-open-state: 3. 캐시가 막지 않도록 회원을 바꿔 부른다
        for (long memberId = 1; memberId <= 3; memberId++) {
            memberClientAdapter.getMemberNickname(memberId);
        }

        assertThat(member.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @DisplayName("half-open에서 다시 실패하면 서킷이 다시 열린다")
    void halfOpenFailureReopensCircuit() {
        WIRE_MOCK.stubFor(get(urlPathEqualTo("/v1/members/1"))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"success\":false,\"code\":\"COMMON503\",\"message\":\"점검 중\",\"result\":null}")));
        CircuitBreaker member = circuitBreakerRegistry.circuitBreaker(DownstreamServiceNames.MEMBER);
        member.transitionToOpenState();
        member.transitionToHalfOpenState();

        for (int i = 0; i < 3; i++) {
            memberClientAdapter.getMemberNickname(1L);
        }

        assertThat(member.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    @DisplayName("서킷은 하위 서비스마다 따로 열린다(member가 열려도 post는 호출된다)")
    void circuitsAreIsolatedPerService() {
        circuitBreakerRegistry.circuitBreaker(DownstreamServiceNames.MEMBER).transitionToOpenState();
        WIRE_MOCK.stubFor(
                get(urlPathEqualTo("/v1/posts/images/presigned-url"))
                        .willReturn(
                                okJson(
                                        "{\"success\":true,\"code\":\"COMMON200\",\"message\":\"OK\",\"result\":{\"url\":\"https://s3/x\",\"imageKey\":\"x\"}}")));

        assertThat(postClient.getPresignedUrl("a.png", "image/png").result().imageKey())
                .isEqualTo("x");

        assertThat(circuitBreakerRegistry
                        .circuitBreaker(DownstreamServiceNames.POST)
                        .getState())
                .isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(memberState()).isEqualTo(CircuitBreaker.State.OPEN);
        WIRE_MOCK.verify(1, getRequestedFor(urlPathEqualTo("/v1/posts/images/presigned-url")));
    }

    @Test
    @DisplayName("yml에 없는 이름의 Feign 클라이언트는 첫 호출에서 예외를 던지고, 하위 서비스를 부르지 않으며 서킷을 새로 만들지 않는다")
    void unknownServiceNameThrowsWithoutCreatingCircuit() {
        WIRE_MOCK.stubFor(get(anyUrl()).willReturn(okJson("{\"success\":true,\"result\":\"ok\"}")));

        assertThatThrownBy(() -> unknownServiceClient.call())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(UNKNOWN_SERVICE);

        WIRE_MOCK.verify(0, anyRequestedFor(anyUrl()));
        assertThat(circuitBreakerRegistry.find(UNKNOWN_SERVICE)).isEmpty();
    }

    @Test
    @DisplayName("모든 @FeignClient의 name이 yml 서킷 instances에 있다")
    void everyFeignClientNameHasCircuit() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                return beanDefinition.getMetadata().isInterface();
            }
        };
        scanner.addIncludeFilter(new AnnotationTypeFilter(FeignClient.class));

        Set<String> clientNames = scanner.findCandidateComponents("com.traveler.web").stream()
                .map(BeanDefinition::getBeanClassName)
                // 이 테스트의 UnknownServiceClient는 일부러 없는 이름을 쓴다
                .filter(className -> !className.equals(UnknownServiceClient.class.getName()))
                .map(DownstreamCircuitBreakerWireMockTest::feignClientName)
                .collect(Collectors.toSet());

        assertThat(clientNames).isNotEmpty().allSatisfy(name -> assertThat(circuitBreakerRegistry.find(name))
                .as("서킷 이름: %s", name)
                .isPresent());
    }

    private static String feignClientName(String className) {
        try {
            FeignClient feignClient = Class.forName(className).getAnnotation(FeignClient.class);
            return feignClient.name().isEmpty() ? feignClient.value() : feignClient.name();
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    private void stubNickname() {
        WIRE_MOCK.stubFor(
                get(urlPathEqualTo("/v1/members/1"))
                        .willReturn(
                                okJson(
                                        "{\"success\":true,\"code\":\"COMMON200\",\"message\":\"OK\",\"result\":{\"memberId\":1,\"nickname\":\"여행자\"}}")));
        WIRE_MOCK.stubFor(
                get(urlPathEqualTo("/v1/members/2"))
                        .willReturn(
                                okJson(
                                        "{\"success\":true,\"code\":\"COMMON200\",\"message\":\"OK\",\"result\":{\"memberId\":2,\"nickname\":\"산책가\"}}")));
        WIRE_MOCK.stubFor(
                get(urlPathEqualTo("/v1/members/3"))
                        .willReturn(
                                okJson(
                                        "{\"success\":true,\"code\":\"COMMON200\",\"message\":\"OK\",\"result\":{\"memberId\":3,\"nickname\":\"등산가\"}}")));
    }

    private Set<String> circuitNames() {
        return circuitBreakerRegistry.getAllCircuitBreakers().stream()
                .map(CircuitBreaker::getName)
                .collect(Collectors.toSet());
    }

    private CircuitBreaker.State memberState() {
        return circuitBreakerRegistry
                .circuitBreaker(DownstreamServiceNames.MEMBER)
                .getState();
    }
}
