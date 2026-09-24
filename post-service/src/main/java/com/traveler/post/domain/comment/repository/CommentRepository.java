package com.traveler.post.domain.comment.repository;

import com.traveler.post.domain.comment.entity.Comment;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    @Query(
            value = "SELECT c.id FROM comment c WHERE c.deleted_at <= :threshold AND c.is_deleted = true",
            nativeQuery = true)
    Slice<Long> findExpiredCommentIds(@Param("threshold") Instant threshold, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    // JPQL 벌크 DELETE에도 @SQLRestriction(is_deleted = false)이 붙어 소프트 삭제된 댓글이 지워지지 않으므로 네이티브 쿼리 사용
    @Query(value = "DELETE FROM comment WHERE id IN :commentIds", nativeQuery = true)
    void hardDeleteCommentsByIds(@Param("commentIds") List<Long> commentIds);

    // 게시글 영구 삭제 시 소프트 삭제된 댓글까지 지워야 하므로 @SQLRestriction을 타지 않는 네이티브 쿼리 사용
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM comment WHERE post_id IN :postIds", nativeQuery = true)
    void hardDeleteCommentsByPostIds(@Param("postIds") List<Long> postIds);

    // Admin - @SQLRestriction 우회를 위해 삭제된 댓글도 포함하여 조회하는 네이티브 쿼리
    @Query(
            value = "SELECT * FROM comment c WHERE (:postId IS NULL OR c.post_id = :postId) "
                    + "AND (:deleted IS NULL OR c.is_deleted = :deleted) "
                    + "ORDER BY c.created_at DESC",
            countQuery = "SELECT COUNT(*) FROM comment c WHERE (:postId IS NULL OR c.post_id = :postId) "
                    + "AND (:deleted IS NULL OR c.is_deleted = :deleted)",
            nativeQuery = true)
    Page<Comment> findAllForAdmin(@Param("postId") Long postId, @Param("deleted") Boolean deleted, Pageable pageable);

    @Query(value = "SELECT * FROM comment c WHERE c.id = :commentId FOR UPDATE", nativeQuery = true)
    Optional<Comment> findByIdForAdminWithLock(@Param("commentId") Long commentId);
}
