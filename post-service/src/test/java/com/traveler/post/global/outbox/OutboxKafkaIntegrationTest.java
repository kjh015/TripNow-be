package com.traveler.post.global.outbox;

import static com.traveler.post.testsupport.KafkaTestSupport.header;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.traveler.post.domain.post.dto.request.PostRequest;
import com.traveler.post.domain.post.enums.Category;
import com.traveler.post.domain.post.enums.Region;
import com.traveler.post.domain.post.service.PostService;
import com.traveler.post.global.kafka.KafkaProducer;
import com.traveler.post.global.outbox.entity.Outbox;
import com.traveler.post.global.outbox.enums.OutboxStatus;
import com.traveler.post.global.outbox.repository.OutboxRepository;
import com.traveler.post.testsupport.IntegrationTestSupport;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

/**
 * 게시글 작성 → Outbox 저장 → 커밋 후 Kafka 발행까지 확인한다.
 * search-service가 기대하는 형식(키 = postId, event-type·event-id 헤더, CreatedDTO 필드)으로 나가는지 함께 본다.
 * 소비 쪽 반영은 search-service의 PostSearchMessageConsumerIntegrationTest가 같은 형식으로 검증한다.
 */
class OutboxKafkaIntegrationTest extends IntegrationTestSupport {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    private PostService postService;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ObservationRegistry observationRegistry;

    @Autowired
    private Tracer tracer;

    @Value("${app.kafka.topics.post-events}")
    private String topic;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Test
    @DisplayName("게시글을 작성하면 Outbox가 SENT가 되고, postId 키와 event-id 헤더를 단 CREATED 메시지가 발행된다")
    void createPostPublishesCreatedEvent() throws Exception {
        long memberId = 7L;
        Long postId = postService
                .createPost(
                        memberId,
                        new PostRequest.CreateDTO(
                                "아웃박스 게시글", "본문", "황리단길", "경상북도 경주시", Category.FOOD, Region.GYEONGGI, List.of()))
                .postId();

        Outbox outbox = await().atMost(TIMEOUT)
                .until(() -> findOutbox(postId), o -> o != null && o.getStatus() == OutboxStatus.SENT);

        ConsumerRecord<String, String> record = pollRecordWithKey(String.valueOf(postId));

        assertThat(header(record, KafkaProducer.EVENT_TYPE_HEADER)).isEqualTo("CREATED");
        assertThat(header(record, KafkaProducer.EVENT_ID_HEADER)).isEqualTo(outbox.getEventId());

        // search-service PostSearchMessage.CreatedDTO가 읽는 필드
        JsonNode payload = objectMapper.readTree(record.value());
        assertThat(payload.get("postId").asLong()).isEqualTo(postId);
        assertThat(payload.get("memberId").asLong()).isEqualTo(memberId);
        assertThat(payload.get("title").asText()).isEqualTo("아웃박스 게시글");
        assertThat(payload.get("category").asText()).isEqualTo("FOOD");
        assertThat(payload.get("region").asText()).isEqualTo("GYEONGGI");
        assertThat(payload.get("travelPlace").asText()).isEqualTo("황리단길");
        assertThat(payload.get("address").asText()).isEqualTo("경상북도 경주시");
        assertThat(payload.get("createdAt").isTextual()).isTrue();
        assertThat(payload.get("images").isArray()).isTrue();
    }

    @Test
    @DisplayName("게시글 작성 요청의 trace가 커밋 후 비동기 발행(outboxTaskExecutor)까지 이어져 같은 traceId의 traceparent 헤더가 실린다")
    void publishedEventCarriesRequestTraceId() {
        AtomicReference<String> traceId = new AtomicReference<>();
        AtomicReference<Long> postId = new AtomicReference<>();

        // 서블릿 요청 observation이 열려 있는 상황. 커밋 후 리스너(@Async)는 이 안에서 작업을 넘긴다
        Observation.createNotStarted("http.server.requests", observationRegistry)
                .observe(() -> {
                    traceId.set(tracer.currentSpan().context().traceId());
                    postId.set(postService
                            .createPost(
                                    7L,
                                    new PostRequest.CreateDTO(
                                            "추적 게시글",
                                            "본문",
                                            "황리단길",
                                            "경상북도 경주시",
                                            Category.FOOD,
                                            Region.GYEONGGI,
                                            List.of()))
                            .postId());
                });

        ConsumerRecord<String, String> record = pollRecordWithKey(String.valueOf(postId.get()));

        assertThat(header(record, "traceparent")).matches("00-" + traceId.get() + "-[0-9a-f]{16}-0[01]");
    }

    private Outbox findOutbox(Long postId) {
        return outboxRepository.findAll().stream()
                // 같은 DB의 좋아요·댓글 Outbox와 aggregateId가 겹칠 수 있어 토픽까지 본다
                .filter(o -> topic.equals(o.getTopic())
                        && Objects.equals(o.getAggregateId(), postId)
                        && "CREATED".equals(o.getEventType()))
                .findFirst()
                .orElse(null);
    }

    private ConsumerRecord<String, String> pollRecordWithKey(String key) {
        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG,
                "outbox-it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest");
        try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(
                        props, new StringDeserializer(), new StringDeserializer())
                .createConsumer()) {
            consumer.subscribe(List.of(topic));
            List<ConsumerRecord<String, String>> received = new ArrayList<>();
            await().atMost(TIMEOUT).until(() -> {
                consumer.poll(Duration.ofMillis(500)).forEach(received::add);
                return received.stream().anyMatch(r -> key.equals(r.key()));
            });
            return received.stream()
                    .filter(r -> key.equals(r.key()))
                    .findFirst()
                    .orElseThrow();
        }
    }
}
