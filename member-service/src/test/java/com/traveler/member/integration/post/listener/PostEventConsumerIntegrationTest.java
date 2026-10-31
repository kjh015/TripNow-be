package com.traveler.member.integration.post.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.traveler.common.core.response.PageResponse;
import com.traveler.member.domain.member.dto.response.AdminMemberResponse;
import com.traveler.member.domain.member.entity.Member;
import com.traveler.member.domain.member.entity.MemberWithdrawal;
import com.traveler.member.domain.member.enums.ContentDeletionStatus;
import com.traveler.member.domain.member.enums.WithdrawalReason;
import com.traveler.member.domain.member.repository.MemberRepository;
import com.traveler.member.domain.member.repository.MemberWithdrawalRepository;
import com.traveler.member.domain.member.service.command.AdminMemberCommandService;
import com.traveler.member.domain.member.service.query.AdminMemberQueryService;
import com.traveler.member.testsupport.IntegrationTestSupport;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * 회원 탈퇴 Saga의 마지막 단계: post-service의 CONTENT_DELETED 수신 → 회원의 정리 상태 PENDING → DONE.
 *
 * <p>메시지 처리 완료는 뒤에 보낸 다른 회원의 완료 메시지가 처리된 것으로 판단한다. 테스트 Kafka의 자동 생성 토픽은 파티션이 하나라
 * 보낸 순서대로 처리된다.
 */
class PostEventConsumerIntegrationTest extends IntegrationTestSupport {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private AdminMemberCommandService adminMemberCommandService;

    @Autowired
    private AdminMemberQueryService adminMemberQueryService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private MemberWithdrawalRepository memberWithdrawalRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${app.kafka.topics.content-events}")
    private String topic;

    @Test
    @DisplayName("정리 완료 메시지를 받으면 탈퇴 회원의 정리 상태가 DONE이 되고, 같은 회원의 완료 메시지를 다시 받아도 완료 일시가 바뀌지 않는다")
    void contentDeletedMarksDoneIdempotently() throws Exception {
        Long memberId = withdrawnMember("content-done");
        assertThat(deletionStatus(memberId)).isEqualTo(ContentDeletionStatus.PENDING);
        assertThat(withdrawal(memberId).getReason()).isEqualTo(WithdrawalReason.ADMIN);

        Instant deletedAt = Instant.parse("2026-10-29T01:00:00Z");
        sendContentDeleted(memberId, deletedAt);
        awaitProcessed();

        MemberWithdrawal withdrawal = withdrawal(memberId);
        assertThat(withdrawal.getContentDeletionStatus()).isEqualTo(ContentDeletionStatus.DONE);
        assertThat(withdrawal.getContentDeletedAt()).isEqualTo(deletedAt);

        // post-service가 탈퇴 메시지를 다시 처리하면 완료 메시지가 한 번 더 온다
        sendContentDeleted(memberId, deletedAt.plusSeconds(60));
        awaitProcessed();

        assertThat(withdrawal(memberId).getContentDeletedAt()).isEqualTo(deletedAt);
    }

    @Test
    @DisplayName("정리 미완료 조회는 탈퇴 후 30분이 지난 PENDING 회원만 돌려준다")
    void pendingWithdrawalQueryReturnsOnlyStalePending() throws Exception {
        Long stale = withdrawnMember("content-pending");
        Long recent = withdrawnMember("content-recent");
        Long done = withdrawnMember("content-finished");
        sendContentDeleted(done, Instant.now());
        awaitProcessed();
        withdrawnBefore(stale, 31);
        withdrawnBefore(done, 31);

        PageResponse<AdminMemberResponse.PendingWithdrawalDTO> pending =
                adminMemberQueryService.getPendingWithdrawals(PageRequest.of(0, 1000));

        assertThat(pending.content())
                .extracting(AdminMemberResponse.PendingWithdrawalDTO::memberId)
                .contains(stale)
                .doesNotContain(recent, done);
    }

    // 저장된 값에서 뺀다. Hibernate는 Instant를 UTC로 저장해 JVM 시간대의 Timestamp를 넣으면 시각이 어긋난다
    private void withdrawnBefore(Long memberId, int minutes) {
        jdbcTemplate.update(
                "UPDATE member_withdrawal SET created_at = DATE_SUB(created_at, INTERVAL ? MINUTE) WHERE member_id = ?",
                minutes,
                memberId);
    }

    // 처리 확인용 회원의 완료 메시지를 뒤에 보내고, 그 회원이 DONE이 될 때까지 기다린다
    private void awaitProcessed() throws Exception {
        Long marker = withdrawnMember("content-marker");
        sendContentDeleted(marker, Instant.now());
        await().atMost(TIMEOUT).until(() -> deletionStatus(marker) == ContentDeletionStatus.DONE);
    }

    private Long withdrawnMember(String loginId) {
        Long memberId = memberRepository
                .save(Member.builder()
                        .loginId(loginId + "-" + UUID.randomUUID().toString().substring(0, 8))
                        .nickname("탈퇴자")
                        .build())
                .getId();
        adminMemberCommandService.deleteMember(memberId);
        return memberId;
    }

    private ContentDeletionStatus deletionStatus(Long memberId) {
        return withdrawal(memberId).getContentDeletionStatus();
    }

    private MemberWithdrawal withdrawal(Long memberId) {
        return memberWithdrawalRepository.findByMemberId(memberId).orElseThrow();
    }

    private void sendContentDeleted(Long memberId, Instant deletedAt) throws Exception {
        String payload = "{\"memberId\":" + memberId + ",\"deletedAt\":\"" + deletedAt
                + "\",\"posts\":1,\"comments\":2,\"likes\":3}";
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, String.valueOf(memberId), payload);
        record.headers().add("event-type", "CONTENT_DELETED".getBytes(StandardCharsets.UTF_8));
        record.headers().add("event-id", UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
        kafkaTemplate.send(record).get(10, TimeUnit.SECONDS);
    }
}
