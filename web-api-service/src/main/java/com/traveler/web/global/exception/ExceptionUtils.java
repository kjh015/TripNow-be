package com.traveler.web.global.exception;

import feign.RetryableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.cloud.client.circuitbreaker.NoFallbackAvailableException;

/**
 * 예외 검사 및 처리를 위한 공통 유틸리티 클래스
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ExceptionUtils {

    /** Spring Cloud CircuitBreaker가 감싼 예외(NoFallbackAvailableException)면 원인 예외를, 아니면 그대로 반환한다 */
    public static Throwable unwrapCircuitBreaker(Throwable e) {
        if (e instanceof NoFallbackAvailableException && e.getCause() != null) {
            return e.getCause();
        }
        return e;
    }

    /** 하위 서비스 장애인가: 서킷 열림, 타임아웃·연결 실패, 하위 서비스 5xx. 서킷이 감싼 예외는 원인으로 판단한다 */
    public static boolean isDownstreamFailure(Throwable e) {
        Throwable cause = unwrapCircuitBreaker(e);
        if (cause instanceof CallNotPermittedException) {
            return true;
        }

        // 네트워크 레벨 장애
        if (cause instanceof RetryableException
                || cause instanceof SocketTimeoutException
                || cause instanceof ConnectException) {
            return true;
        }

        // 하위 서비스의 일시적 서버 장애 (5xx)
        if (cause instanceof WebApiServiceException webException) {
            int status = webException.getCode().getStatus();
            return status >= 500 && status < 600;
        }

        return false;
    }
}
