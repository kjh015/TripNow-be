package com.traveler.member.global.outbox.scheduler;

import com.traveler.member.global.outbox.service.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxScheduler {
    private final OutboxService outboxService;

    @Scheduled(fixedDelay = 180000) // 3분마다 실행
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
