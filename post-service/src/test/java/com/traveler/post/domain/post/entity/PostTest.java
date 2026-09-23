package com.traveler.post.domain.post.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.traveler.post.global.exception.PostServiceException;
import com.traveler.post.global.exception.code.PostServiceErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PostTest {

    @Test
    @DisplayName("삭제된 게시글에 댓글을 추가하면 예외가 나고 카운터가 바뀌지 않는다")
    void addCommentOnDeletedPost() {
        Post post = Post.builder().memberId(1L).build();
        post.delete();

        assertThatThrownBy(() -> post.addComment(5))
                .isInstanceOf(PostServiceException.class)
                .extracting("code")
                .isEqualTo(PostServiceErrorCode.POST_ALREADY_DELETED);
        assertThat(post.getCommentCount()).isZero();
        assertThat(post.getStarSum()).isZero();
    }

    @Test
    @DisplayName("삭제된 게시글에서 댓글을 제거하면 예외가 나고 카운터가 바뀌지 않는다")
    void removeCommentOnDeletedPost() {
        Post post = Post.builder().memberId(1L).build();
        post.addComment(4);
        post.delete();

        assertThatThrownBy(() -> post.removeComment(4))
                .isInstanceOf(PostServiceException.class)
                .extracting("code")
                .isEqualTo(PostServiceErrorCode.POST_ALREADY_DELETED);
        assertThat(post.getCommentCount()).isEqualTo(1);
        assertThat(post.getStarSum()).isEqualTo(4);
    }

    @Test
    @DisplayName("삭제되지 않은 게시글은 댓글 추가·제거가 카운터에 반영된다")
    void commentCountersOnActivePost() {
        Post post = Post.builder().memberId(1L).build();

        post.addComment(5);
        post.addComment(3);
        post.removeComment(5);

        assertThat(post.getCommentCount()).isEqualTo(1);
        assertThat(post.getStarSum()).isEqualTo(3);
        assertThat(post.getStarAvg()).isEqualTo(3.0);
    }
}
