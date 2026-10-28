package com.traveler.post.domain.post.listener;

import com.traveler.post.domain.post.dto.event.PostEvent;
import com.traveler.post.domain.post.publisher.PostOutboxPublisher;
import com.traveler.post.domain.post.repository.PostStatsSyncRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class PostEventListener {
    private final PostOutboxPublisher postOutboxPublisher;
    private final PostStatsSyncRepository postStatsSyncRepository;

    @EventListener
    public void handleCreatedEvent(PostEvent.Created event) {
        postOutboxPublisher.publishCreated(event.postMsg());
    }

    @EventListener
    public void handleUpdatedEvent(PostEvent.Updated event) {
        postOutboxPublisher.publishUpdated(event.postMsg());
    }

    @EventListener
    public void handleDeletedEvent(PostEvent.Deleted event) {
        postOutboxPublisher.publishDeleted(event.postMsg());
    }

    // 검색 쪽에는 생성 메시지로 다시 색인한다. 통계 표시는 handleStatsUpdate가 받는다
    @EventListener
    public void handleRestoredEvent(PostEvent.Restored event) {
        postOutboxPublisher.publishCreated(event.postMsg());
    }

    @EventListener
    public void handleDeleteImageEvent(PostEvent.ImagesDelete event) {
        postOutboxPublisher.publishImagesDelete(event.postMsg());
    }

    @EventListener
    public void handleDeleteImageEvent(PostEvent.ImagesDeleteBatch event) {
        postOutboxPublisher.publishImagesDeleteBatch(event.postMsg());
    }

    @EventListener
    public void handleStatUpdatedEvent(PostEvent.StatUpdated event) {
        postOutboxPublisher.publishStatUpdated(event.postMsg());
    }

    // 좋아요·댓글 이벤트(StatsUpdate 구현)마다 통계 변경을 표시한다.
    // 커밋 전에 표시하면 스케줄러가 커밋 전 값을 읽고 표시를 지워 새 값이 발행되지 않을 수 있어 커밋 뒤에 표시한다
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleStatsUpdate(PostEvent.StatsUpdate event) {
        try {
            postStatsSyncRepository.markStatsUpdated(event.postId());
        } catch (RuntimeException e) {
            // 이미 커밋된 요청을 실패로 돌려보내지 않는다. 다음 변경 때 다시 표시된다
            log.warn("[PostStats] 통계 변경 표시 실패 postId={}", event.postId(), e);
        }
    }
}
