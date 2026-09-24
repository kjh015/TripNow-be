package com.traveler.post.domain.comment.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.traveler.post.domain.comment.entity.Comment;
import com.traveler.post.domain.post.entity.Post;
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

// 운영은 MySQL이라 H2 MySQL 모드로 근사한다.
@DataJpaTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:comment-repository;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.jpa.database=h2",
            "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
            "spring.jpa.hibernate.ddl-auto=create-drop"
        })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(CommentRepositoryTest.AuditingConfig.class)
class CommentRepositoryTest {

    @TestConfiguration
    @EnableJpaAuditing
    static class AuditingConfig {}

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private EntityManager em;

    @Test
    @DisplayName("만료된 소프트 삭제 댓글을 조회해 배치 삭제하면 실제로 지워지고 다시 조회되지 않는다")
    void expiredCommentsAreFoundAndDeleted() {
        Post post = Post.builder().memberId(1L).title("title").build();
        em.persist(post);
        Comment expired = persistComment(post, true);
        Comment active = persistComment(post, false);
        em.flush();
        em.clear();

        Instant threshold = Instant.now().plus(1, ChronoUnit.MINUTES);
        List<Long> ids = commentRepository
                .findExpiredCommentIds(threshold, PageRequest.of(0, 500))
                .getContent();

        assertThat(ids).containsExactly(expired.getId());

        commentRepository.hardDeleteCommentsByIds(ids);

        assertThat(commentRepository.findExpiredCommentIds(threshold, PageRequest.of(0, 500)))
                .isEmpty();
        assertThat(count(expired.getId())).isZero();
        assertThat(count(active.getId())).isEqualTo(1);
    }

    private Comment persistComment(Post post, boolean deleted) {
        Comment comment = Comment.builder()
                .memberId(2L)
                .post(post)
                .content("comment")
                .star(5)
                .build();
        if (deleted) {
            comment.delete();
        }
        em.persist(comment);
        return comment;
    }

    private long count(Long id) {
        return ((Number) em.createNativeQuery("SELECT COUNT(*) FROM comment WHERE id = ?1")
                        .setParameter(1, id)
                        .getSingleResult())
                .longValue();
    }
}
