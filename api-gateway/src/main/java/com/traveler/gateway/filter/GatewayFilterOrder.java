package com.traveler.gateway.filter;

import org.springframework.core.Ordered;

/**
 * GlobalFilter의 실행 순서. 숫자가 작을수록 먼저 실행된다.
 * GlobalFilter를 추가하면 getOrder()에 숫자를 직접 쓰지 말고 여기에 상수를 추가한다.
 *
 * <pre>
 * 요청 흐름
 *   INTERNAL_HEADER_STRIP   위조 내부 헤더 제거
 *   ACCESS_LOG              접근 로그 (응답 커밋 직전에 기록)
 *   TRACE_ID_RESPONSE_HEADER  응답 헤더 X-Trace-Id
 *   라우트 필터              JwtAuthenticationFilter → RoleAuthorizationFilter
 *                           (application.yml routes[].filters 나열 순서대로 1, 2 ... 순서를 받는다)
 *   Spring Cloud Gateway 기본 필터
 *                           (NettyWriteResponseFilter -1, RouteToRequestUrlFilter 10000,
 *                            ReactiveLoadBalancerClientFilter 10150, NettyRoutingFilter LOWEST_PRECEDENCE)
 * </pre>
 */
public final class GatewayFilterOrder {

    /** 위조 내부 헤더 제거. 라우트 필터(인증 필터의 헤더 주입)를 포함한 다른 모든 필터보다 먼저 */
    public static final int INTERNAL_HEADER_STRIP = Ordered.HIGHEST_PRECEDENCE;

    /** 접근 로그. 지연 시간에 게이트웨이 처리 전체(인증, 라우팅, 하위 호출)가 들어가도록 최대한 앞에서 */
    public static final int ACCESS_LOG = INTERNAL_HEADER_STRIP + 1;

    /** 응답 헤더 X-Trace-Id. 라우트 필터(인증 실패 응답)보다 먼저 넣어야 에러 응답에도 남는다 */
    public static final int TRACE_ID_RESPONSE_HEADER = ACCESS_LOG + 1;

    private GatewayFilterOrder() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
}
