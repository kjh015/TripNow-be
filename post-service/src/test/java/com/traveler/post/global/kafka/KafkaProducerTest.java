package com.traveler.post.global.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

class KafkaProducerTest {

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);

    private final KafkaProducer kafkaProducer = new KafkaProducer(kafkaTemplate);

    @BeforeEach
    void setUp() {
        given(kafkaTemplate.send(any(ProducerRecord.class))).willAnswer(invocation -> {
            ProducerRecord<String, String> sent = invocation.getArgument(0);
            RecordMetadata metadata = new RecordMetadata(new TopicPartition(sent.topic(), 0), 0, 0, 0, 0, 0);
            return CompletableFuture.completedFuture(new SendResult<>(sent, metadata));
        });
    }

    @Test
    @DisplayName("키와 event-type·event-id 헤더를 실어 발행한다")
    void sendsKeyAndHeaders() {
        kafkaProducer.send("post-service.post.events", "10", "evt-1", "UPDATED", "{\"postId\":10}");

        ProducerRecord<String, String> record = captureSentRecord();
        assertThat(record.topic()).isEqualTo("post-service.post.events");
        assertThat(record.key()).isEqualTo("10");
        assertThat(record.value()).isEqualTo("{\"postId\":10}");
        assertThat(header(record, KafkaProducer.EVENT_TYPE_HEADER)).isEqualTo("UPDATED");
        assertThat(header(record, KafkaProducer.EVENT_ID_HEADER)).isEqualTo("evt-1");
    }

    @SuppressWarnings("unchecked")
    private ProducerRecord<String, String> captureSentRecord() {
        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        return captor.getValue();
    }

    private static String header(ProducerRecord<String, String> record, String name) {
        return new String(record.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
    }
}
