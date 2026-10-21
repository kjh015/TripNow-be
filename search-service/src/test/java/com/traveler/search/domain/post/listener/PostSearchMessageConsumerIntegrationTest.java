package com.traveler.search.domain.post.listener;

import static com.traveler.search.testsupport.fixture.SearchFixture.nextId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.traveler.search.domain.post.document.PostDocument;
import com.traveler.search.domain.post.repository.PostDocumentRepository;
import com.traveler.search.testsupport.IntegrationTestSupport;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * post-service Outbox가 발행하는 형식(키 = postId, event-type·event-id 헤더, PostMessage JSON) 그대로
 * Kafka에 넣었을 때 ES 문서까지 반영되는지 확인한다. 발행 쪽 형식은 post-service의 OutboxKafkaIntegrationTest가 검증한다.
 */
class PostSearchMessageConsumerIntegrationTest extends IntegrationTestSupport {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private PostDocumentRepository postDocumentRepository;

    @Value("${app.kafka.topics.post-events}")
    private String topic;

    @Test
    @DisplayName("Outbox 형식의 CREATED 메시지를 받으면 ES 문서가 생기고, 같은 메시지가 다시 와도 문서는 같다")
    void createdMessageCreatesDocument() throws Exception {
        long postId = nextId();
        String eventId = UUID.randomUUID().toString();
        String payload = createdPayload(postId, "카프카로 온 게시글");

        send(postId, "CREATED", eventId, payload);
        await().atMost(TIMEOUT).until(() -> postDocumentRepository.existsById(postId));

        send(postId, "CREATED", eventId, payload);
        send(postId, "STAT_UPDATED", UUID.randomUUID().toString(), statPayload(postId));

        await().atMost(TIMEOUT).untilAsserted(() -> {
            PostDocument doc = postDocumentRepository.findById(postId).orElseThrow();
            assertThat(doc.getTitle()).isEqualTo("카프카로 온 게시글");
            assertThat(doc.getCategory()).isEqualTo("FOOD");
            assertThat(doc.getLikeCount()).isEqualTo(7L);
        });
    }

    @Test
    @DisplayName("event-type 헤더가 없는 메시지는 건너뛰고, 같은 파티션의 다음 메시지를 정상 처리한다")
    void missingEventTypeDoesNotBlockPartition() throws Exception {
        long postId = nextId();

        send(postId, null, UUID.randomUUID().toString(), createdPayload(postId, "헤더 없음"));
        send(postId, "CREATED", UUID.randomUUID().toString(), createdPayload(postId, "헤더 있음"));

        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(postDocumentRepository.findById(postId))
                .get()
                .extracting(PostDocument::getTitle)
                .isEqualTo("헤더 있음"));
    }

    private void send(long postId, String eventType, String eventId, String payload) throws Exception {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, String.valueOf(postId), payload);
        if (eventType != null) {
            record.headers().add("event-type", eventType.getBytes(StandardCharsets.UTF_8));
        }
        record.headers().add("event-id", eventId.getBytes(StandardCharsets.UTF_8));
        kafkaTemplate.send(record).get();
    }

    // post-service PostMessage.CreatedDTO 직렬화 결과와 같은 형식 (Category·Region은 enum 이름)
    private static String createdPayload(long postId, String title) {
        return """
                {"postId":%d,"memberId":1,"title":"%s","content":"본문","category":"FOOD","region":"GYEONGGI",\
                "travelPlace":"황리단길","address":"경상북도 경주시","createdAt":"2026-10-01T00:00:00Z","images":[]}"""
                .formatted(postId, title);
    }

    private static String statPayload(long postId) {
        return """
                {"postId":%d,"starAvg":4.0,"commentCount":1,"likeCount":7,"viewCount":20}"""
                .formatted(postId);
    }
}
