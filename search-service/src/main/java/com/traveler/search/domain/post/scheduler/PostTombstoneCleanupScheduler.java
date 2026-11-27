package com.traveler.search.domain.post.scheduler;

import com.traveler.search.domain.post.repository.PostDocumentRepository;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 삭제 표시(tombstone)된 게시글 문서를 7일 뒤 지운다. 7일은 post-service Outbox 재전송 기간(약 15분)보다 충분히 길어, 그 뒤에 예전
 * 버전의 생성 이벤트가 도착해 글을 되살릴 일은 없다.
 *
 * <p>search-service가 여러 대면 같은 시각에 모두 실행되지만, 같은 조건의 delete_by_query라 결과가 같으므로 따로 잠그지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostTombstoneCleanupScheduler {

    static final Duration RETENTION = Duration.ofDays(7);

    private final PostDocumentRepository postDocumentRepository;

    // post-service 정리 배치(03:00·03:30·04:00)와 겹치지 않는 시각
    @Scheduled(cron = "0 30 4 * * *")
    public void cleanupTombstones() {
        try {
            long deleted =
                    postDocumentRepository.deleteTombstonesBefore(Instant.now().minus(RETENTION));
            log.info("[PostTombstoneCleanup] 삭제 표시 문서 {}건 정리", deleted);
        } catch (RuntimeException e) {
            log.error("[PostTombstoneCleanup] 삭제 표시 문서 정리 실패", e);
        }
    }
}
