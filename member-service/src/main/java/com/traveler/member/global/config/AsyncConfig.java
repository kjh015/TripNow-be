package com.traveler.member.global.config;

import com.traveler.common.support.logging.executor.ThrottledRejectionLoggingHandler;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Outbox 발행용 스레드 풀.
 * 회원 이벤트는 탈퇴 때만 발행되어 게시글보다 훨씬 적으므로 풀 크기를 줄였다.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    public static final String OUTBOX_TASK_EXECUTOR = "outboxTaskExecutor";
    public static final String OUTBOX_STATUS_EXECUTOR = "outboxStatusExecutor";

    @Bean(name = OUTBOX_TASK_EXECUTOR)
    public Executor outboxTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("outbox-task-");
        // 큐가 가득 찼을 때 호출 스레드에서 직접 실행 (backpressure)
        executor.setRejectedExecutionHandler(
                new ThrottledRejectionLoggingHandler("OutboxExecutor", new ThreadPoolExecutor.CallerRunsPolicy()));
        // 요청 스레드의 trace와 MDC userId를 작업 스레드로 옮긴다.
        executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        executor.initialize();
        return executor;
    }

    /**
     * Kafka 전송 완료 후 Outbox 상태(SENT/FAILED) 갱신을 맡는다.
     * 프로듀서 전송 스레드(1개)가 DB 커밋을 기다리면 뒤 메시지 전송도 멈추므로 갱신을 이 풀로 넘긴다.
     */
    @Bean(name = OUTBOX_STATUS_EXECUTOR)
    public Executor outboxStatusExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 갱신마다 커넥션 1개를 쓰므로 Hikari 풀(10개)의 절반 이하로 둔다
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("outbox-status-");
        // 큐가 가득 차면 호출 스레드(프로듀서 전송 스레드)에서 직접 갱신한다. 갱신이 빠지지 않는다
        executor.setRejectedExecutionHandler(new ThrottledRejectionLoggingHandler(
                "OutboxStatusExecutor", new ThreadPoolExecutor.CallerRunsPolicy()));
        // 종료 시 남은 갱신을 마친다. 못 마친 행은 INIT로 남아 재시도 스케줄러가 다시 보낸다
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        executor.initialize();
        return executor;
    }
}
