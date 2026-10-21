package com.traveler.post.domain.like.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.traveler.post.domain.comment.dto.request.CommentRequest;
import com.traveler.post.domain.comment.service.CommentService;
import com.traveler.post.domain.like.dto.request.LikeRequest;
import com.traveler.post.domain.post.entity.Post;
import com.traveler.post.domain.post.repository.PostRepository;
import com.traveler.post.support.IntegrationTestSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 같은 게시글에 동시에 들어온 좋아요·댓글 요청이 게시글 행 비관적 락(findByIdWithLock, SELECT ... FOR UPDATE)으로
 * 직렬화되어 카운터와 실제 행 수가 맞는지 확인한다. H2는 락 동작이 달라 MySQL 컨테이너로 돌린다.
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
        Post post = postRepository.findById(postId).orElseThrow();
        long expectedStarSum = IntStream.range(0, THREADS).map(i -> i % 5 + 1).sum();
        assertThat(post.getCommentCount()).isEqualTo(THREADS);
        assertThat(post.getStarSum()).isEqualTo(expectedStarSum);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM comment WHERE post_id = ? AND is_deleted = false", Long.class, postId))
                .isEqualTo(THREADS);
    }

    private Long savePost() {
        return postRepository
                .save(Post.builder().memberId(1L).title("동시성").content("본문").build())
                .getId();
    }

    private long likeCount(Long postId) {
        return postRepository.findById(postId).orElseThrow().getLikeCount();
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
