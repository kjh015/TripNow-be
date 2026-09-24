package com.traveler.post.domain.post.scheduler;

import com.traveler.post.domain.post.repository.PostViewRepository;
import com.traveler.post.domain.post.service.PostService;
import java.time.Duration;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PostViewSyncScheduler {

    private static final Duration FLUSH_LOCK_TTL = Duration.ofSeconds(30);

    private final PostViewRepository postViewRepository;
    private final PostService postService;

    // 5초(5,000ms)마다 실행
    @Scheduled(fixedDelay = 5000)
    public void flushViewCounts() {
        // 배포 중 인스턴스가 두 개 떠 있어도 한 인스턴스만 처리용 키를 다루도록 락을 건다
        String lockToken = postViewRepository.tryAcquireFlushLock(FLUSH_LOCK_TTL);
        if (lockToken == null) {
            return;
        }

        try {
            // 처리용 버퍼로 격리 (이전 주기에 실패해 남은 처리용 키가 있으면 그것부터 처리)
            if (!postViewRepository.prepareProcessingBuffer()) {
                return;
            }

            // 데이터 추출
            Map<Object, Object> viewCountMap = postViewRepository.getProcessingViewCounts();

            // DB 및 Outbox 동기화 위임
            postService.flushViewCountsToDB(viewCountMap);

            log.info("[ViewCount] {}개의 게시글 조회수가 DB에 동기화되었습니다.", viewCountMap.size());

            // 버퍼 정리
            postViewRepository.deleteProcessingBuffer();
        } catch (Exception e) {
            // 실패 시 PROCESSING_KEY를 보존하여 다음 주기에 재시도
            log.error("[ViewCount] 조회수 동기화 배치 실패. 버퍼를 보존하고 다음 주기에 재시도합니다.", e);
        } finally {
            postViewRepository.releaseFlushLock(lockToken);
        }
    }
}
