package com.traveler.common.support.logging.context;

import com.traveler.common.support.logging.MdcKeys;
import io.micrometer.context.ThreadLocalAccessor;
import org.slf4j.MDC;

/**
 * MDC userId를 context-propagation에 등록해 비동기 스레드로 옮긴다.
 *
 * <p>tracing의 컨텍스트 전파는 trace(span)만 옮기고 MDC userId는 옮기지 않는다. 이 accessor를 등록하면
 * {@code ContextPropagatingTaskDecorator}를 단 Executor가 작업을 받을 때 호출 스레드의 userId를 함께 캡처하고,
 * 작업이 끝나면 작업 스레드의 값을 원래대로 되돌린다.
 *
 * <p>등록은 {@code META-INF/services/io.micrometer.context.ThreadLocalAccessor}(ServiceLoader)로 한다.
 */
public class UserIdMdcAccessor implements ThreadLocalAccessor<String> {

    @Override
    public Object key() {
        return MdcKeys.USER_ID;
    }

    @Override
    public String getValue() {
        return MDC.get(MdcKeys.USER_ID);
    }

    @Override
    public void setValue(String value) {
        MDC.put(MdcKeys.USER_ID, value);
    }

    @Override
    public void setValue() {
        MDC.remove(MdcKeys.USER_ID);
    }

    @Override
    public void restore() {
        MDC.remove(MdcKeys.USER_ID);
    }
}
