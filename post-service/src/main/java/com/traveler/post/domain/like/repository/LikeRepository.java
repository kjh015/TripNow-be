package com.traveler.post.domain.like.repository;

import com.traveler.post.domain.like.entity.Like;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LikeRepository extends JpaRepository<Like, Long> {
    Optional<Like> findByPostIdAndMemberId(Long postId, Long memberId);

    // 같은 회원의 중복 좋아요는 유니크 제약(uk_like_member_post)이 막는다. INSERT IGNORE는 중복이면 예외 없이 0을 돌려준다
    @Modifying
    @Query(
            value = "INSERT IGNORE INTO likes (member_id, post_id, created_at, updated_at) "
                    + "VALUES (:memberId, :postId, :now, :now)",
            nativeQuery = true)
    int insertIfAbsent(@Param("postId") Long postId, @Param("memberId") Long memberId, @Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM Like l WHERE l.id = :id")
    int deleteIfPresent(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Like l WHERE l.post.id IN :postIds")
    void hardDeleteLikesByPostIds(@Param("postIds") List<Long> postIds);
}
