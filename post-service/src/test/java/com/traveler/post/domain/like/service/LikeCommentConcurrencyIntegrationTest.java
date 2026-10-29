package com.traveler.post.domain.like.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.traveler.post.domain.comment.dto.request.CommentRequest;
import com.traveler.post.domain.comment.dto.response.CommentResponse;
import com.traveler.post.domain.comment.service.CommentService;
import com.traveler.post.domain.like.dto.request.LikeRequest;
import com.traveler.post.domain.post.dto.result.PostStatsSummary;
import com.traveler.post.domain.post.dto.result.PostStatsSyncBatch;
import com.traveler.post.domain.post.entity.Post;
import com.traveler.post.domain.post.entity.PostStats;
import com.traveler.post.domain.post.repository.PostRepository;
import com.traveler.post.domain.post.repository.PostStatsRepository;
import com.traveler.post.domain.post.service.AdminPostService;
import com.traveler.post.domain.post.service.PostService;
import com.traveler.post.domain.post.support.PostStatsSlots;
import com.traveler.post.global.exception.PostServiceException;
import com.traveler.post.global.exception.code.PostServiceErrorCode;
import com.traveler.post.testsupport.IntegrationTestSupport;
import com.traveler.post.testsupport.fixture.PostFixture;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 같은 게시글에 동시에 들어온 좋아요·댓글 요청에서 통계(post_stats)와 실제 행 수가 맞는지 확인한다.
 *
 * <p>게시글 행은 잠그지 않는다. likes·comment INSERT의 외래 키 검사가 post 행에 거는 공유 잠금과, 통계 갱신이
 * post_stats 슬롯 행에 거는 배타 잠금이 서로 다른 행이라 교착 없이 처리되어야 한다. 통계는 슬롯 합계로 확인한다. H2는 락 동작이 달라 MySQL 컨테이너로 돌린다.
 */
class LikeCommentConcurrencyIntegrationTest extends IntegrationTestSupport {

    private static final int THREADS = 20;

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
    private PostService postService;

    @Autowired
    private AdminPostService adminPostService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("서로 다른 회원 N명이 동시에 좋아요하면 likeCount와 like 행 수가 모두 N이다")
    void concurrentLikesFromDifferentMembers() throws Exception {
        Long postId = savePost();

        List<Throwable> errors =
                runConcurrently(i -> () -> likeService.addLike(new LikeRequest.AddDTO(postId), 1000L + i));

        assertThat(errors).isEmpty();
        assertThat(likeCount(postId)).isEqualTo(THREADS);
        assertThat(likeRows(postId)).isEqualTo(THREADS);
    }

    @Test
    @DisplayName("같은 회원이 동시에 N번 좋아요해도 like 행은 하나이고 likeCount는 1이다")
    void concurrentLikesFromSameMember() throws Exception {
        Long postId = savePost();

        List<Throwable> errors = runConcurrently(i -> () -> likeService.addLike(new LikeRequest.AddDTO(postId), 42L));

        assertThat(errors).isEmpty();
        assertThat(likeCount(postId)).isEqualTo(1);
        assertThat(likeRows(postId)).isEqualTo(1);
    }

    @Test
    @DisplayName("동시에 N개 댓글을 달면 commentCount와 별점 합계가 실제 댓글과 일치한다")
    void concurrentComments() throws Exception {
        Long postId = savePost();

        // 별점 1~5를 번갈아 사용
        List<Throwable> errors = runConcurrently(i -> () ->
                commentService.createComment(2000L + i, new CommentRequest.CreateDTO(postId, "댓글 " + i, i % 5 + 1)));

        assertThat(errors).isEmpty();
        PostStatsSummary stats = postStatsSlots.summarize(postId);
        long expectedStarSum = IntStream.range(0, THREADS).map(i -> i % 5 + 1).sum();
        assertThat(stats.commentCount()).isEqualTo(THREADS);
        assertThat(stats.starSum()).isEqualTo(expectedStarSum);
        assertThat(stats.starAvg()).isEqualTo((double) expectedStarSum / THREADS);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM comment WHERE post_id = ? AND is_deleted = false", Long.class, postId))
                .isEqualTo(THREADS);
    }

    @Test
    @DisplayName("좋아요 추가·취소를 서로 다른 회원이 동시에 하면 likeCount와 like 행 수가 같다")
    void concurrentAddAndRemove() throws Exception {
        Long postId = savePost();
        // 짝수 번 회원은 미리 좋아요해 두고 동시에 취소, 홀수 번 회원은 동시에 추가
        for (int i = 0; i < THREADS; i += 2) {
            likeService.addLike(new LikeRequest.AddDTO(postId), 3000L + i);
        }

        List<Throwable> errors = runConcurrently(i -> () -> {
            if (i % 2 == 0) {
                likeService.removeLike(postId, 3000L + i);
            } else {
                likeService.addLike(new LikeRequest.AddDTO(postId), 3000L + i);
            }
        });

        assertThat(errors).isEmpty();
        assertThat(likeCount(postId)).isEqualTo(THREADS / 2);
        assertThat(likeRows(postId)).isEqualTo(THREADS / 2);
    }

