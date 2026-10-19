package com.traveler.common.support.logging.executor;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class ThrottledRejectionLoggingHandlerTest {

    private final ThreadPoolExecutor executor =
            new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1));

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    @DisplayName("간격 안의 거부는 한 번만 남기고, 거부 정책은 매번 실행한다")
    void logsOncePerIntervalAndAlwaysDelegates(CapturedOutput output) {
        AtomicInteger delegated = new AtomicInteger();
        ThrottledRejectionLoggingHandler handler = new ThrottledRejectionLoggingHandler(
                "TestExecutor", (task, ex) -> delegated.incrementAndGet(), () -> "ctx=1", Duration.ofMinutes(1));

        for (int i = 0; i < 5; i++) {
            handler.rejectedExecution(() -> {}, executor);
        }

        assertThat(delegated).hasValue(5);
        assertThat(output.getAll().lines().filter(line -> line.contains("스레드풀 포화")))
                .singleElement()
                .asString()
                .contains("[TestExecutor]")
                .contains("rejected=1")
                .contains("ctx=1");
    }

    @Test
    @DisplayName("간격이 지나면 그 사이 합산한 거부 건수와 함께 다시 남긴다")
    void logsAccumulatedCountAfterInterval(CapturedOutput output) {
        ThrottledRejectionLoggingHandler handler =
                new ThrottledRejectionLoggingHandler("TestExecutor", (task, ex) -> {}, () -> "", Duration.ZERO);

        handler.rejectedExecution(() -> {}, executor);
        handler.rejectedExecution(() -> {}, executor);

        assertThat(output.getAll().lines().filter(line -> line.contains("스레드풀 포화")))
                .hasSize(2);
    }
}
