package com.traveler.search.domain.comment.listener;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.traveler.search.domain.comment.dto.message.CommentSearchMessage;
import com.traveler.search.domain.comment.service.CommentSearchCommandService;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.kafka.support.Acknowledgment;

@ExtendWith(MockitoExtension.class)
class CommentSearchMessageConsumerTest {

    @Mock
    private CommentSearchCommandService commandService;

    @Mock
    private Acknowledgment ack;

    // Spring Boot가 앱에 주입하는 것과 같은 설정의 진짜 ObjectMapper
    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();

    private CommentSearchMessageConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new CommentSearchMessageConsumer(commandService, objectMapper);
    }

    @Test
    @DisplayName("CREATED는 댓글 문서를 만든다")
    void created() {
        consumer.consume(
                "{\"commentId\":5,\"postId\":10,\"memberId\":1,\"content\":\"좋아요\",\"star\":4,"
                        + "\"createdAt\":\"2026-10-01T00:00:00Z\"}",
                "CREATED",
                "5",
                "event-1",
                ack);

        verify(commandService)
                .createDocument(new CommentSearchMessage.CreatedDTO(
                        5L, 10L, 1L, "좋아요", 4, Instant.parse("2026-10-01T00:00:00Z")));
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("UPDATED는 부분 갱신, DELETED는 삭제로 처리한다")
    void updatedAndDeleted() {
        consumer.consume("{\"commentId\":5,\"star\":2}", "UPDATED", "5", "event-2", ack);
        consumer.consume("{\"commentId\":5}", "DELETED", "5", "event-3", ack);

        verify(commandService).updateDocument(new CommentSearchMessage.UpdatedDTO(5L, null, 2, null));
        verify(commandService).deleteDocument(new CommentSearchMessage.DeletedDTO(5L));
    }

    @Test
    @DisplayName("event-type 누락과 알 수 없는 타입은 처리 없이 커밋한다")
    void missingOrUnknownEventType() {
        consumer.consume("{\"commentId\":5}", null, "5", "event-4", ack);
        consumer.consume("{\"commentId\":5}", "STAT_UPDATED", "5", "event-5", ack);

        verifyNoInteractions(commandService);
        verify(ack, times(2)).acknowledge();
    }
}
