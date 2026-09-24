package com.traveler.useractivity.global.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.traveler.useractivity.global.exception.UserActivityServiceException;
import com.traveler.useractivity.global.exception.code.UserActivityServiceErrorCode;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.SpelMessage;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.SendResult;

class KafkaConfigErrorHandlerTest {

    private static final String TOPIC = "user-activity";

    private KafkaTemplate<String, String> kafkaTemplate;
    private CommonErrorHandler errorHandler;
    private Consumer<?, ?> consumer;
    private MessageListenerContainer container;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        ProducerFactory<String, String> producerFactory = mock(ProducerFactory.class);
        given(producerFactory.getConfigurationProperties()).willReturn(Map.of());
        given(kafkaTemplate.getProducerFactory()).willReturn(producerFactory);
        given(kafkaTemplate.send(any(ProducerRecord.class)))
                .willReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        KafkaConfig kafkaConfig =
                new KafkaConfig(new KafkaProperties(), mock(SslBundles.class), new KafkaExceptionHandler());
        errorHandler = kafkaConfig.commonErrorHandler(kafkaTemplate);
        consumer = mock(Consumer.class);
        container = mock(MessageListenerContainer.class);
    }

    @Test
    @DisplayName("필터 규칙 문법 오류(5xx지만 규칙 자체 문제)는 재시도 없이 첫 실패에서 DLT로 보낸다")
    void invalidRuleSyntaxIsNotRetried() {
        UserActivityServiceException cause = new UserActivityServiceException(
                UserActivityServiceErrorCode.INVALID_FILTER_RULE_SYNTAX, parseException());

        boolean recovered = errorHandler.handleOne(listenerFailure(cause), record(), consumer, container);

        assertThat(recovered).isTrue();
        verifyPublishedToDlt();
    }

    @Test
    @DisplayName("4xx 코드 예외는 재시도 없이 첫 실패에서 DLT로 보낸다")
    void clientErrorIsNotRetried() {
        UserActivityServiceException cause = new UserActivityServiceException(
                UserActivityServiceErrorCode.LOG_DATA_MISMATCH,
                new SpelEvaluationException(SpelMessage.TYPE_NOT_FOUND, "x"));

        boolean recovered = errorHandler.handleOne(listenerFailure(cause), record(), consumer, container);

        assertThat(recovered).isTrue();
        verifyPublishedToDlt();
    }

    @Test
    @DisplayName("그 외 5xx 코드 예외는 기존대로 재시도 대상이라 첫 실패에서 DLT로 보내지 않는다")
    void serverErrorIsRetried() {
        UserActivityServiceException cause =
                new UserActivityServiceException(UserActivityServiceErrorCode.TOPIC_NOT_CONFIGURED);

        boolean recovered = errorHandler.handleOne(listenerFailure(cause), record(), consumer, container);

        assertThat(recovered).isFalse();
        verify(kafkaTemplate, never()).send(any(ProducerRecord.class));
    }

    private static ConsumerRecord<String, String> record() {
        return new ConsumerRecord<>(TOPIC, 0, 0L, "key", "value");
    }

    private static ListenerExecutionFailedException listenerFailure(Throwable cause) {
        return new ListenerExecutionFailedException("Listener failed", cause);
    }

    private static Exception parseException() {
        try {
            new SpelExpressionParser().parseExpression("#a >");
        } catch (Exception e) {
            return e;
        }
        throw new IllegalStateException("parse should fail");
    }

    @SuppressWarnings("unchecked")
    private void verifyPublishedToDlt() {
        verify(kafkaTemplate)
                .send(argThat((ProducerRecord<String, String> r) -> r.topic().equals(TOPIC + ".DLT")));
    }
}
