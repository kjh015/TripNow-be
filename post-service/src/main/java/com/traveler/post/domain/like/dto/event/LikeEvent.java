package com.traveler.post.domain.like.dto.event;

import com.traveler.post.domain.like.dto.message.LikeMessage;
import com.traveler.post.domain.post.dto.event.PostEvent;

public final class LikeEvent {

    private LikeEvent() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public record Added(LikeMessage.AddedDTO likeMsg) implements PostEvent.StatsUpdate {
        @Override
        public Long postId() {
            return likeMsg.postId();
        }
    }

    public record Removed(LikeMessage.RemovedDTO likeMsg, Long postId) implements PostEvent.StatsUpdate {}

    // 탈퇴 회원 정리. 한 게시글의 좋아요를 묶어 보낸다
    public record BatchRemoved(LikeMessage.BatchRemovedDTO likeMsg) implements PostEvent.StatsUpdate {
        @Override
        public Long postId() {
            return likeMsg.postId();
        }
    }
}
