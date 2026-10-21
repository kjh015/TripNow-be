package com.traveler.post.testsupport;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

/** Kafka 메시지를 다루는 테스트의 공통 도우미. */
public final class KafkaTestSupport {

    private KafkaTestSupport() {}

    /** 보낸 메시지의 헤더 값을 문자열로 꺼낸다. 헤더가 없으면 null. */
    public static String header(ProducerRecord<?, ?> record, String name) {
        return header(record.headers(), name);
    }

    /** 받은 메시지의 헤더 값을 문자열로 꺼낸다. 헤더가 없으면 null. */
    public static String header(ConsumerRecord<?, ?> record, String name) {
        return header(record.headers(), name);
    }

    /**
     * 가짜 KafkaTemplate의 send를 "곧바로 성공"으로 흉내 낸다.
     * 진짜 send는 비동기로 CompletableFuture를 돌려주므로, 이미 끝난 Future를 돌려준다.
     */
    @SuppressWarnings("unchecked")
    public static void stubSuccessfulSend(KafkaTemplate<String, String> kafkaTemplate) {
        given(kafkaTemplate.send(any(ProducerRecord.class))).willAnswer(invocation -> {
            ProducerRecord<String, String> sent = invocation.getArgument(0);
            RecordMetadata metadata = new RecordMetadata(new TopicPartition(sent.topic(), 0), 0, 0, 0, 0, 0);
            return CompletableFuture.completedFuture(new SendResult<>(sent, metadata));
        });
    }

    private static String header(Headers headers, String name) {
        Header header = headers.lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
