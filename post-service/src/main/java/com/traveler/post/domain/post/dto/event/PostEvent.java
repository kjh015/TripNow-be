package com.traveler.post.domain.post.dto.event;

import com.traveler.post.domain.post.dto.message.PostMessage;

public final class PostEvent {

    private PostEvent() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public record Created(PostMessage.CreatedDTO postMsg) {}

    public record Updated(PostMessage.UpdatedDTO postMsg) {}

    public record Deleted(PostMessage.DeletedDTO postMsg) {}

    /** 관리자 게시글 복구. 삭제 때 지워진 검색 문서를 생성 메시지로 다시 만들고, 통계도 다시 보낸다(StatsUpdate) */
    public record Restored(PostMessage.CreatedDTO postMsg) implements StatsUpdate {

        @Override
        public Long postId() {
            return postMsg.postId();
        }
    }

    public record ImagesDelete(PostMessage.ImagesDeleteDTO postMsg) {}

    public record ImagesDeleteBatch(PostMessage.ImagesDeleteDTO postMsg) {}

    public record StatUpdated(PostMessage.UpdateStatDTO postMsg) {}

    /**
     * 게시글 통계(좋아요·댓글)를 바꾸는 이벤트. 좋아요·댓글 이벤트가 구현하므로 이벤트 하나만 발행하면 되고, 커밋 뒤 통계 변경이
     * 표시된다(PostEventListener). 통계 동기화 스케줄러가 모아 StatUpdated로 보낸다
     */
    public interface StatsUpdate {
        Long postId();
    }
}
