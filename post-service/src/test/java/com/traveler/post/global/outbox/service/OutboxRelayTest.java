package com.traveler.post.global.outbox.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.traveler.common.support.logging.MdcKeys;
import com.traveler.post.global.kafka.KafkaProducer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    private static final String EVENT_ID = "event-1";
    private static final String PRODUCER_THREAD = "kafka-producer-network-thread | test";
    private static final String STATUS_THREAD = "outbox-status-test";
    private static final long TIMEOUT_MS = 2000;

    @Mock
    private KafkaProducer kafkaProducer;

    @Mock
    private OutboxStatusManager outboxStatusManager;

    private ExecutorService statusExecutor;
    private OutboxRelay outboxRelay;
    private CompletableFuture<SendResult<String, String>> sendFuture;

    @BeforeEach
    void setUp() {
        statusExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, STATUS_THREAD));
        outboxRelay = new OutboxRelay(kafkaProducer, outboxStatusManager, statusExecutor);
        sendFuture = new CompletableFuture<>();
        given(kafkaProducer.send(anyString(), anyString(), anyString(), anyString(), anyString()))
                .willReturn(sendFuture);
    }

    @AfterEach
    void tearDown() {
        statusExecutor.shutdownNow();
    }

    @Test
    @DisplayName("전송이 성공하면 SENT로 바꾸고, 그 갱신은 프로듀서 전송 스레드가 아닌 상태 갱신 풀에서 돈다")
    void sentUpdateRunsOffProducerThread() throws InterruptedException {
        AtomicReference<String> updatedOn = new AtomicReference<>();
        willAnswer(invocation -> {
                    updatedOn.set(Thread.currentThread().getName());
                    return null;
                })
                .given(outboxStatusManager)
                .updateToSent(EVENT_ID);

        relay();
        completeOnProducerThread(() -> sendFuture.complete(null));

        verify(outboxStatusManager, timeout(TIMEOUT_MS)).updateToSent(EVENT_ID);
        verify(outboxStatusManager, never()).updateToFailed(anyString());
        assertThat(updatedOn.get()).isEqualTo(STATUS_THREAD);
    }

    @Test
    @DisplayName("상태 갱신 스레드에 전송을 요청한 스레드의 MDC userId가 옮겨진다 (프로듀서 전송 스레드에는 값이 없다)")
    void statusUpdateCarriesCallerContext() throws InterruptedException {
        AtomicReference<String> userIdOnUpdate = new AtomicReference<>();
        willAnswer(invocation -> {
                    userIdOnUpdate.set(MDC.get(MdcKeys.USER_ID));
                    return null;
                })
                .given(outboxStatusManager)
                .updateToSent(EVENT_ID);

        MDC.put(MdcKeys.USER_ID, "7");
        try {
            relay();
        } finally {
            MDC.remove(MdcKeys.USER_ID);
        }
        completeOnProducerThread(() -> sendFuture.complete(null));

        verify(outboxStatusManager, timeout(TIMEOUT_MS)).updateToSent(EVENT_ID);
        assertThat(userIdOnUpdate.get()).isEqualTo("7");
    }

    @Test
    @DisplayName("전송이 실패하면 FAILED로 바꾼다")
    void failedSendMarksFailed() throws InterruptedException {
        relay();
        completeOnProducerThread(() -> sendFuture.completeExceptionally(new RuntimeException("broker down")));

        verify(outboxStatusManager, timeout(TIMEOUT_MS)).updateToFailed(EVENT_ID);
        verify(outboxStatusManager, never()).updateToSent(anyString());
    }

    private void relay() {
        outboxRelay.relayAsync(EVENT_ID, "topic", "10", "CREATED", "{}");
    }

    /** 실제 Kafka처럼 전송 완료를 프로듀서 전송 스레드에서 알린다 */
    private void completeOnProducerThread(Runnable completion) throws InterruptedException {
        Thread producer = new Thread(completion, PRODUCER_THREAD);
        producer.start();
        producer.join(TIMEOUT_MS);
    }
}
