package com.traveler.member.global.kafka;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.MicrometerConsumerListener;
import org.springframework.kafka.core.MicrometerProducerListener;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@EnableKafka
@RequiredArgsConstructor
public class KafkaConfig {

    private final KafkaProperties kafkaProperties;
    private final SslBundles sslBundles;
    private final KafkaExceptionHandler kafkaExceptionHandler;
    private final MeterRegistry meterRegistry;

    // Producer
    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> nodes = kafkaProperties.buildProducerProperties(sslBundles);
        DefaultKafkaProducerFactory<String, String> factory = new DefaultKafkaProducerFactory<>(nodes);
        // 프로듀서 클라이언트 지표(kafka_producer_*)를 등록한다. 직접 생성한 빈에는 Boot의 지표 자동 설정이 적용되지 않는다.
        factory.addListener(new MicrometerProducerListener<>(meterRegistry));
        return factory;
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        KafkaTemplate<String, String> template = new KafkaTemplate<>(producerFactory());
        // 발행 시 현재 trace 정보를 traceparent 헤더에 포함한다. 직접 생성한 빈에는 spring.kafka.template.observation-enabled 설정이 적용되지 않는다.
        template.setObservationEnabled(true);
        return template;
    }

    // Consumer
    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> nodes = kafkaProperties.buildConsumerProperties(sslBundles);
        DefaultKafkaConsumerFactory<String, Object> factory = new DefaultKafkaConsumerFactory<>(nodes);
        // 컨슈머 클라이언트 지표(kafka_consumer_*, 파티션별 lag 포함)를 등록한다. 직접 생성한 빈에는 Boot의 지표 자동 설정이 적용되지 않는다.
        factory.addListener(new MicrometerConsumerListener<>(meterRegistry));
        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        // 수신 메시지의 traceparent 헤더를 통해 발행 측 trace를 이어 간다. spring.kafka.listener.observation-enabled 대신 코드로 활성화한다.
        factory.getContainerProperties().setObservationEnabled(true);
        factory.setCommonErrorHandler(commonErrorHandler(kafkaTemplate()));
        Integer concurrency = kafkaProperties.getListener().getConcurrency();
        if (concurrency != null) {
            factory.setConcurrency(concurrency);
        }
        ContainerProperties.AckMode ackMode = kafkaProperties.getListener().getAckMode();
        if (ackMode != null) {
            factory.getContainerProperties().setAckMode(ackMode);
        }
        return factory;
    }

    @Bean
    public CommonErrorHandler commonErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        DeadLetterPublishingRecoverer dltRecoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate, (record, ex) -> new TopicPartition(record.topic() + ".DLT", record.partition()));

        // Recoverer 합성: 중앙 로깅(KafkaExceptionHandler) 후 DLT 격리
        ConsumerRecordRecoverer recoverer = (record, exception) -> {
            kafkaExceptionHandler.handle((Exception) exception, (ConsumerRecord<?, ?>) record);
            dltRecoverer.accept(record, exception);
        };

        // FixedBackOff: (대기시간 2s, 최대재시도 3회)
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(2000L, 3L));
        handler.addNotRetryableExceptions(com.fasterxml.jackson.core.JsonProcessingException.class);
        handler.setRetryListeners(kafkaExceptionHandler::logRetry);
        return handler;
    }
}
