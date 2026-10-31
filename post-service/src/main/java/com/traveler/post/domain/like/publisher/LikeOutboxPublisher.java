package com.traveler.post.domain.like.publisher;

import com.traveler.post.domain.like.dto.message.LikeMessage;
import com.traveler.post.domain.like.enums.LikeEventType;
import com.traveler.post.global.kafka.KafkaTopicProperties;
import com.traveler.post.global.outbox.event.OutboxEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** 메시지 키는 게시글 ID다. 같은 게시글의 좋아요 메시지가 한 파티션에 들어가 묶음 삭제와도 순서가 유지된다. */
@Component
public class LikeOutboxPublisher extends OutboxEventPublisher {

    public LikeOutboxPublisher(ApplicationEventPublisher eventPublisher, KafkaTopicProperties topicProperties) {
        super(eventPublisher, topicProperties);
    }

    public void publishAdded(LikeMessage.AddedDTO payload) {
        publish(payload.postId(), LikeEventType.ADDED, payload);
    }

    public void publishRemoved(LikeMessage.RemovedDTO payload, Long postId) {
        publish(postId, LikeEventType.REMOVED, payload);
    }

    public void publishBatchRemoved(LikeMessage.BatchRemovedDTO payload) {
        publish(payload.postId(), LikeEventType.BATCH_REMOVED, payload);
    }
}
