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

    public static final String OUTBOX_TASK_EXECUTOR = "outboxTaskExecutor";
    public static final String OUTBOX_STATUS_EXECUTOR = "outboxStatusExecutor";
    public static final String POST_IMAGE_EXECUTOR = "postImageExecutor";

    /**
     * 게시글 이미지 확정(S3 HEAD + 복사)을 병렬로 처리한다. 차례로 처리하면 10장일 때 S3 시간만으로 Feign 제한(1.5초)을 넘을 수 있다.
     * 요청 스레드는 작업이 모두 끝날 때까지 기다린다.
     */
    @Bean(name = POST_IMAGE_EXECUTOR)
    public Executor postImageExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("post-image-");
        // 큐가 가득 차면 요청 스레드가 직접 처리한다.
        executor.setRejectedExecutionHandler(
                new ThrottledRejectionLoggingHandler("PostImageExecutor", new ThreadPoolExecutor.CallerRunsPolicy()));
        // 요청 스레드의 trace와 MDC userId를 작업 스레드로 옮긴다.
        executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
        // 종료 시 진행 중인 요청의 복사를 마친다
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        executor.initialize();
        return executor;
    }

    @Bean(name = OUTBOX_TASK_EXECUTOR)
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

    /**
     * Kafka 전송 완료 후 Outbox 상태(SENT/FAILED) 갱신을 맡는다.
     * 프로듀서 전송 스레드(1개)가 DB 커밋을 기다리면 뒤 메시지 전송도 멈추므로 갱신을 이 풀로 넘긴다.
     */
    @Bean(name = OUTBOX_STATUS_EXECUTOR)
    public Executor outboxStatusExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 갱신마다 커넥션 1개를 쓰므로 Hikari 풀(10개)의 절반 이하로 두어 요청 처리 몫을 남긴다
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(2000);
        executor.setThreadNamePrefix("outbox-status-");
        // 큐가 가득 차면 호출 스레드(프로듀서 전송 스레드)에서 직접 갱신한다. 개선 전과 같은 동작이고 갱신이 빠지지 않는다
        executor.setRejectedExecutionHandler(new ThrottledRejectionLoggingHandler(
                "OutboxStatusExecutor", new ThreadPoolExecutor.CallerRunsPolicy()));
        // 종료 시 남은 갱신을 마친다. 못 마친 행은 INIT로 남아 재시도 스케줄러가 다시 보낸다
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        executor.initialize();
        return executor;
    }
}
