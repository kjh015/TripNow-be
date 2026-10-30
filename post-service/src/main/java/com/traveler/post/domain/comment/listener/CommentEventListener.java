package com.traveler.post.domain.comment.listener;

import com.traveler.post.domain.comment.dto.event.CommentEvent;
import com.traveler.post.domain.comment.publisher.CommentOutboxPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CommentEventListener {
    private final CommentOutboxPublisher commentOutboxPublisher;

    @EventListener
    public void handleCreatedEvent(CommentEvent.Created event) {
        commentOutboxPublisher.publishCreated(event.commentMsg());
    }

    @EventListener
    public void handleUpdatedEvent(CommentEvent.Updated event) {
        commentOutboxPublisher.publishUpdated(event.commentMsg(), event.postId());
    }

    @EventListener
    public void handleDeletedEvent(CommentEvent.Deleted event) {
        commentOutboxPublisher.publishDeleted(event.commentMsg(), event.postId());
    }

    @EventListener
    public void handleBatchDeletedEvent(CommentEvent.BatchDeleted event) {
        commentOutboxPublisher.publishBatchDeleted(event.commentMsg());
    }

    @EventListener
    public void handleAdminDeletedEvent(CommentEvent.AdminDeleted event) {
        commentOutboxPublisher.publishDeleted(event.commentMsg(), event.postId());
    }
}
