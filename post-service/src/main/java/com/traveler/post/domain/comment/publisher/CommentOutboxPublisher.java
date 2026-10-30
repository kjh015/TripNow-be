package com.traveler.post.domain.comment.publisher;

import com.traveler.post.domain.comment.dto.message.CommentMessage;
import com.traveler.post.domain.comment.enums.CommentEventType;
import com.traveler.post.global.kafka.KafkaTopicProperties;
import com.traveler.post.global.outbox.event.OutboxEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** 메시지 키는 게시글 ID다. 같은 게시글의 댓글 메시지가 한 파티션에 들어가 묶음 삭제와도 순서가 유지된다. */
@Component
public class CommentOutboxPublisher extends OutboxEventPublisher {

    public CommentOutboxPublisher(ApplicationEventPublisher eventPublisher, KafkaTopicProperties topicProperties) {
        super(eventPublisher, topicProperties);
    }

    public void publishCreated(CommentMessage.CreatedDTO payload) {
        publish(payload.postId(), CommentEventType.CREATED, payload);
    }

    public void publishUpdated(CommentMessage.UpdatedDTO payload, Long postId) {
        publish(postId, CommentEventType.UPDATED, payload);
    }

    public void publishDeleted(CommentMessage.DeletedDTO payload, Long postId) {
        publish(postId, CommentEventType.DELETED, payload);
    }

    public void publishBatchDeleted(CommentMessage.BatchDeletedDTO payload) {
        publish(payload.postId(), CommentEventType.BATCH_DELETED, payload);
    }
}
