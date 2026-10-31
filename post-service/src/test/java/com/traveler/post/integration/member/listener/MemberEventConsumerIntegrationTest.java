package com.traveler.post.integration.member.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.traveler.post.domain.comment.dto.request.CommentRequest;
import com.traveler.post.domain.comment.service.CommentService;
import com.traveler.post.domain.like.dto.request.LikeRequest;
import com.traveler.post.domain.like.service.LikeService;
import com.traveler.post.domain.post.dto.result.PostStatsSummary;
import com.traveler.post.domain.post.entity.Post;
import com.traveler.post.domain.post.entity.PostStats;
import com.traveler.post.domain.post.repository.PostRepository;
import com.traveler.post.domain.post.repository.PostStatsRepository;
import com.traveler.post.domain.post.support.PostStatsSlots;
import com.traveler.post.testsupport.IntegrationTestSupport;
import com.traveler.post.testsupport.fixture.PostFixture;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * WITHDRAWN 수신 → 탈퇴 회원의 게시글·댓글 소프트 삭제, 좋아요 삭제, 다른 회원 게시글의 통계 보정을 확인한다.
 * 같은 메시지를 두 번 받아도 결과(행 상태·통계·발행 이벤트 수)가 같아야 한다.
 *
 * <p>메시지 처리 완료는 뒤에 보낸 다른 회원의 탈퇴 메시지가 처리된 것으로 판단한다. 테스트 Kafka의 자동 생성 토픽은 파티션이 하나라
 * 보낸 순서대로 처리된다.
 */
class MemberEventConsumerIntegrationTest extends IntegrationTestSupport {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    // 다른 테스트 클래스와 같은 DB를 쓰므로 겹치지 않는 회원 ID를 쓴다
    private static final AtomicLong MEMBER_IDS = new AtomicLong(900_000L);

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private LikeService likeService;

    @Autowired
    private CommentService commentService;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private PostStatsRepository postStatsRepository;

    @Autowired
    private PostStatsSlots postStatsSlots;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${app.kafka.topics.member-events}")
    private String topic;

    @Test
    @DisplayName("탈퇴 메시지를 받으면 회원의 게시글·댓글을 소프트 삭제하고 좋아요를 지우며, 다른 게시글의 통계를 실제 행에 맞춘다. 두 번 받아도 같다")
    void withdrawnDeletesMemberContentIdempotently() throws Exception {
        long withdrawn = MEMBER_IDS.incrementAndGet();
        long other = MEMBER_IDS.incrementAndGet();

        // 탈퇴 회원의 게시글(다른 회원의 좋아요·댓글이 달림)
        Long ownPost = savePost(withdrawn);
        likeService.addLike(new LikeRequest.AddDTO(ownPost), other);
        commentService.createComment(other, new CommentRequest.CreateDTO(ownPost, "남의 댓글", 3));

        // 다른 회원의 게시글에 남긴 탈퇴 회원의 좋아요·댓글
        Long otherPost = savePost(other);
        likeService.addLike(new LikeRequest.AddDTO(otherPost), withdrawn);
        likeService.addLike(new LikeRequest.AddDTO(otherPost), other);
        List<Long> withdrawnComments = new ArrayList<>();
        withdrawnComments.add(commentService
                .createComment(withdrawn, new CommentRequest.CreateDTO(otherPost, "탈퇴자 댓글 1", 5))
                .commentId());
        withdrawnComments.add(commentService
                .createComment(withdrawn, new CommentRequest.CreateDTO(otherPost, "탈퇴자 댓글 2", 1))
                .commentId());
        commentService.createComment(other, new CommentRequest.CreateDTO(otherPost, "남는 댓글", 4));
        long lastOutboxId = lastOutboxId();

        String eventId = UUID.randomUUID().toString();
        sendWithdrawn(withdrawn, eventId);
        awaitProcessed();

        assertContentDeleted(withdrawn, ownPost, otherPost, withdrawnComments, lastOutboxId);
        // 정리를 마치면 member-service에 정리 완료를 알린다
        assertThat(contentDeletedCount(lastOutboxId, withdrawn)).isEqualTo(1);

        // 같은 메시지(같은 event-id)를 한 번 더 받는다
        sendWithdrawn(withdrawn, eventId);
        awaitProcessed();

        assertContentDeleted(withdrawn, ownPost, otherPost, withdrawnComments, lastOutboxId);
        // 완료 이벤트는 다시 나가지만 member-service는 PENDING일 때만 바꾸므로 결과가 같다
        assertThat(contentDeletedCount(lastOutboxId, withdrawn)).isEqualTo(2);
    }

    @Test
    @DisplayName("배치 크기보다 많은 댓글도 모두 지우고 통계를 맞춘다")
    void withdrawnDeletesContentAcrossBatches() throws Exception {
        long withdrawn = MEMBER_IDS.incrementAndGet();
        long other = MEMBER_IDS.incrementAndGet();
        Long otherPost = savePost(other);
        int count = 105; // MemberWithdrawalFacade.BATCH_SIZE(100)보다 많게
        for (int i = 0; i < count; i++) {
            commentService.createComment(withdrawn, new CommentRequest.CreateDTO(otherPost, "댓글 " + i, 2));
        }
        commentService.createComment(other, new CommentRequest.CreateDTO(otherPost, "남는 댓글", 5));

        sendWithdrawn(withdrawn, UUID.randomUUID().toString());
        awaitProcessed();

        assertThat(activeComments(withdrawn)).isZero();
        PostStatsSummary stats = postStatsSlots.summarize(otherPost);
        assertThat(stats.commentCount()).isEqualTo(1);
        assertThat(stats.starSum()).isEqualTo(5);
    }

