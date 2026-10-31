package com.traveler.post.domain.post.repository;

import com.traveler.post.domain.post.dto.result.PostStatsSummary;
import com.traveler.post.domain.post.entity.PostStats;
import com.traveler.post.domain.post.entity.PostStatsId;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostStatsRepository extends JpaRepository<PostStats, PostStatsId> {

    // 슬롯 고르기와 합계 조립은 PostStatsSlots. 락 대기 제한은 커넥션 세션의 innodb_lock_wait_timeout(3초)이 건다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM PostStats s WHERE s.id.postId = :postId AND s.id.slot = :slot")
    Optional<PostStats> findSlotWithLock(@Param("postId") Long postId, @Param("slot") int slot);

    @Query("SELECT new com.traveler.post.domain.post.dto.result.PostStatsSummary("
            + "s.id.postId, SUM(s.likeCount), SUM(s.commentCount), SUM(s.starSum)) "
            + "FROM PostStats s WHERE s.id.postId IN :postIds GROUP BY s.id.postId")
    List<PostStatsSummary> summarizeAll(@Param("postIds") Collection<Long> postIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM post_stats WHERE post_id IN :postIds", nativeQuery = true)
    void hardDeleteByPostIds(@Param("postIds") List<Long> postIds);
}
