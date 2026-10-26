package com.traveler.web.global.resilience;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * {@link DownstreamCallExecutor}에 넘기는 하위 서비스 이름. 서킷은 하위 서비스 단위로 하나씩 둔다(메서드 단위로 만들지 않는다).
 *
 * <p>값은 yml {@code resilience4j.circuitbreaker.instances}의 키와 같아야 한다. 다르면 실행기가 첫 호출에서 예외를 던지고,
 * {@code DownstreamCircuitBreakerWireMockTest}가 빌드에서 잡는다. Feign 클라이언트의 {@code name}과도 같게 둔다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DownstreamServiceNames {
    public static final String MEMBER = "member-service";
    public static final String POST = "post-service";
    public static final String SEARCH = "search-service";
    public static final String USER_ACTIVITY = "user-activity-service";
}
