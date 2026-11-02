package com.traveler.web.global.handler;

import com.traveler.common.api.handler.BaseExceptionAdvice;
import com.traveler.common.core.code.ErrorCode;
import com.traveler.common.core.response.ApiResponse;
import com.traveler.web.global.exception.WebApiServiceException;
import com.traveler.web.global.exception.code.WebApiServiceErrorCode;
import feign.RetryableException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.traveler.web")
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
@Slf4j
public class WebApiServiceExceptionAdvice implements BaseExceptionAdvice {

    /** WebApi Service에서 발생하는 커스텀 비즈니스 예외 처리 */
    @ExceptionHandler(WebApiServiceException.class)
    protected ResponseEntity<ApiResponse<Void>> handleWebApiServiceException(WebApiServiceException ex) {
        if (ex.getCause() != null) {
            log.error("[WebApi Service System Exception] Code: {}, Message: {}", ex.getCode(), ex.getMessage(), ex);
        } else {
            log.warn("[WebApi Service Business Exception] Code: {}, Message: {}", ex.getCode(), ex.getMessage());
        }
        return createErrorResponse(ex.getCode(), null);
    }

    /** 서킷이 열리기 전의 하위 서비스 타임아웃·연결 실패. 열린 뒤와 같은 503으로 응답해 같은 장애를 응답 코드로 묶어 본다 */
    @ExceptionHandler(RetryableException.class)
    protected ResponseEntity<ApiResponse<Void>> handleRetryableException(
            RetryableException ex, HttpServletRequest request) {
        log.error(
                "[Downstream Unavailable] Method: {}, URI: {}, Message: {}",
                request.getMethod(),
                request.getRequestURI(),
                ex.getMessage());
        return createErrorResponse(WebApiServiceErrorCode.DOWNSTREAM_UNAVAILABLE, null);
    }

    /**
     * [Client Disconnect] 클라이언트가 먼저 연결을 끊었을 때 소켓 write/flush에서 발생하는 IOException.
     *
     * <p>SSE(/rankings/live)는 응답이 이미 커밋된 상태라 어떤 본문도 쓸 수 없다. 이때 전역 핸들러가 ApiResponse(JSON)를 쓰려 하면
     * "No converter with preset Content-Type 'text/event-stream'"으로 실패하면서 스택트레이스가 여러 번 찍히므로, null을 반환해 요청을
     * 처리 완료로 표시하고 로그만 남긴다. 아직 커밋되지 않은 요청의 IOException은 진짜 장애이므로 500으로 응답한다.
     */
    @ExceptionHandler(IOException.class)
    protected ResponseEntity<ApiResponse<Void>> handleIOException(
            IOException ex, HttpServletRequest request, HttpServletResponse response) {
        if (response.isCommitted()) {
            log.debug("[Client Disconnected] URI: {}, Message: {}", request.getRequestURI(), ex.getMessage());
            return null;
        }
        log.error("[IO Exception] Method: {}, URI: {}", request.getMethod(), request.getRequestURI(), ex);
        return createErrorResponse(ErrorCode.INTERNAL_SERVER_ERROR, null);
    }
}
