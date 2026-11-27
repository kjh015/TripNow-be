package com.traveler.post.domain.post.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.traveler.post.global.exception.PostServiceException;
import com.traveler.post.testsupport.fixture.PostFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PostTest {

    @Test
    @DisplayName("새 게시글의 본문 버전은 0이다")
    void newPostStartsAtZero() {
        assertThat(PostFixture.post(1L).getContentVersion()).isZero();
    }

    @Test
    @DisplayName("수정·삭제·복구는 본문 버전을 1씩 올린다")
    void updateDeleteRestoreIncreaseVersion() {
        Post post = PostFixture.post(1L);

        post.update("새 제목", "새 본문");
        assertThat(post.getContentVersion()).isEqualTo(1L);

        post.delete();
        assertThat(post.getContentVersion()).isEqualTo(2L);

        post.restore();
        assertThat(post.getContentVersion()).isEqualTo(3L);
    }

    @Test
    @DisplayName("제목·본문이 같아도 수정하면 버전이 오른다(장소·이미지만 바뀐 수정)")
    void updateWithSameTextIncreasesVersion() {
        Post post = PostFixture.post(1L);

        post.update(post.getTitle(), post.getContent());

        assertThat(post.getContentVersion()).isEqualTo(1L);
    }

    @Test
    @DisplayName("이미 삭제된 글의 삭제는 아무것도 바꾸지 않고 버전도 그대로다")
    void deleteTwiceKeepsVersion() {
        Post post = PostFixture.post(1L);
        post.delete();
        long version = post.getContentVersion();

        post.delete();

        assertThat(post.getContentVersion()).isEqualTo(version);
    }

    @Test
    @DisplayName("삭제된 글의 수정과 삭제되지 않은 글의 복구는 실패하고 버전을 올리지 않는다")
    void rejectedWritesKeepVersion() {
        Post deleted = PostFixture.post(1L);
        deleted.delete();
        long deletedVersion = deleted.getContentVersion();
        assertThatThrownBy(() -> deleted.update("t", "c")).isInstanceOf(PostServiceException.class);
        assertThat(deleted.getContentVersion()).isEqualTo(deletedVersion);

        Post active = PostFixture.post(1L);
        assertThatThrownBy(active::restore).isInstanceOf(PostServiceException.class);
        assertThat(active.getContentVersion()).isZero();
    }
}
