package com.traveler.web.global.resilience;

import com.traveler.web.global.exception.ExceptionUtils;
import java.util.function.Predicate;

/**
 * 서킷 브레이커가 실패로 셀 예외를 고른다(yml {@code record-failure-predicate}).
 *
 * <p>하위 서비스 장애(5xx·타임아웃·연결 실패)일 때만 true를 돌려 실패로 세게 한다.
 * 4xx는 서비스가 정상이고 요청 내용이 잘못된 경우다. 이것까지 세면 잘못된 요청이 몰릴 때 멀쩡한 서비스의 서킷이 열린다.
 *
 * <p>참고: 여기서 false가 나온 예외를 Resilience4j는 무시하지 않고 성공으로 기록한다. 예외 자체는 호출한 쪽으로 그대로 던져진다.
 */
public class DownstreamFailurePredicate implements Predicate<Throwable> {

    @Override
    public boolean test(Throwable throwable) {
        return throwable instanceof Exception exception && ExceptionUtils.isRetryableException(exception);
    }
}
