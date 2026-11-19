package com.traveler.web.global.handler;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.traveler.common.api.converter.ExceptionConverter;
import com.traveler.common.api.handler.GeneralExceptionAdvice;
import com.traveler.web.global.exception.WebApiServiceException;
import com.traveler.web.global.exception.code.WebApiServiceErrorCode;
import com.traveler.web.global.feign.decoder.ServiceErrorCode;
import com.traveler.web.global.resilience.DownstreamServiceNames;
import feign.RetryableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.circuitbreaker.NoFallbackAvailableException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영과 같이 GeneralExceptionAdvice(Exception 처리, LOWEST_PRECEDENCE)도 함께 등록해, 서킷이 감싼 예외가
 * 이 Advice의 원인 예외 처리로 가는지 실제 순서로 확인한다.
 */
class WebApiServiceExceptionAdviceTest {

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new FailingController())
            .setControllerAdvice(
                    new WebApiServiceExceptionAdvice(), new GeneralExceptionAdvice(new ExceptionConverter()))
            .build();

    @Test
    @DisplayName("하위 서비스 타임아웃·연결 실패는 서킷이 열렸을 때와 같은 503 WEB503_1로 응답한다")
    void respondsServiceUnavailableOnRetryableException() throws Exception {
        mockMvc.perform(get("/timeout"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("WEB503_1"));
    }

    @Test
    @DisplayName("재발급 요청에 리프레시 토큰이 없으면 401 WEB401_1로 응답한다")
    void respondsUnauthorizedOnRefreshTokenMissing() throws Exception {
        mockMvc.perform(get("/refresh-token-missing"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("WEB401_1"));
    }

    @Test
    @DisplayName("서킷이 감싼 서킷 열림 예외는 503 WEB503_1로 응답한다")
    void respondsServiceUnavailableOnWrappedCallNotPermitted() throws Exception {
        mockMvc.perform(get("/wrapped/circuit-open"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("WEB503_1"));
    }

    @Test
    @DisplayName("감싸지 않은 서킷 열림 예외도 503 WEB503_1로 응답한다")
    void respondsServiceUnavailableOnCallNotPermitted() throws Exception {
        mockMvc.perform(get("/circuit-open"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("WEB503_1"));
    }

    @Test
    @DisplayName("서킷이 감싼 하위 서비스 4xx는 하위 코드와 상태 그대로 응답한다")
    void respondsDownstreamCodeOnWrappedClientError() throws Exception {
        mockMvc.perform(get("/wrapped/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER404_1"));
    }

    @Test
    @DisplayName("서킷이 감싼 타임아웃·연결 실패는 503 WEB503_1로 응답한다")
    void respondsServiceUnavailableOnWrappedRetryableException() throws Exception {
        mockMvc.perform(get("/wrapped/timeout"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("WEB503_1"));
    }

    @RestController
    static class FailingController {

        @GetMapping("/timeout")
        String timeout() {
            throw mock(RetryableException.class);
        }

        @GetMapping("/refresh-token-missing")
        String refreshTokenMissing() {
            throw new WebApiServiceException(WebApiServiceErrorCode.REFRESH_TOKEN_MISSING);
        }

        @GetMapping("/circuit-open")
        String circuitOpen() {
            throw callNotPermitted();
        }

        @GetMapping("/wrapped/circuit-open")
        String wrappedCircuitOpen() {
            throw wrapped(callNotPermitted());
        }

        @GetMapping("/wrapped/not-found")
        String wrappedNotFound() {
            throw wrapped(new WebApiServiceException(ServiceErrorCode.of(404, "MEMBER404_1", "회원 없음")));
        }

        @GetMapping("/wrapped/timeout")
        String wrappedTimeout() {
            throw wrapped(mock(RetryableException.class));
        }

        // Spring Cloud CircuitBreaker(fallback 없음)가 Feign 호출 예외를 감싸는 형태와 같게 만든다
        private static NoFallbackAvailableException wrapped(Throwable cause) {
            return new NoFallbackAvailableException("No fallback available.", cause);
        }

        private static CallNotPermittedException callNotPermitted() {
            CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults(DownstreamServiceNames.MEMBER);
            circuitBreaker.transitionToOpenState();
            return CallNotPermittedException.createCallNotPermittedException(circuitBreaker);
        }
    }
}
