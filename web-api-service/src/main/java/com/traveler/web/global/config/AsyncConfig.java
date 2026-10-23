package com.traveler.web.global.config;

import com.traveler.common.support.logging.executor.ThrottledRejectionLoggingHandler;
import com.traveler.web.domain.search.service.RankingService;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {
    @Bean(name = "sseExecutor")
    public Executor sseExecutor(ObjectProvider<RankingService> rankingService) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("SSE-Broker-");
        // 거부 동작(AbortPolicy)은 그대로 두고 로그만 남긴다. RankingService는 이 Executor에 의존하므로 지연 조회한다
        executor.setRejectedExecutionHandler(new ThrottledRejectionLoggingHandler(
                "SseExecutor",
                new ThreadPoolExecutor.AbortPolicy(),
                () -> "activeEmitters=" + rankingService.getObject().getActiveEmitterCount()));
        // 작업을 넘긴 스레드의 trace와 MDC userId를 작업 스레드로 옮긴다
        executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
        executor.initialize();
        return executor;
    }
}
