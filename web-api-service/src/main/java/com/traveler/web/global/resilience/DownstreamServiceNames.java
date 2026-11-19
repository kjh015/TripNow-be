package com.traveler.web.global.resilience;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 하위 서비스 이름. @FeignClient의 name이자 서킷 이름이다. 서킷은 하위 서비스 단위로 하나씩 둔다(메서드 단위로 만들지 않는다).
 *
 * <p>값은 yml {@code resilience4j.circuitbreaker.instances}의 키와 같아야 한다. 다르면 {@code DownstreamCircuitBreakerConfig}의
 * resolver가 첫 호출에서 예외를 던지고, {@code DownstreamCircuitBreakerWireMockTest}가 빌드에서 잡는다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DownstreamServiceNames {
    public static final String MEMBER = "member-service";
    public static final String POST = "post-service";
    public static final String SEARCH = "search-service";
    public static final String USER_ACTIVITY = "user-activity-service";
}
