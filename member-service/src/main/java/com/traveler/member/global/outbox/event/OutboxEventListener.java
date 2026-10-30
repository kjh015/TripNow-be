package com.traveler.member.global.outbox.event;

import com.traveler.member.global.config.AsyncConfig;
import com.traveler.member.global.outbox.service.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxEventListener {
    private final OutboxService outboxService;

    /** [STEP 1] 커밋 전: DB에 아웃박스 기록 및 ID 획득 */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void saveOutbox(OutboxEvent event) {
        outboxService.createOutbox(event);
    }

    /** [STEP 2] 커밋 후: 비동기로 Kafka 메시지 발행 */
    @Async(AsyncConfig.OUTBOX_TASK_EXECUTOR) // 별도 스레드 풀 사용
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishToKafka(OutboxEvent event) {
        outboxService.publish(event);
    }
}
