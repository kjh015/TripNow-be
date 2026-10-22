package com.traveler.common.support.logging.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.traveler.common.support.logging.MdcKeys;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;

/** ServiceLoader로 등록한 UserIdMdcAccessor가 ContextPropagatingTaskDecorator로 MDC userId를 옮기는지 확인한다 */
class UserIdMdcAccessorTest {

    // 스레드 하나를 재사용해 앞 작업의 값이 다음 작업에 남는지 본다
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ContextPropagatingTaskDecorator decorator = new ContextPropagatingTaskDecorator();

    @AfterEach
    void tearDown() {
        MDC.clear();
        worker.shutdownNow();
    }

    @Test
    @DisplayName("작업을 넘긴 스레드의 userId가 작업 스레드에 보이고, 작업이 끝나면 작업 스레드에서 지워진다")
    void propagatesUserIdAndRestoresAfterTask() throws Exception {
        MDC.put(MdcKeys.USER_ID, "7");
        AtomicReference<String> seenInTask = new AtomicReference<>();

        worker.submit(decorator.decorate(() -> seenInTask.set(MDC.get(MdcKeys.USER_ID))))
                .get();
        String leftAfterTask = worker.submit(() -> MDC.get(MdcKeys.USER_ID)).get();

        assertThat(seenInTask).hasValue("7");
        assertThat(leftAfterTask).isNull();
    }

    @Test
    @DisplayName("작업을 넘긴 스레드에 userId가 없으면 작업 스레드에도 없다")
    void doesNotSetUserIdWhenCallerHasNone() throws Exception {
        AtomicReference<String> seenInTask = new AtomicReference<>("not-run");

        worker.submit(decorator.decorate(() -> seenInTask.set(MDC.get(MdcKeys.USER_ID))))
                .get();

        assertThat(seenInTask.get()).isNull();
    }
}
