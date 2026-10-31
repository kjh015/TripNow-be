package com.traveler.search.domain.like.listener;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.traveler.search.domain.like.dto.message.LikeSearchMessage;
import com.traveler.search.domain.like.service.LikeSearchCommandService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.kafka.support.Acknowledgment;

@ExtendWith(MockitoExtension.class)
class LikeSearchMessageConsumerTest {

    @Mock
    private LikeSearchCommandService commandService;

    @Mock
    private Acknowledgment ack;

    // Spring Boot가 앱에 주입하는 것과 같은 설정의 진짜 ObjectMapper
    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();

    private LikeSearchMessageConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new LikeSearchMessageConsumer(commandService, objectMapper);
    }

    @Test
    @DisplayName("ADDED는 좋아요 문서를 만들고, REMOVED는 삭제한다")
    void addedAndRemoved() {
        consumer.consume(
                "{\"likeId\":3,\"postId\":10,\"memberId\":1,\"createdAt\":\"2026-10-01T00:00:00Z\"}",
                "ADDED",
                "3",
                "event-1",
                ack);
        consumer.consume("{\"likeId\":3}", "REMOVED", "3", "event-2", ack);

        verify(commandService)
                .addDocument(new LikeSearchMessage.AddedDTO(3L, 10L, 1L, Instant.parse("2026-10-01T00:00:00Z")));
        verify(commandService).removeDocument(new LikeSearchMessage.RemovedDTO(3L));
        verify(ack, times(2)).acknowledge();
    }

    @Test
    @DisplayName("event-type 누락과 알 수 없는 타입은 처리 없이 커밋한다")
    void missingOrUnknownEventType() {
        consumer.consume("{\"likeId\":3}", null, "3", "event-3", ack);
        consumer.consume("{\"likeId\":3}", "CREATED", "3", "event-4", ack);

        verifyNoInteractions(commandService);
        verify(ack, times(2)).acknowledge();
    }

    @Test
    @DisplayName("BATCH_REMOVED는 묶인 좋아요를 모두 삭제한다")
    void batchRemoved() {
        consumer.consume("{\"postId\":10,\"likeIds\":[5,6]}", "BATCH_REMOVED", "10", "event-6", ack);

        verify(commandService).removeDocuments(new LikeSearchMessage.BatchRemovedDTO(10L, List.of(5L, 6L)));
        verify(ack).acknowledge();
    }
}