    @Test
    @DisplayName("좋아요 추가·취소 응답에 처리 직후의 좋아요 수(슬롯 합계)가 실린다")
    void likeResponsesCarryLikeCount() {
        Long postId = savePost();

        assertThat(likeService.addLike(new LikeRequest.AddDTO(postId), 1L).likeCount())
                .isEqualTo(1);
        assertThat(likeService.addLike(new LikeRequest.AddDTO(postId), 2L).likeCount())
                .isEqualTo(2);
        assertThat(likeService.addLike(new LikeRequest.AddDTO(postId), 2L).likeCount())
                .isEqualTo(2);
        assertThat(likeService.removeLike(postId, 1L).likeCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("댓글 작성·수정·삭제 응답에 처리 직후의 댓글 수·평균 별점이 실리고, 통계에 반영된다")
    void commentResponsesCarryStats() {
        Long postId = savePost();
        CommentResponse.CreateDTO created =
                commentService.createComment(10L, new CommentRequest.CreateDTO(postId, "첫 댓글", 5));
        assertThat(created.commentCount()).isEqualTo(1);
        assertThat(created.starAvg()).isEqualTo(5.0);
        commentService.createComment(11L, new CommentRequest.CreateDTO(postId, "둘째 댓글", 2));

        CommentResponse.UpdateDTO updated =
                commentService.updateComment(created.commentId(), 10L, new CommentRequest.UpdateDTO("고친 댓글", 4));
        assertThat(updated.commentCount()).isEqualTo(2);
        assertThat(updated.starAvg()).isEqualTo(3.0);

        CommentResponse.DeleteDTO deleted = commentService.deleteComment(created.commentId(), 10L);
        assertThat(deleted.commentCount()).isEqualTo(1);
        assertThat(deleted.starAvg()).isEqualTo(2.0);

        PostStatsSummary stats = postStatsSlots.summarize(postId);
        assertThat(stats.commentCount()).isEqualTo(1);
        assertThat(stats.starSum()).isEqualTo(2);
    }

    @Test
    @DisplayName("통계 동기화는 조회수를 더하고, 슬롯 합계와 발행 시각(syncedAt)을 STAT_UPDATED로 발행한다")
    void syncStatsPublishesSummary() {
        Long postId = savePost();
        likeService.addLike(new LikeRequest.AddDTO(postId), 1L);
        commentService.createComment(10L, new CommentRequest.CreateDTO(postId, "댓글", 4));
        long before = System.currentTimeMillis();

        postService.syncStats(new PostStatsSyncBatch(Map.of(postId, 3L), Set.of(postId)));

        String payload = lastStatPayload(postId);
        assertThat(payload).contains("\"likeCount\":1", "\"commentCount\":1", "\"starAvg\":4.0", "\"viewCount\":3");
        long syncedAt = Long.parseLong(payload.replaceAll(".*\"syncedAt\":(\\d+).*", "$1"));
        assertThat(syncedAt).isGreaterThanOrEqualTo(before);
        assertThat(postRepository.findById(postId).orElseThrow().getViewCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("좋아요 이벤트 하나로 커밋 뒤 통계 변경이 표시되고, 동기화 스케줄러가 통계를 발행한다")
    void likeEventMarksStatsUpdatedAndSchedulerPublishes() throws Exception {
        Long postId = savePost();

        likeService.addLike(new LikeRequest.AddDTO(postId), 1L);

        // 스케줄러는 5초마다 돈다. 표시(StatsUpdate 이벤트, AFTER_COMMIT) → 발행까지 기다린다
        long deadline = System.currentTimeMillis() + 15_000;
        String payload = null;
        while (System.currentTimeMillis() < deadline && payload == null) {
            payload = jdbcTemplate.query(
                    "SELECT payload FROM outbox WHERE aggregate_id = ? AND payload LIKE '%syncedAt%' "
                            + "ORDER BY id DESC LIMIT 1",
                    rs -> rs.next() ? rs.getString(1) : null, postId);
            if (payload == null) {
                Thread.sleep(500);
            }
        }
        assertThat(payload).isNotNull().contains("\"likeCount\":1");
    }

    @Test
    @DisplayName("관리자 복구 이벤트 하나로 검색 재색인(CREATED)이 쌓이고, 커밋 뒤 표시로 스케줄러가 통계를 발행한다")
    void restoreEventReindexesAndPublishesStats() throws Exception {
        Long postId = savePost();
        likeService.addLike(new LikeRequest.AddDTO(postId), 1L);
        jdbcTemplate.update("UPDATE post SET is_deleted = true, deleted_at = NOW() WHERE id = ?", postId);
        long lastOutboxId = jdbcTemplate.queryForObject("SELECT COALESCE(MAX(id), 0) FROM outbox", Long.class);

        adminPostService.restorePost(postId);

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM outbox WHERE id > ? AND aggregate_id = ? AND event_type = 'CREATED'",
                        Long.class,
                        lastOutboxId,
                        postId))
                .isEqualTo(1);
        long deadline = System.currentTimeMillis() + 15_000;
        String payload = null;
        while (System.currentTimeMillis() < deadline && payload == null) {
            payload = jdbcTemplate.query(
                    "SELECT payload FROM outbox WHERE id > ? AND aggregate_id = ? AND payload LIKE '%syncedAt%' "
                            + "ORDER BY id DESC LIMIT 1",
                    rs -> rs.next() ? rs.getString(1) : null, lastOutboxId, postId);
            if (payload == null) {
                Thread.sleep(500);
            }
        }
        assertThat(payload).isNotNull().contains("\"likeCount\":1");
    }

    @Test
    @DisplayName("삭제된 게시글에 좋아요하면 POST_NOT_FOUND이고 좋아요·통계가 바뀌지 않는다")
    void likeOnDeletedPost() {
        Long postId = savePost();
        jdbcTemplate.update("UPDATE post SET is_deleted = true, deleted_at = NOW() WHERE id = ?", postId);

        assertThatThrownBy(() -> likeService.addLike(new LikeRequest.AddDTO(postId), 42L))
                .isInstanceOf(PostServiceException.class)
                .extracting("code")
                .isEqualTo(PostServiceErrorCode.POST_NOT_FOUND);
        assertThat(likeRows(postId)).isZero();
        assertThat(likeCount(postId)).isZero();
    }

    @Test
    @DisplayName("통계 슬롯 락을 3초 넘게 기다리면 락 획득 실패로 끝나고 좋아요 INSERT도 되돌린다")
    void lockWaitTimeout() throws Exception {
        Long postId = savePost();
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService holder = Executors.newSingleThreadExecutor();
        try {
            // 다른 트랜잭션이 게시글의 통계 슬롯을 모두 잠근 채로 있는 상황
            Future<?> holding = holder.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                jdbcTemplate.queryForList("SELECT * FROM post_stats WHERE post_id = ? FOR UPDATE", postId);
                locked.countDown();
                try {
                    release.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

            long start = System.nanoTime();
            assertThatThrownBy(() -> likeService.addLike(new LikeRequest.AddDTO(postId), 42L))
                    .isInstanceOf(PessimisticLockingFailureException.class);
            long waitedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

            release.countDown();
            holding.get(10, TimeUnit.SECONDS);
            assertThat(waitedMillis).isBetween(2_500L, 10_000L);
            assertThat(likeRows(postId)).isZero();
        } finally {
            release.countDown();
            holder.shutdownNow();
        }
    }

    // 게시글 작성(PostService.createPost)처럼 게시글과 통계 행을 한 트랜잭션에서 만든다
    private Long savePost() {
        return transactionTemplate.execute(status -> {
            Post post = postRepository.save(PostFixture.post(1L));
            postStatsRepository.saveAll(PostStats.createSlots(post));
            return post.getId();
        });
    }

    private long likeCount(Long postId) {
        return postStatsSlots.summarize(postId).likeCount();
    }

    // 게시글 통계 이벤트(STAT_UPDATED)는 게시글 ID를 키로 outbox에 쌓인다
    private String lastStatPayload(Long postId) {
        return jdbcTemplate.queryForObject(
                "SELECT payload FROM outbox WHERE aggregate_id = ? AND payload LIKE '%syncedAt%' ORDER BY id DESC LIMIT 1",
                String.class, postId);
    }

    private long likeRows(Long postId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM likes WHERE post_id = ?", Long.class, postId);
    }

    private interface TaskFactory {
        Runnable create(int index);
    }

    /** 모든 스레드를 동시에 출발시키고, 각 작업에서 난 예외를 모아 돌려준다. */
    private static List<Throwable> runConcurrently(TaskFactory factory) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        try {
            List<Future<Throwable>> futures = IntStream.range(0, THREADS)
                    .mapToObj(i -> executor.submit((Callable<Throwable>) () -> {
                        start.await();
                        try {
                            factory.create(i).run();
                            return null;
                        } catch (Throwable t) {
                            return t;
                        }
                    }))
                    .toList();
            start.countDown();
            List<Throwable> errors = new ArrayList<>();
            for (Future<Throwable> future : futures) {
                Throwable error = future.get();
                if (error != null) {
                    errors.add(error);
                }
            }
            return errors;
        } finally {
            executor.shutdownNow();
        }
    }
}
