package com.traveler.search.global.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.core.JsonParseException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.SendResult;

class KafkaConfigTest {

    private static final String TOPIC = "post-service.post.events";

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);

    private final KafkaExceptionHandler kafkaExceptionHandler = mock(KafkaExceptionHandler.class);

    private CommonErrorHandler errorHandler;

    @BeforeEach
    void setUp() {
        KafkaConfig config = new KafkaConfig(new KafkaProperties(), mock(SslBundles.class), kafkaExceptionHandler);
        errorHandler = config.commonErrorHandler(kafkaTemplate);

        given(kafkaTemplate.send(any(ProducerRecord.class))).willAnswer(invocation -> {
            ProducerRecord<String, String> sent = invocation.getArgument(0);
            RecordMetadata metadata = new RecordMetadata(new TopicPartition(sent.topic(), 0), 0, 0, 0, 0, 0);
            return CompletableFuture.completedFuture(new SendResult<>(sent, metadata));
        });
    }

    @Test
    @DisplayName("재시도 불가능한 예외는 로깅 후 원본 토픽.DLT로 키·헤더를 유지한 채 발행된다")
    @SuppressWarnings("unchecked")
    void publishesToDeadLetterTopic() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(TOPIC, 0, 42L, "10", "{\"postId\":10}");
        record.headers().add("event-type", "UPDATED".getBytes(StandardCharsets.UTF_8));
        record.headers().add("event-id", "evt-1".getBytes(StandardCharsets.UTF_8));
        Exception thrown =
                new ListenerExecutionFailedException("listener failed", new JsonParseException(null, "bad json"));

        boolean recovered =
                errorHandler.handleOne(thrown, record, mock(Consumer.class), mock(MessageListenerContainer.class));

        assertThat(recovered).isTrue();
        verify(kafkaExceptionHandler).handle(same(thrown), same(record));

        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        ProducerRecord<String, String> dlt = captor.getValue();
        assertThat(dlt.topic()).isEqualTo(TOPIC + ".DLT");
        assertThat(dlt.partition()).isZero();
        assertThat(dlt.key()).isEqualTo("10");
        assertThat(dlt.value()).isEqualTo("{\"postId\":10}");
        assertThat(new String(dlt.headers().lastHeader("event-id").value(), StandardCharsets.UTF_8))
                .isEqualTo("evt-1");
        assertThat(new String(dlt.headers().lastHeader("event-type").value(), StandardCharsets.UTF_8))
                .isEqualTo("UPDATED");
    }

    @Test
    @DisplayName("리스너 예외의 원인이 인프라 오류면 DLT로 보내지 않고 재시도한다")
    void retriesInfraErrorWrappedInListenerException() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(TOPIC, 0, 42L, "10", "{\"postId\":10}");
        Exception thrown = new ListenerExecutionFailedException("listener failed", new IOException("es down"));

        boolean recovered =
                errorHandler.handleOne(thrown, record, mock(Consumer.class), mock(MessageListenerContainer.class));

        assertThat(recovered).isFalse();
        verify(kafkaTemplate, never()).send(any(ProducerRecord.class));
    }
}
