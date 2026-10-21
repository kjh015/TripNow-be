package com.traveler.post.global.kafka;

import static com.traveler.post.testsupport.KafkaTestSupport.header;
import static com.traveler.post.testsupport.KafkaTestSupport.stubSuccessfulSend;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

@ExtendWith(MockitoExtension.class)
class KafkaProducerTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks
    private KafkaProducer kafkaProducer;

    @BeforeEach
    void setUp() {
        stubSuccessfulSend(kafkaTemplate);
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
}
