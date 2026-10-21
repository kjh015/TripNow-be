package com.traveler.post.global.outbox.service;

import static com.traveler.post.testsupport.KafkaTestSupport.header;
import static com.traveler.post.testsupport.KafkaTestSupport.stubSuccessfulSend;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.traveler.post.domain.post.dto.message.PostMessage;
import com.traveler.post.domain.post.enums.PostEventType;
import com.traveler.post.global.kafka.KafkaProducer;
import com.traveler.post.global.outbox.entity.Outbox;
import com.traveler.post.global.outbox.event.OutboxEvent;
import com.traveler.post.global.outbox.mapper.OutboxMapper;
import com.traveler.post.global.outbox.repository.OutboxRepository;
import java.util.List;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

@ExtendWith(MockitoExtension.class)
class OutboxServiceTest {

    private static final String TOPIC = "post-service.post.events";
    private static final Long POST_ID = 10L;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private OutboxStatusManager outboxStatusManager;

    @Mock
    private OutboxMapper outboxMapper;

    private OutboxService outboxService;

    @BeforeEach
    void setUp() {
        // 발행 경로(OutboxRelay → KafkaProducer)는 진짜로 두고 KafkaTemplate만 가짜로 둔다
        OutboxRelay outboxRelay = new OutboxRelay(new KafkaProducer(kafkaTemplate), outboxStatusManager);
        outboxService =
                new OutboxService(outboxRepository, new ObjectMapper(), outboxMapper, outboxStatusManager, outboxRelay);
        stubSuccessfulSend(kafkaTemplate);
    }

    @Test
    @DisplayName("즉시 발행 경로는 aggregateId를 키로, eventId를 헤더로 싣는다")
    void publishSendsKeyAndEventId() {
        OutboxEvent event = OutboxEvent.of(POST_ID, PostEventType.DELETED, TOPIC, new PostMessage.DeletedDTO(POST_ID));

        outboxService.publish(event);

        ProducerRecord<String, String> record = captureSentRecords(1).get(0);
        assertThat(record.key()).isEqualTo("10");
        assertThat(header(record, KafkaProducer.EVENT_ID_HEADER)).isEqualTo(event.eventId());
        assertThat(header(record, KafkaProducer.EVENT_TYPE_HEADER)).isEqualTo("DELETED");
        verify(outboxStatusManager).updateToSent(event.eventId());
    }

    @Test
    @DisplayName("재시도 경로도 저장된 aggregateId·eventId로 같은 키와 헤더를 싣는다")
    void retrySendsSameKeyAndEventId() {
        OutboxEvent event = OutboxEvent.of(POST_ID, PostEventType.DELETED, TOPIC, new PostMessage.DeletedDTO(POST_ID));
        Outbox outbox = Outbox.builder()
                .eventId(event.eventId())
                .aggregateType(event.aggregateType())
                .aggregateId(event.aggregateId())
                .eventType(event.eventType())
                .topic(event.topic())
                .payload("{\"postId\":10}")
                .build();
        given(outboxStatusManager.claimRetryableMessages(any(), anyInt(), any()))
                .willReturn(List.of(outbox));

        outboxService.retry();
        outboxService.publish(event);

        List<ProducerRecord<String, String>> records = captureSentRecords(2);
        ProducerRecord<String, String> retried = records.get(0);
        ProducerRecord<String, String> published = records.get(1);
        assertThat(retried.key()).isEqualTo("10").isEqualTo(published.key());
        assertThat(header(retried, KafkaProducer.EVENT_ID_HEADER))
                .isEqualTo(event.eventId())
                .isEqualTo(header(published, KafkaProducer.EVENT_ID_HEADER));
        assertThat(header(retried, KafkaProducer.EVENT_TYPE_HEADER)).isEqualTo("DELETED");
    }

    @Test
    @DisplayName("aggregateId가 없으면 키 없이 발행한다")
    void publishWithoutAggregateIdHasNullKey() {
        OutboxEvent event = OutboxEvent.of(null, PostEventType.DELETED, TOPIC, new PostMessage.DeletedDTO(null));

        outboxService.publish(event);

        assertThat(captureSentRecords(1).get(0).key()).isNull();
    }

    @SuppressWarnings("unchecked")
    private List<ProducerRecord<String, String>> captureSentRecords(int count) {
        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate, times(count)).send(captor.capture());
        return captor.getAllValues();
    }
}
