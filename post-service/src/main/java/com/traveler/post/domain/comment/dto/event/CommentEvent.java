package com.traveler.post.domain.comment.dto.event;

import com.traveler.post.domain.comment.dto.message.CommentMessage;
import com.traveler.post.domain.post.dto.event.PostEvent;

public final class CommentEvent {

    private CommentEvent() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    // PostStat은 StatsUpdate로 커밋 뒤 통계 변경이 표시되고 스케줄러가 모아 보낸다
    public record Created(CommentMessage.CreatedDTO commentMsg) implements PostEvent.StatsUpdate {
        @Override
        public Long postId() {
            return commentMsg.postId();
        }
    }

    public record Updated(CommentMessage.UpdatedDTO commentMsg, Long postId) implements PostEvent.StatsUpdate {}

    public record Deleted(CommentMessage.DeletedDTO commentMsg, Long postId) implements PostEvent.StatsUpdate {}

    // 탈퇴 회원 정리. 한 게시글의 댓글을 묶어 보낸다
    public record BatchDeleted(CommentMessage.BatchDeletedDTO commentMsg) implements PostEvent.StatsUpdate {
        @Override
        public Long postId() {
            return commentMsg.postId();
        }
    }

    // 부모 게시물이 이미 삭제되어 통계 갱신 없이 댓글 삭제 메시지만 발행하는 어드민 전용 이벤트
    public record AdminDeleted(CommentMessage.DeletedDTO commentMsg, Long postId) {}
}
