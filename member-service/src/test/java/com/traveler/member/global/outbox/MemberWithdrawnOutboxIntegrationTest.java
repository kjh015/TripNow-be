package com.traveler.member.global.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.traveler.member.domain.auth.support.JwtTokenProvider;
import com.traveler.member.domain.member.entity.Member;
import com.traveler.member.domain.member.enums.RoleType;
import com.traveler.member.domain.member.repository.MemberRepository;
import com.traveler.member.domain.member.service.command.AdminMemberCommandService;
import com.traveler.member.domain.member.service.command.MemberCommandService;
import com.traveler.member.global.outbox.entity.Outbox;
import com.traveler.member.global.outbox.enums.OutboxStatus;
import com.traveler.member.global.outbox.repository.OutboxRepository;
import com.traveler.member.testsupport.IntegrationTestSupport;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

/**
 * 회원 탈퇴 → Outbox 저장 → 커밋 후 Kafka 발행까지 확인한다.
 * post-service MemberEventConsumer가 기대하는 형식(키 = memberId, event-type·event-id 헤더, WithdrawnDTO 필드)으로 나가는지 함께 본다.
 */
class MemberWithdrawnOutboxIntegrationTest extends IntegrationTestSupport {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    private MemberCommandService memberCommandService;

    @Autowired
    private AdminMemberCommandService adminMemberCommandService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${app.kafka.topics.member-events}")
    private String topic;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Test
    @DisplayName("본인 탈퇴하면 memberId 키와 event-id 헤더를 단 WITHDRAWN(USER) 메시지가 발행되고 Outbox가 SENT가 된다")
    void withdrawPublishesWithdrawnEvent() throws Exception {
        Long memberId = saveMember("withdraw-user");

        memberCommandService.withdraw(
                memberId, jwtTokenProvider.createAccessToken(memberId, List.of(RoleType.ROLE_USER)));

        Outbox outbox = await().atMost(TIMEOUT)
                .until(() -> findOutbox(memberId), o -> o != null && o.getStatus() == OutboxStatus.SENT);
        assertThat(outbox.getTopic()).isEqualTo(topic);
        assertThat(outbox.getAggregateType()).isEqualTo("MEMBER");
        assertThat(outbox.getEventType()).isEqualTo("WITHDRAWN");

        ConsumerRecord<String, String> record = pollRecordWithKey(String.valueOf(memberId));
        assertThat(header(record, "event-type")).isEqualTo("WITHDRAWN");
        assertThat(header(record, "event-id")).isEqualTo(outbox.getEventId());

        JsonNode payload = objectMapper.readTree(record.value());
        assertThat(payload.get("memberId").asLong()).isEqualTo(memberId);
        assertThat(payload.get("reason").asText()).isEqualTo("USER");
        assertThat(payload.get("withdrawnAt").isTextual()).isTrue();
    }

    @Test
    @DisplayName("관리자가 강제 탈퇴하면 reason이 ADMIN인 Outbox 행이 생긴다")
    void adminDeletePublishesWithdrawnEvent() throws Exception {
        Long memberId = saveMember("withdraw-admin");

        adminMemberCommandService.deleteMember(memberId);

        Outbox outbox = await().atMost(TIMEOUT).until(() -> findOutbox(memberId), Objects::nonNull);
        assertThat(outbox.getTopic()).isEqualTo(topic);
        assertThat(objectMapper.readTree(outbox.getPayload()).get("reason").asText())
                .isEqualTo("ADMIN");
    }

    private Long saveMember(String loginId) {
        return memberRepository
                .save(Member.builder()
                        .loginId(loginId + "-" + UUID.randomUUID().toString().substring(0, 8))
                        .nickname("탈퇴자")
                        .build())
                .getId();
    }

    private Outbox findOutbox(Long memberId) {
        return outboxRepository.findAll().stream()
                .filter(o -> Objects.equals(o.getAggregateId(), memberId))
                .findFirst()
                .orElse(null);
    }

    private ConsumerRecord<String, String> pollRecordWithKey(String key) {
        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG,
                "member-outbox-it-" + UUID.randomUUID(),
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

    private static String header(ConsumerRecord<?, ?> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
