package com.traveler.common.support.logging;

/**
 * 서비스 공통 MDC 키. 로그 패턴(logging.yml)과 구조화 로그 필드 이름이 이 값을 따른다.
 */
public final class MdcKeys {

    /** 요청 사용자 ID. 서블릿 서비스의 UserContextInterceptor가 요청 스레드에 넣는다 */
    public static final String USER_ID = "userId";

    /** 분산 추적 ID. 같은 요청의 서비스 간 호출에서 동일한 값을 사용한다. */
    public static final String TRACE_ID = "traceId";

    /** 현재 span ID. */
    public static final String SPAN_ID = "spanId";

    private MdcKeys() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
}
