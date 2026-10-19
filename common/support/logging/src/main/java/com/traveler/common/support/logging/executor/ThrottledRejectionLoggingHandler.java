package com.traveler.common.support.logging.executor;

import java.time.Duration;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 거부 정책(delegate)은 그대로 실행하고, 거부가 일어났다는 사실을 WARN으로 남긴다.
 * 폭주 시 로그가 쏟아지지 않도록 일정 간격에 1회만 남기고, 그 사이 거부 건수를 합산해 함께 남긴다.
 */
public class ThrottledRejectionLoggingHandler implements RejectedExecutionHandler {

    private static final Logger log = LoggerFactory.getLogger(ThrottledRejectionLoggingHandler.class);
    private static final Duration DEFAULT_LOG_INTERVAL = Duration.ofMinutes(1);

    private final String executorName;
    private final RejectedExecutionHandler delegate;
    private final Supplier<String> context;
    private final long logIntervalNanos;

    private final AtomicLong rejectedSinceLastLog = new AtomicLong();
    private final AtomicLong lastLoggedAt;

    public ThrottledRejectionLoggingHandler(String executorName, RejectedExecutionHandler delegate) {
        this(executorName, delegate, () -> "");
    }

    /**
     * @param context 로그에 덧붙일 상황 정보 (예: "activeEmitters=12")
     */
    public ThrottledRejectionLoggingHandler(
            String executorName, RejectedExecutionHandler delegate, Supplier<String> context) {
        this(executorName, delegate, context, DEFAULT_LOG_INTERVAL);
    }

    ThrottledRejectionLoggingHandler(
            String executorName, RejectedExecutionHandler delegate, Supplier<String> context, Duration logInterval) {
        this.executorName = executorName;
        this.delegate = delegate;
        this.context = context;
        this.logIntervalNanos = logInterval.toNanos();
        // 첫 거부는 바로 남긴다
        this.lastLoggedAt = new AtomicLong(System.nanoTime() - logIntervalNanos);
    }

    @Override
    public void rejectedExecution(Runnable task, ThreadPoolExecutor executor) {
        rejectedSinceLastLog.incrementAndGet();
        logIfIntervalElapsed(executor);
        delegate.rejectedExecution(task, executor);
    }

    private void logIfIntervalElapsed(ThreadPoolExecutor executor) {
        long now = System.nanoTime();
        long last = lastLoggedAt.get();
        if (now - last < logIntervalNanos || !lastLoggedAt.compareAndSet(last, now)) {
            return;
        }
        log.warn(
                "[{}] 스레드풀 포화로 작업 거부 rejected={} policy={} active={} poolSize={} queueSize={} {}",
                executorName,
                rejectedSinceLastLog.getAndSet(0),
                delegate.getClass().getSimpleName(),
                executor.getActiveCount(),
                executor.getPoolSize(),
                executor.getQueue().size(),
                context.get());
    }
}
