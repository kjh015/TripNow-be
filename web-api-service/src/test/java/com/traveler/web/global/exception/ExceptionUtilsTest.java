package com.traveler.web.global.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.traveler.web.global.feign.decoder.ServiceErrorCode;
import com.traveler.web.global.resilience.DownstreamServiceNames;
import feign.Request;
import feign.RetryableException;
import feign.codec.DecodeException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.cloud.client.circuitbreaker.NoFallbackAvailableException;

class ExceptionUtilsTest {

    @Nested
    @DisplayName("unwrapCircuitBreaker")
    class UnwrapCircuitBreaker {

        @Test
        @DisplayName("서킷이 감싼 예외면 원인 예외를 반환한다")
        void wrapped_returnsCause() {
            WebApiServiceException cause = serviceException(500);

            assertThat(ExceptionUtils.unwrapCircuitBreaker(wrapped(cause))).isSameAs(cause);
        }

        @Test
        @DisplayName("감싸지 않은 예외는 그대로 반환한다")
        void notWrapped_returnsItself() {
            WebApiServiceException exception = serviceException(500);

            assertThat(ExceptionUtils.unwrapCircuitBreaker(exception)).isSameAs(exception);
        }

        @Test
        @DisplayName("원인이 없는 NoFallbackAvailableException은 그대로 반환한다")
        void wrappedWithoutCause_returnsItself() {
            NoFallbackAvailableException exception = new NoFallbackAvailableException("No fallback available.", null);

            assertThat(ExceptionUtils.unwrapCircuitBreaker(exception)).isSameAs(exception);
        }
    }

    @Nested
    @DisplayName("isDownstreamFailure")
    class IsDownstreamFailure {

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.traveler.web.global.exception.ExceptionUtilsTest#cases")
        @DisplayName("감싸지 않은 예외")
        void notWrapped(String name, Throwable exception, boolean expected) {
            assertThat(ExceptionUtils.isDownstreamFailure(exception)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.traveler.web.global.exception.ExceptionUtilsTest#cases")
        @DisplayName("서킷이 감싼 예외는 원인으로 판단한다")
        void wrappedByCircuitBreaker(String name, Throwable exception, boolean expected) {
            assertThat(ExceptionUtils.isDownstreamFailure(wrapped(exception))).isEqualTo(expected);
        }
    }

    static Stream<Arguments> cases() {
        return Stream.of(
                Arguments.of("서킷 열림", callNotPermitted(), true),
                Arguments.of("하위 서비스 5xx", serviceException(503), true),
                Arguments.of("타임아웃·연결 실패(RetryableException)", retryableException(), true),
                Arguments.of("하위 서비스 4xx", serviceException(404), false),
                Arguments.of("응답 변환 실패(DecodeException)", decodeException(), false));
    }

    private static NoFallbackAvailableException wrapped(Throwable cause) {
        return new NoFallbackAvailableException("No fallback available.", cause);
    }

    private static CallNotPermittedException callNotPermitted() {
        CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults(DownstreamServiceNames.MEMBER);
        circuitBreaker.transitionToOpenState();
        return CallNotPermittedException.createCallNotPermittedException(circuitBreaker);
    }

    // FeignErrorDecoder가 하위 서비스 에러 응답을 변환한 형태와 같게 만든다
    private static WebApiServiceException serviceException(int status) {
        return new WebApiServiceException(ServiceErrorCode.of(status, null, null));
    }

    private static Request request() {
        return Request.create(Request.HttpMethod.GET, "http://member-service/v1/members", Map.of(), null, null, null);
    }

    private static RetryableException retryableException() {
        return new RetryableException(-1, "Read timed out", Request.HttpMethod.GET, (Long) null, request());
    }

    private static DecodeException decodeException() {
        return new DecodeException(200, "result 없음", request(), serviceException(500));
    }
}
