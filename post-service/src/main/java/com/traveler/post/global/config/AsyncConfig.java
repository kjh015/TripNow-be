package com.traveler.post.global.config;

import com.traveler.common.support.logging.executor.ThrottledRejectionLoggingHandler;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "outboxTaskExecutor")
    public Executor outboxTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 핵심 스레드 수: 항상 유지되는 스레드
        executor.setCorePoolSize(10);
        // 최대 스레드 수: 큐가 찼을 때 추가로 생성되는 최대치
        executor.setMaxPoolSize(20);
        // 큐 용량: 작업들이 대기하는 공간
        executor.setQueueCapacity(500);
        // 스레드 이름 접두사: 모니터링 및 디버깅 시 식별 용이
        executor.setThreadNamePrefix("outbox-task-");
        // 큐가 가득 찼을 때 호출 스레드에서 직접 실행 (backpressure)
        // 요청 스레드가 Kafka 전송을 떠안아 응답이 느려지는 원인이 되므로 발생 사실을 로그로 남긴다
        executor.setRejectedExecutionHandler(
                new ThrottledRejectionLoggingHandler("OutboxExecutor", new ThreadPoolExecutor.CallerRunsPolicy()));
        // 요청 스레드의 trace와 MDC userId를 작업 스레드로 옮긴다.
        executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
        // 종료 시 대기 설정
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        executor.initialize();
        return executor;
    }
}