    private void assertContentDeleted(
            long withdrawn, Long ownPost, Long otherPost, List<Long> withdrawnComments, long lastOutboxId) {
        // 게시글: 탈퇴 회원의 글만 소프트 삭제, 검색 삭제 이벤트는 한 번만
        assertThat(isDeleted("post", ownPost)).isTrue();
        assertThat(isDeleted("post", otherPost)).isFalse();
        assertThat(outboxCount(lastOutboxId, "post-service.post.events", ownPost, "DELETED"))
                .isEqualTo(1);

        // 댓글: 탈퇴 회원의 댓글만 소프트 삭제, 삭제 이벤트는 게시글마다 하나로 묶어 한 번만
        assertThat(activeComments(withdrawn)).isZero();
        for (Long commentId : withdrawnComments) {
            assertThat(isDeleted("comment", commentId)).isTrue();
        }
        assertThat(outboxCount(lastOutboxId, "post-service.comment.events", otherPost, "BATCH_DELETED"))
                .isEqualTo(1);
        assertThat(outboxPayload(lastOutboxId, "post-service.comment.events", otherPost))
                .contains(withdrawnComments.stream().map(String::valueOf).toList());

        // 좋아요: 탈퇴 회원의 좋아요만 삭제, 취소 이벤트는 게시글마다 한 번만
        assertThat(outboxCount(lastOutboxId, "post-service.like.events", otherPost, "BATCH_REMOVED"))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM likes WHERE member_id = ?", Long.class, withdrawn))
                .isZero();

        // 다른 회원 게시글의 통계는 남은 행과 같다: 좋아요 1(other), 댓글 1(별점 4)
        PostStatsSummary otherStats = postStatsSlots.summarize(otherPost);
        assertThat(otherStats.likeCount()).isEqualTo(1);
        assertThat(otherStats.commentCount()).isEqualTo(1);
        assertThat(otherStats.starSum()).isEqualTo(4);

        // 삭제된 게시글의 다른 회원 좋아요·댓글은 건드리지 않는다
        PostStatsSummary ownStats = postStatsSlots.summarize(ownPost);
        assertThat(ownStats.likeCount()).isEqualTo(1);
        assertThat(ownStats.commentCount()).isEqualTo(1);
    }

    // 처리 확인용 회원의 탈퇴 메시지를 뒤에 보내고, 그 회원의 게시글이 삭제될 때까지 기다린다
    private void awaitProcessed() throws Exception {
        long marker = MEMBER_IDS.incrementAndGet();
        Long markerPost = savePost(marker);
        sendWithdrawn(marker, UUID.randomUUID().toString());
        await().atMost(TIMEOUT).until(() -> isDeleted("post", markerPost));
    }

    private void sendWithdrawn(long memberId, String eventId) throws Exception {
        String payload = "{\"memberId\":" + memberId + ",\"withdrawnAt\":\"2026-10-29T00:00:00Z\",\"reason\":\"USER\"}";
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, String.valueOf(memberId), payload);
        record.headers().add("event-type", "WITHDRAWN".getBytes(StandardCharsets.UTF_8));
        record.headers().add("event-id", eventId.getBytes(StandardCharsets.UTF_8));
        kafkaTemplate.send(record).get(10, TimeUnit.SECONDS);
    }

    // 게시글 작성(PostService.createPost)처럼 게시글과 통계 행을 한 트랜잭션에서 만든다
    private Long savePost(long memberId) {
        return transactionTemplate.execute(status -> {
            Post post = postRepository.save(PostFixture.post(memberId));
            postStatsRepository.saveAll(PostStats.createSlots(post));
            return post.getId();
        });
    }

    private boolean isDeleted(String table, Long id) {
        return Boolean.TRUE.equals(
                jdbcTemplate.queryForObject("SELECT is_deleted FROM " + table + " WHERE id = ?", Boolean.class, id));
    }

    private long activeComments(long memberId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM comment WHERE member_id = ? AND is_deleted = false", Long.class, memberId);
    }

    private long lastOutboxId() {
        return jdbcTemplate.queryForObject("SELECT COALESCE(MAX(id), 0) FROM outbox", Long.class);
    }

    private long contentDeletedCount(long afterId, long memberId) {
        return outboxCount(afterId, "post-service.content.events", memberId, "CONTENT_DELETED");
    }

    private String outboxPayload(long afterId, String topic, Long aggregateId) {
        return jdbcTemplate.queryForObject(
                "SELECT payload FROM outbox WHERE id > ? AND topic = ? AND aggregate_id = ?",
                String.class,
                afterId,
                topic,
                aggregateId);
    }

    private long outboxCount(long afterId, String topic, Long aggregateId, String eventType) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox WHERE id > ? AND topic = ? AND aggregate_id = ? AND event_type = ?",
                Long.class,
                afterId,
                topic,
                aggregateId,
                eventType);
    }
}
