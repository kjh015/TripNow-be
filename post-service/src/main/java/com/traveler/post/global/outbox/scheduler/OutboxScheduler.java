package com.traveler.post.global.outbox.scheduler;

import com.traveler.post.global.outbox.service.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxScheduler {
    private final OutboxService outboxService;

    // 실패 행은 nextRetryAt(1초부터 두 배씩, 최대 60초)이 지나야 대상이 되므로 짧게 돈다
    @Scheduled(fixedDelay = 1000)
    public void retryFailedMessages() {
        try {
            outboxService.retry();
        } catch (Exception e) {
            log.error("Outbox retry 실패", e);
        }
    }

    @Scheduled(cron = "0 0 4 * * *")
    public void cleanupOldSentMessages() {
        try {
            outboxService.cleanupOldSentMessages(7);
        } catch (Exception e) {
            log.error("Outbox cleanup 실패", e);
        }
    }
}
