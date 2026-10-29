package com.traveler.post.domain.post.scheduler;

import com.traveler.post.domain.post.dto.result.PostStatsSyncBatch;
import com.traveler.post.domain.post.repository.PostStatsSyncRepository;
import com.traveler.post.domain.post.service.PostService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 게시글 통계 동기화: 모아 둔 조회수를 DB에 반영하고, 조회수·좋아요·댓글이 바뀐 게시글의 통계를 검색 서버로 보낸다. */
@Slf4j
@Component
public class PostStatsSyncScheduler {

    private static final Duration SYNC_LOCK_TTL = Duration.ofSeconds(30);

    private final PostStatsSyncRepository postStatsSyncRepository;
    private final PostService postService;
    // 조회수를 DB에 동기화한 게시글 수 (post_view_sync_posts_total). 대시보드가 쓰는 이름이라 유지한다
    private final Counter syncedPostsCounter;

    public PostStatsSyncScheduler(
            PostStatsSyncRepository postStatsSyncRepository, PostService postService, MeterRegistry meterRegistry) {
        this.postStatsSyncRepository = postStatsSyncRepository;
        this.postService = postService;
        this.syncedPostsCounter = Counter.builder("post.view.sync.posts")
                .description("조회수를 DB에 동기화한 게시글 수")
                .register(meterRegistry);
    }

    // 5초(5,000ms)마다 실행
    @Scheduled(fixedDelay = 5000)
    public void sync() {
        // 배포 중 인스턴스가 두 개 떠 있어도 한 인스턴스만 처리용 키를 다루도록 락을 건다
        String lockToken = postStatsSyncRepository.tryAcquireSyncLock(SYNC_LOCK_TTL);
        if (lockToken == null) {
            return;
        }

        try {
            // 처리용 키로 격리 (이전 주기에 실패해 남은 처리용 키가 있으면 그것부터 처리)
            if (!postStatsSyncRepository.prepareProcessing()) {
                return;
            }

            PostStatsSyncBatch batch = postStatsSyncRepository.getProcessing();
            postService.syncStats(batch);
            syncedPostsCounter.increment(batch.viewCounts().size());

            log.debug(
                    "[PostStats] 조회수 {}건 반영, 통계 {}건 발행",
                    batch.viewCounts().size(),
                    batch.postIds().size());

            postStatsSyncRepository.deleteProcessing();
        } catch (Exception e) {
            // 실패 시 처리용 키를 보존하여 다음 주기에 재시도
            log.error("[PostStats] 통계 동기화 배치 실패. 처리용 키를 보존하고 다음 주기에 재시도합니다.", e);
        } finally {
            postStatsSyncRepository.releaseSyncLock(lockToken);
        }
    }
}
