package com.traveler.post.integration.useractivity.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.traveler.post.domain.post.repository.PostStatsSyncRepository;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

class UserActivityEventConsumerTest {

    private final PostStatsSyncRepository postStatsSyncRepository = mock(PostStatsSyncRepository.class);
    private final Acknowledgment ack = mock(Acknowledgment.class);
    private final UserActivityEventConsumer consumer =
            new UserActivityEventConsumer(postStatsSyncRepository, new ObjectMapper());

    @Test
    @DisplayName("POST_VIEWED는 event-id 헤더로 중복을 판단해 조회수를 올린다")
    void postViewed() {
        consumer.consume("{\"postId\":10}", "POST_VIEWED", null, "POST_VIEWED:trace-1", ack);

        verify(postStatsSyncRepository).incrementViewCountIfFirst("POST_VIEWED:trace-1", 10L, Duration.ofMinutes(10));
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("event-id 헤더가 없으면 조회수를 올리지 않고 커밋한다")
    void missingEventId() {
        consumer.consume("{\"postId\":10}", "POST_VIEWED", null, null, ack);

        verify(postStatsSyncRepository, never()).incrementViewCountIfFirst(anyString(), anyLong(), any());
        verify(ack).acknowledge();
    }
}
