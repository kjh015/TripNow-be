package com.traveler.post.domain.post.support;

import static com.traveler.post.testsupport.fixture.PostFixture.MEMBER_1_KEY;
import static com.traveler.post.testsupport.fixture.PostFixture.MEMBER_1_KEY_2;
import static org.assertj.core.api.Assertions.assertThat;

import com.traveler.post.domain.comment.entity.Comment;
import com.traveler.post.domain.like.entity.Like;
import com.traveler.post.domain.post.entity.Post;
import com.traveler.post.domain.post.entity.PostStats;
import com.traveler.post.domain.post.entity.TravelPlace;
import com.traveler.post.domain.post.repository.PostRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

// 운영은 MySQL이라 H2 MySQL 모드로 근사한다. 테이블명 대소문자(#8)는 H2가 구분하지 않아 여기서 검증되지 않는다.
@DataJpaTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:post-hard-delete;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.jpa.database=h2",
            "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            // 운영 설정의 MySQL 전용 락 대기 제한(SET SESSION innodb_lock_wait_timeout)은 H2에서 실행되지 않는다
            "spring.datasource.hikari.connection-init-sql=SELECT 1"
        })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostHardDeleter.class, PostHardDeleterTest.AuditingConfig.class})
class PostHardDeleterTest {

    @TestConfiguration
    @EnableJpaAuditing
    static class AuditingConfig {}

    @Autowired
    private PostHardDeleter postHardDeleter;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private EntityManager em;

    @Test
    @DisplayName("이미지·좋아요·댓글·통계·장소가 달린 소프트 삭제 게시글을 영구 삭제하면 자식 행이 모두 지워진다")
    void hardDeleteRemovesChildRows() {
        Post target = persistPostWithChildren(true);
        Post other = persistPostWithChildren(false);
        Long targetPlaceId = target.getTravelPlace().getId();
        em.flush();
        em.clear();

        List<String> imageKeys = postHardDeleter.hardDelete(List.of(target.getId()));

        assertThat(imageKeys).containsExactlyInAnyOrder(MEMBER_1_KEY, MEMBER_1_KEY_2);
        assertThat(count("SELECT COUNT(*) FROM post WHERE id = ?1", target.getId()))
                .isZero();
        assertThat(count("SELECT COUNT(*) FROM post_image WHERE post_id = ?1", target.getId()))
                .isZero();
        assertThat(count("SELECT COUNT(*) FROM likes WHERE post_id = ?1", target.getId()))
                .isZero();
        assertThat(count("SELECT COUNT(*) FROM comment WHERE post_id = ?1", target.getId()))
                .isZero();
        assertThat(count("SELECT COUNT(*) FROM post_stats WHERE post_id = ?1", target.getId()))
                .isZero();
        assertThat(count("SELECT COUNT(*) FROM travel_place WHERE id = ?1", targetPlaceId))
                .isZero();

        // 다른 게시글은 그대로
        assertThat(count("SELECT COUNT(*) FROM post WHERE id = ?1", other.getId()))
                .isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM post_image WHERE post_id = ?1", other.getId()))
                .isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM likes WHERE post_id = ?1", other.getId()))
                .isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM comment WHERE post_id = ?1", other.getId()))
                .isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM post_stats WHERE post_id = ?1", other.getId()))
                .isEqualTo(PostStats.SLOT_COUNT);
    }

    @Test
    @DisplayName("소프트 삭제되지 않은 게시글(어드민 즉시 영구 삭제)도 자식 행과 함께 지워진다")
    void hardDeleteActivePost() {
        Post target = persistPostWithChildren(false);
        em.flush();
        em.clear();

        postHardDeleter.hardDelete(List.of(target.getId()));

        assertThat(count("SELECT COUNT(*) FROM post WHERE id = ?1", target.getId()))
                .isZero();
        assertThat(count("SELECT COUNT(*) FROM comment WHERE post_id = ?1", target.getId()))
                .isZero();
    }

    @Test
    @DisplayName("만료된 게시글 ID 조회 후 배치 삭제까지 이어진다")
    void expiredPostsAreFoundAndDeleted() {
        Post expired = persistPostWithChildren(true);
        persistPostWithChildren(false);
        em.flush();
        em.clear();

        Instant threshold = Instant.now().plus(1, ChronoUnit.MINUTES);
        List<Long> ids = postRepository
                .findExpiredPostIds(threshold, PageRequest.of(0, 500))
                .getContent();

        assertThat(ids).containsExactly(expired.getId());

        postHardDeleter.hardDelete(ids);

        assertThat(postRepository.findExpiredPostIds(threshold, PageRequest.of(0, 500)))
                .isEmpty();
    }

    private Post persistPostWithChildren(boolean deleted) {
        Post post = Post.builder()
                .memberId(1L)
                .title("title")
                .content("content")
                .travelPlace(TravelPlace.builder().name("place").build())
                .build();
        post.addPostImage(MEMBER_1_KEY, 1);
        post.addPostImage(MEMBER_1_KEY_2, 2);
        em.persist(post);
        PostStats.createSlots(post).forEach(em::persist);

        em.persist(Like.builder().memberId(2L).post(post).build());
        em.persist(Like.builder().memberId(3L).post(post).build());

        em.persist(Comment.builder()
                .memberId(2L)
                .post(post)
                .content("comment")
                .star(5)
                .build());
        Comment deletedComment = Comment.builder()
                .memberId(3L)
                .post(post)
                .content("deleted comment")
                .star(3)
                .build();
        deletedComment.delete();
        em.persist(deletedComment);

        if (deleted) {
            post.delete();
        }
        return post;
    }

    private long count(String sql, Long id) {
        return ((Number) em.createNativeQuery(sql).setParameter(1, id).getSingleResult()).longValue();
    }
}
