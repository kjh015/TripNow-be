package com.traveler.web.global.resilience;

import com.traveler.web.global.exception.ExceptionUtils;
import com.traveler.web.global.exception.WebApiServiceException;
import com.traveler.web.global.exception.code.WebApiServiceErrorCode;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 하위 서비스 호출을 서비스별 서킷 브레이커로 감싸 실행한다.
 *
 * <p>하위 서비스 호출은 요청을 처리하는 스레드에서 그대로 실행한다.
 * 로그인 사용자 정보({@code UserContextHolder})는 스레드마다 따로 저장되는 값이라, 호출을 다른 스레드로 넘기면
 * {@code FeignAuthInterceptor}가 그 값을 읽지 못해 X-User-Id 등 인증 헤더가 빠진다.
 * 그래서 호출을 다른 스레드에서 실행하는 TimeLimiter와 Spring Cloud CircuitBreaker 연동은 쓰지 않는다.
 * 호출 시간 제한은 Feign connectTimeout·readTimeout이 맡는다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DownstreamCallExecutor {

    private final CircuitBreakerRegistry circuitBreakerRegistry;

    /** 서킷이 열려 있으면 하위 서비스를 호출하지 않고 {@link WebApiServiceErrorCode#DOWNSTREAM_UNAVAILABLE}(503)로 끝낸다. */
    public <T> T execute(String service, Supplier<T> call) {
        try {
            return circuitBreaker(service).executeSupplier(call);
        } catch (CallNotPermittedException ex) {
            throw new WebApiServiceException(WebApiServiceErrorCode.DOWNSTREAM_UNAVAILABLE);
        }
    }

    /**
     * 서킷이 열려 있거나 하위 서비스 장애(5xx·타임아웃·연결 실패)로 실패하면 fallback을 반환한다.
     * 4xx는 BFF의 잘못된 요청이므로 그대로 전파한다. 부가 정보(닉네임 등)에만 쓰고, 쓰기에는 쓰지 않는다.
     */
    public <T> T executeOrDefault(String service, Supplier<T> call, T fallback) {
        try {
            return circuitBreaker(service).executeSupplier(call);
        } catch (CallNotPermittedException ex) {
            // 서킷 열림은 상태가 바뀔 때만 WARN을 남긴다(CircuitBreakerStateLogger). 호출마다 남기지 않는다
            return fallback;
        } catch (RuntimeException ex) {
            if (!ExceptionUtils.isRetryableException(ex)) {
                throw ex;
            }
            log.warn("{} 장애로 대체값을 사용합니다.", service, ex);
            return fallback;
        }
    }

    /**
     * yml {@code instances}로 기동 시 만들어 둔 서킷만 꺼낸다. {@code registry.circuitBreaker(name)}은 없는 이름이면 default 설정으로
     * 새 서킷을 조용히 만들므로, 이름이 yml과 어긋난 것을 바로 드러내도록 예외를 던진다.
     */
    private CircuitBreaker circuitBreaker(String service) {
        return circuitBreakerRegistry
                .find(service)
                .orElseThrow(() -> new IllegalStateException(
                        "등록되지 않은 서킷 이름입니다. resilience4j.circuitbreaker.instances를 확인하세요. service: " + service));
    }
}
