package com.traveler.web.global.resilience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.traveler.web.global.exception.WebApiServiceException;
import com.traveler.web.global.exception.code.WebApiServiceErrorCode;
import com.traveler.web.global.feign.decoder.ServiceErrorCode;
import feign.Request;
import feign.RetryableException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 작은 윈도우(호출 4번, 실패율 50%)로 서킷 상태 전이와 실행기의 503·fallback 변환을 확인한다.
 * 실패 판정은 운영과 같은 {@link DownstreamFailurePredicate}를 쓴다.
 */
@ExtendWith(MockitoExtension.class)
class DownstreamCallExecutorTest {

    private static final String SERVICE = DownstreamServiceNames.MEMBER;
    private static final int WINDOW_SIZE = 4;

    @Mock
    private Supplier<String> call;

    private CircuitBreakerRegistry registry;
    private DownstreamCallExecutor executor;

    @BeforeEach
    void setUp() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(WINDOW_SIZE)
                .minimumNumberOfCalls(WINDOW_SIZE)
                .failureRateThreshold(50)
                .permittedNumberOfCallsInHalfOpenState(2)
                .recordException(new DownstreamFailurePredicate())
                .build();
        registry = CircuitBreakerRegistry.of(config);
        // 운영에서는 yml instances가 기동 시 만들어 둔다. 실행기는 없는 이름을 새로 만들지 않으므로 미리 만든다
        registry.circuitBreaker(DownstreamServiceNames.MEMBER);
        registry.circuitBreaker(DownstreamServiceNames.SEARCH);
        executor = new DownstreamCallExecutor(registry);
    }

    @Test
    @DisplayName("등록되지 않은 서킷 이름이면 하위 서비스를 부르지 않고 예외를 던지며, 서킷을 새로 만들지 않는다")
    void unknownServiceName_throwsWithoutCreatingCircuit() {
        assertThatThrownBy(() -> executor.execute("member-servce", call))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("member-servce");
        assertThatThrownBy(() -> executor.executeOrDefault("member-servce", call, "기본값"))
                .isInstanceOf(IllegalStateException.class);

        verify(call, never()).get();
        assertThat(registry.find("member-servce")).isEmpty();
    }

    @Nested
    @DisplayName("execute")
    class Execute {

        @Test
        @DisplayName("5xx가 이어지면 서킷이 열리고, 이후 호출은 하위 서비스를 부르지 않고 503으로 끝난다")
        void serverErrors_openCircuit_thenFailFastWith503() {
            given(call.get()).willThrow(serviceException(500));
            failTimes(WINDOW_SIZE);
            assertThat(circuitBreaker().getState()).isEqualTo(CircuitBreaker.State.OPEN);
            reset(call);

            assertThatThrownBy(() -> executor.execute(SERVICE, call))
                    .isInstanceOf(WebApiServiceException.class)
                    .extracting("code")
                    .isEqualTo(WebApiServiceErrorCode.DOWNSTREAM_UNAVAILABLE);
            verify(call, never()).get();
        }

        @Test
        @DisplayName("타임아웃·연결 실패(RetryableException)도 실패로 세어 서킷이 열린다")
        void networkErrors_openCircuit() {
            given(call.get()).willThrow(retryableException());

            failTimes(WINDOW_SIZE);

            assertThat(circuitBreaker().getState()).isEqualTo(CircuitBreaker.State.OPEN);
        }

        @Test
        @DisplayName("4xx는 실패로 세지 않아 계속 이어져도 서킷이 닫힌 채로 남고, 예외는 그대로 전파된다")
        void clientErrors_keepCircuitClosed() {
            WebApiServiceException exception = serviceException(400);
            given(call.get()).willThrow(exception);

            for (int i = 0; i < WINDOW_SIZE * 2; i++) {
                assertThatThrownBy(() -> executor.execute(SERVICE, call)).isSameAs(exception);
            }

            assertThat(circuitBreaker().getState()).isEqualTo(CircuitBreaker.State.CLOSED);
            assertThat(circuitBreaker().getMetrics().getNumberOfFailedCalls()).isZero();
            verify(call, times(WINDOW_SIZE * 2)).get();
        }

        @Test
        @DisplayName("half-open에서 허용된 호출이 성공하면 서킷이 다시 닫힌다")
        void halfOpenSuccess_closesCircuit() {
            given(call.get()).willThrow(serviceException(503));
            failTimes(WINDOW_SIZE);
            // wait-duration-in-open-state가 지난 상황
            circuitBreaker().transitionToHalfOpenState();
            reset(call);
            given(call.get()).willReturn("ok");

            assertThat(executor.execute(SERVICE, call)).isEqualTo("ok");
            assertThat(executor.execute(SERVICE, call)).isEqualTo("ok");

            assertThat(circuitBreaker().getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        }

        @Test
        @DisplayName("half-open에서 다시 실패하면 서킷이 다시 열린다")
        void halfOpenFailure_reopensCircuit() {
            given(call.get()).willThrow(serviceException(503));
            failTimes(WINDOW_SIZE);
            circuitBreaker().transitionToHalfOpenState();

            failTimes(2);

            assertThat(circuitBreaker().getState()).isEqualTo(CircuitBreaker.State.OPEN);
        }

        @Test
        @DisplayName("서킷은 하위 서비스마다 따로 열린다")
        void circuitsAreIsolatedPerService() {
            given(call.get()).willThrow(serviceException(500));
            failTimes(WINDOW_SIZE);
            reset(call);
            given(call.get()).willReturn("ok");

            assertThat(executor.execute(DownstreamServiceNames.SEARCH, call)).isEqualTo("ok");
            assertThat(registry.circuitBreaker(DownstreamServiceNames.SEARCH).getState())
                    .isEqualTo(CircuitBreaker.State.CLOSED);
        }
    }

    @Nested
    @DisplayName("executeOrDefault")
    class ExecuteOrDefault {

        @Test
        @DisplayName("5xx면 fallback을 반환하고, 서킷이 열린 뒤에는 하위 서비스를 부르지 않고 fallback을 반환한다")
        void serverErrors_fallback_thenOpenCircuitSkipsCall() {
            given(call.get()).willThrow(serviceException(500));
            for (int i = 0; i < WINDOW_SIZE; i++) {
                assertThat(executor.executeOrDefault(SERVICE, call, "기본값")).isEqualTo("기본값");
            }
            assertThat(circuitBreaker().getState()).isEqualTo(CircuitBreaker.State.OPEN);
            reset(call);

            assertThat(executor.executeOrDefault(SERVICE, call, "기본값")).isEqualTo("기본값");
            verify(call, never()).get();
        }

        @Test
        @DisplayName("타임아웃·연결 실패면 fallback을 반환한다")
        void networkError_fallback() {
            given(call.get()).willThrow(retryableException());

            assertThat(executor.executeOrDefault(SERVICE, call, "기본값")).isEqualTo("기본값");
        }

        @Test
        @DisplayName("4xx는 fallback하지 않고 그대로 전파한다")
        void clientError_propagates() {
            WebApiServiceException exception = serviceException(404);
            given(call.get()).willThrow(exception);

            assertThatThrownBy(() -> executor.executeOrDefault(SERVICE, call, "기본값"))
                    .isSameAs(exception);
        }

        @Test
        @DisplayName("성공하면 하위 서비스의 값을 반환한다")
        void success_returnsValue() {
            given(call.get()).willReturn("여행자");

            assertThat(executor.executeOrDefault(SERVICE, call, "기본값")).isEqualTo("여행자");
        }
    }

    private void failTimes(int count) {
        for (int i = 0; i < count; i++) {
            try {
                executor.execute(SERVICE, call);
            } catch (RuntimeException ignored) {
                // 실패 집계만 필요하다
            }
        }
    }

    private CircuitBreaker circuitBreaker() {
        return registry.circuitBreaker(SERVICE);
    }

    // FeignErrorDecoder가 하위 서비스 에러 응답을 변환한 형태와 같게 만든다
    private static WebApiServiceException serviceException(int status) {
        return new WebApiServiceException(ServiceErrorCode.of(status, null, null));
    }

    private static RetryableException retryableException() {
        Request request =
                Request.create(Request.HttpMethod.GET, "http://member-service/v1/members", Map.of(), null, null, null);
        return new RetryableException(-1, "Read timed out", Request.HttpMethod.GET, (Long) null, request);
    }
}
