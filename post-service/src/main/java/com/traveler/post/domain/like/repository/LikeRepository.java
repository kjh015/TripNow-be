package com.traveler.post.domain.like.repository;

import com.traveler.post.domain.like.entity.Like;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LikeRepository extends JpaRepository<Like, Long> {
    boolean existsByPostIdAndMemberId(Long postId, Long memberId);

    Optional<Like> findByPostIdAndMemberId(Long postId, Long memberId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Like l WHERE l.post.id IN :postIds")
    void hardDeleteLikesByPostIds(@Param("postIds") List<Long> postIds);
}
