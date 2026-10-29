package com.traveler.post.domain.post.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.traveler.post.testsupport.fixture.PostFixture;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PostStatsTest {

    @Test
    @DisplayName("게시글 하나에 슬롯 행을 SLOT_COUNT개 만들고, 번호는 0부터 차례로 붙는다")
    void createSlots() {
        List<PostStats> slots = PostStats.createSlots(PostFixture.post(1L));

        assertThat(slots).hasSize(PostStats.SLOT_COUNT);
        assertThat(slots).extracting(s -> s.getId().getSlot()).containsExactly(0, 1, 2, 3, 4, 5, 6, 7);
        assertThat(slots).allSatisfy(s -> assertThat(s.getLikeCount()).isZero());
    }

    @Test
    @DisplayName("추가와 취소가 다른 슬롯에 가면 슬롯 하나는 음수가 될 수 있고, 합계는 맞다")
    void slotCanGoNegative() {
        List<PostStats> slots = PostStats.createSlots(PostFixture.post(1L));

        slots.get(2).addLike();
        slots.get(5).removeLike();

        assertThat(slots.get(5).getLikeCount()).isEqualTo(-1);
        assertThat(slots.stream().mapToLong(PostStats::getLikeCount).sum()).isZero();
    }

    @Test
    @DisplayName("댓글 추가·별점 변경·제거가 슬롯의 댓글 수와 별점 합계에 반영된다")
    void commentCounters() {
        PostStats slot = PostStats.createSlots(PostFixture.post(1L)).getFirst();

        slot.addComment(5);
        slot.addComment(3);
        slot.updateCommentStar(3, 1);
        slot.removeComment(5);

        assertThat(slot.getCommentCount()).isEqualTo(1);
        assertThat(slot.getStarSum()).isEqualTo(1);
    }
}
