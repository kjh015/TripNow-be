package com.traveler.post.domain.like.listener;

import com.traveler.post.domain.like.dto.event.LikeEvent;
import com.traveler.post.domain.like.publisher.LikeOutboxPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LikeEventListener {
    private final LikeOutboxPublisher likeOutboxPublisher;

    @EventListener
    public void handleCreatedEvent(LikeEvent.Added event) {
        likeOutboxPublisher.publishAdded(event.likeMsg());
    }

    @EventListener
    public void handleDeletedEvent(LikeEvent.Removed event) {
        likeOutboxPublisher.publishRemoved(event.likeMsg(), event.postId());
    }

    @EventListener
    public void handleBatchRemovedEvent(LikeEvent.BatchRemoved event) {
        likeOutboxPublisher.publishBatchRemoved(event.likeMsg());
    }
}
