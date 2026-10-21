package com.traveler.search.domain.post.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.traveler.search.domain.post.dto.message.PostSearchMessage;
import com.traveler.search.domain.post.service.PostSearchCommandService;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.kafka.support.Acknowledgment;

@ExtendWith(MockitoExtension.class)
class PostSearchMessageConsumerTest {

    @Mock
    private PostSearchCommandService commandService;

    @Mock
    private Acknowledgment ack;

    // Spring Boot가 앱에 주입하는 것과 같은 설정의 진짜 ObjectMapper
    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();

    private PostSearchMessageConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new PostSearchMessageConsumer(commandService, objectMapper);
    }

    @Test
    @DisplayName("CREATED는 게시글 생성 메시지로 변환해 문서를 만들고 커밋한다")
    void created() {
        String payload =
                """
                {"postId":10,"memberId":1,"title":"경주 여행","content":"본문","category":"FOOD","region":"GYEONGGI",
                 "travelPlace":"황리단길","address":"경주시","createdAt":"2026-10-01T00:00:00Z",
                 "images":[{"imageKey":"posts/1/a.png","sortOrder":0}]}
                """;

        consumer.consume(payload, "CREATED", "10", "event-1", ack);

        ArgumentCaptor<PostSearchMessage.CreatedDTO> captor =
                ArgumentCaptor.forClass(PostSearchMessage.CreatedDTO.class);
        verify(commandService).createDocument(captor.capture());
        assertThat(captor.getValue().postId()).isEqualTo(10L);
        assertThat(captor.getValue().category()).isEqualTo("FOOD");
        assertThat(captor.getValue().createdAt()).isEqualTo(Instant.parse("2026-10-01T00:00:00Z"));
        assertThat(captor.getValue().images()).containsExactly(new PostSearchMessage.ImageInfo("posts/1/a.png", 0));
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("UPDATED는 부분 갱신으로 처리한다")
    void updated() {
        consumer.consume("{\"postId\":10,\"title\":\"새 제목\"}", "UPDATED", "10", "event-2", ack);

        verify(commandService)
                .updateDocument(
                        new PostSearchMessage.UpdatedDTO(10L, "새 제목", null, null, null, null, null, null, null));
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("DELETED는 문서를 삭제한다")
    void deleted() {
        consumer.consume("{\"postId\":10}", "DELETED", "10", "event-3", ack);

        verify(commandService).deleteDocument(new PostSearchMessage.DeletedDTO(10L));
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("STAT_UPDATED는 통계 갱신으로 처리한다")
    void statUpdated() {
        consumer.consume(
                "{\"postId\":10,\"starAvg\":4.5,\"commentCount\":2,\"likeCount\":3,\"viewCount\":100}",
                "STAT_UPDATED",
                "10",
                "event-4",
                ack);

        verify(commandService).updateStatistics(new PostSearchMessage.StatUpdatedDTO(10L, 4.5, 2L, 3L, 100L));
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("event-type 헤더가 없으면 처리하지 않고 커밋해 다음 메시지로 넘어간다")
    void missingEventType() {
        consumer.consume("{\"postId\":10}", null, "10", "event-5", ack);

        verifyNoInteractions(commandService);
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("알 수 없는 이벤트 타입은 처리하지 않고 커밋한다")
    void unknownEventType() {
        consumer.consume("{\"postId\":10}", "IMAGE_DELETE", "10", "event-6", ack);

        verifyNoInteractions(commandService);
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("JSON 형식이 깨지면 예외를 던지고 커밋하지 않는다 (에러 핸들러가 DLT로 보낸다)")
    void malformedPayload() {
        assertThatThrownBy(() -> consumer.consume("{not-json", "CREATED", "10", "event-7", ack))
                .isInstanceOf(JsonProcessingException.class);

        verifyNoInteractions(commandService);
        verify(ack, never()).acknowledge();
    }

    @Test
    @DisplayName("문서 처리 중 예외가 나면 커밋하지 않아 재시도 대상이 된다")
    void serviceFailure() {
        willThrow(new IllegalStateException("es down")).given(commandService).deleteDocument(any());

        assertThatThrownBy(() -> consumer.consume("{\"postId\":10}", "DELETED", "10", "event-8", ack))
                .isInstanceOf(IllegalStateException.class);

        verify(ack, never()).acknowledge();
    }
}
