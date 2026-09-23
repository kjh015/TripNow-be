package com.traveler.post.domain.post.repository;

import com.traveler.post.domain.post.entity.Post;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Long> {
    @Query("SELECT DISTINCT p FROM Post p "
            + "LEFT JOIN FETCH p.travelPlace tp "
            + "LEFT JOIN FETCH p.images im "
            + "WHERE p.id = :postId ")
    Optional<Post> findByIdWithDetails(Long postId);

    @Query("SELECT pi.imageKey FROM PostImage pi WHERE pi.post.id IN :postIds")
    List<String> findImageKeysByPostIds(@Param("postIds") List<Long> postIds);

    @Query(
            value = "SELECT p.id FROM post p WHERE p.deleted_at <= :threshold AND p.is_deleted = true",
            nativeQuery = true)
    Slice<Long> findExpiredPostIds(@Param("threshold") Instant threshold, Pageable pageable);

    // 영구 삭제 대상은 대부분 소프트 삭제된 게시글이므로 @SQLRestriction을 타지 않는 네이티브 쿼리로 조회
    @Query(
            value = "SELECT p.travel_place_id FROM post p WHERE p.id IN :postIds AND p.travel_place_id IS NOT NULL",
            nativeQuery = true)
    List<Long> findTravelPlaceIdsByPostIds(@Param("postIds") List<Long> postIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM PostImage pi WHERE pi.post.id IN :postIds")
    void hardDeletePostImagesByPostIds(@Param("postIds") List<Long> postIds);

    // JPQL 벌크 DELETE에도 @SQLRestriction(is_deleted = false)이 붙어 소프트 삭제된 게시글이 지워지지 않으므로 네이티브 쿼리 사용
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM post WHERE id IN :postIds", nativeQuery = true)
    void hardDeletePostsByIds(@Param("postIds") List<Long> postIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM TravelPlace tp WHERE tp.id IN :travelPlaceIds")
    void hardDeleteTravelPlacesByIds(@Param("travelPlaceIds") List<Long> travelPlaceIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000")})
    @Query("select p from Post p where p.id = :id")
    Optional<Post> findByIdWithLock(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Post p SET p.viewCount = p.viewCount + :increment WHERE p.id = :postId")
    void incrementViewCount(@Param("postId") Long postId, @Param("increment") Long increment);

    // Admin - @SQLRestriction 우회를 위해 삭제된 게시글도 포함하여 조회하는 네이티브 쿼리
    @Query(
            value = "SELECT * FROM post p WHERE (:deleted IS NULL OR p.is_deleted = :deleted) "
                    + "ORDER BY p.created_at DESC",
            countQuery = "SELECT COUNT(*) FROM post p WHERE (:deleted IS NULL OR p.is_deleted = :deleted)",
            nativeQuery = true)
    Page<Post> findAllForAdmin(@Param("deleted") Boolean deleted, Pageable pageable);

    @Query(value = "SELECT * FROM post p WHERE p.id = :postId", nativeQuery = true)
    Optional<Post> findByIdForAdmin(@Param("postId") Long postId);

    @Query(value = "SELECT * FROM post p WHERE p.id = :postId FOR UPDATE", nativeQuery = true)
    Optional<Post> findByIdForAdminWithLock(@Param("postId") Long postId);
}
