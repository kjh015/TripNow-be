package com.traveler.post.domain.post.entity;

import jakarta.persistence.*;
import java.util.List;
import java.util.stream.IntStream;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 게시글 통계(좋아요·댓글·별점)를 저장하는 슬롯 엔티티.
 * 게시글마다 {@link #SLOT_COUNT}개의 슬롯을 두며, 게시글 통계는 모든 슬롯의 합계로 계산한다.
 *
 * <p>특정 게시글에 쓰기가 집중될 때 행(칼럼) 잠금 경합을 줄이기 위해 통계를 여러 행으로 분산한다. 요청은
 * {@code PostStatsSlots.getRandom}로 슬롯 하나를 무작위로 선택해 잠근 뒤, 해당 슬롯의 값을 갱신한다.
 * 행 잠금은 트랜잭션 커밋까지 유지되므로, 좋아요·댓글 쓰기를 마친 후 슬롯을 잠근다.
 *
 * <p>좋아요·댓글의 추가와 취소가 서로 다른 슬롯에 반영될 수 있으므로 슬롯별 값은 음수가 될 수 있다.
 * 게시글 통계는 슬롯별 값의 합계로 계산한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "post_stats")
public class PostStats {

    public static final int SLOT_COUNT = 8;

    @EmbeddedId
    private PostStatsId id;

    @MapsId("postId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id")
    private Post post;

    @Column(nullable = false)
    private Long likeCount = 0L;

    @Column(nullable = false)
    private Long commentCount = 0L;

    @Column(nullable = false)
    private Long starSum = 0L;

    private PostStats(Post post, int slot) {
        this.id = new PostStatsId(null, slot);
        this.post = post;
    }

    /** 게시글을 만들 때 슬롯 {@link #SLOT_COUNT}개의 행을 만든다. */
    public static List<PostStats> createSlots(Post post) {
        return IntStream.range(0, SLOT_COUNT)
                .mapToObj(slot -> new PostStats(post, slot))
                .toList();
    }

    public void addLike() {
        this.likeCount++;
    }

    public void removeLike() {
        this.likeCount--;
    }

    public void addComment(int star) {
        this.commentCount++;
        this.starSum += star;
    }

    public void updateCommentStar(int oldStar, int newStar) {
        this.starSum = this.starSum - oldStar + newStar;
    }

    public void removeComment(int star) {
        this.commentCount--;
        this.starSum -= star;
    }

    // 탈퇴 회원 정리: 한 게시글에서 지운 댓글 여러 개를 슬롯 하나에 한 번에 반영한다
    public void removeComments(long count, long starSum) {
        this.commentCount -= count;
        this.starSum -= starSum;
    }

    // 탈퇴 회원 정리: 한 게시글에서 지운 좋아요 여러 개를 슬롯 하나에 한 번에 반영한다
    public void removeLikes(long count) {
        this.likeCount -= count;
    }
}
