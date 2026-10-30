package com.traveler.post.domain.post.entity;

import com.traveler.common.db.entity.BaseEntity;
import com.traveler.post.global.exception.PostServiceException;
import com.traveler.post.global.exception.code.PostServiceErrorCode;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

@SuperBuilder
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
@Table(
        name = "post",
        indexes = {
            @Index(name = "idx_post_deleted_at_status", columnList = "is_deleted, deleted_at"),
            // 탈퇴 회원의 게시글 정리(MemberWithdrawalHandler)
            @Index(name = "idx_post_member_id", columnList = "member_id")
        })
public class Post extends BaseEntity {

    @Column(nullable = false)
    private Long memberId;

    @OneToOne(
            fetch = FetchType.LAZY,
            cascade = {CascadeType.PERSIST, CascadeType.REMOVE},
            orphanRemoval = true)
    @JoinColumn(name = "travel_place_id")
    private TravelPlace travelPlace;

    @Column(length = 100)
    private String title;

    @Column(length = 2000)
    private String content;

    // view_count(조회수)는 user-activity-service가 발행한 조회 이벤트(Kafka)를 Redis에 모아 두고,
    // 통계 동기화 스케줄러(PostStatsSyncScheduler)가 5초마다 일괄 반영한다.
    // 스케줄러는 한 서버에서만 실행되므로 동시 갱신이 발생하지 않아 post 칼럼으로 둔다.
    //
    // like_count(좋아요 수), comment_count(댓글 수), star_sum(별점 합계)은 사용자 요청마다 갱신된다.
    // 같은 게시글에 요청이 집중되면 post 칼럼의 잠금 경합이 발생하므로,
    // 이 값들은 PostStats(post_stats)에 게시글당 여러 행(슬롯)으로 분산한다.
    @Builder.Default
    private Long viewCount = 0L;

    @Builder.Default
    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PostImage> images = new ArrayList<>();

    @Builder.Default
    private boolean isDeleted = false;

    private Instant deletedAt;

    public void addPostImage(String imageKey, int sortOrder) {
        PostImage postImage = PostImage.builder()
                .imageKey(imageKey)
                .sortOrder(sortOrder)
                .post(this)
                .build();
        this.images.add(postImage);
    }

    public void update(String title, String content) {
        validateNotDeleted();
        this.title = title;
        this.content = content;
    }

    public void delete() {
        if (this.isDeleted) {
            return;
        }
        this.isDeleted = true;
        this.deletedAt = Instant.now();
    }

    public void restore() {
        if (!this.isDeleted) {
            throw new PostServiceException(PostServiceErrorCode.POST_NOT_DELETED);
        }
        this.isDeleted = false;
        this.deletedAt = null;
    }

    public List<String> setImages(List<String> newUrls) {
        if (newUrls == null) return Collections.emptyList();
        // List를 Set으로 변환하여 탐색 속도 개선
        Set<String> newUrlSet = new HashSet<>(newUrls);
        if (newUrlSet.size() != newUrls.size()) {
            throw new PostServiceException(PostServiceErrorCode.POST_IMAGE_DUPLICATE);
        }

        // 삭제될 이미지 추출
        List<String> keysToDelete = this.images.stream()
                .map(PostImage::getImageKey)
                .filter(key -> !newUrlSet.contains(key))
                .toList();

        this.images.removeIf(img -> !newUrlSet.contains(img.getImageKey()));

        // 기존 이미지 순서 업데이트 및 신규 이미지 추가
        Map<String, PostImage> currentImageMap =
                this.images.stream().collect(Collectors.toMap(PostImage::getImageKey, img -> img));

        for (int i = 0; i < newUrls.size(); i++) {
            String url = newUrls.get(i);
            int sequence = i + 1;
            if (currentImageMap.containsKey(url)) {
                currentImageMap.get(url).updateSequence(sequence);
            } else {
                this.addPostImage(url, sequence);
            }
        }

        return keysToDelete;
    }

    // 요청 키 중 현재 게시글에 붙어 있지 않은 키(= 새로 추가될 키)
    public Set<String> findNewImageKeys(List<String> keys) {
        if (keys == null) return Collections.emptySet();
        Set<String> currentKeys =
                this.images.stream().map(PostImage::getImageKey).collect(Collectors.toSet());
        return keys.stream()
                .filter(key -> !currentKeys.contains(key))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void validateNotDeleted() {
        if (this.isDeleted) {
            throw new PostServiceException(PostServiceErrorCode.POST_ALREADY_DELETED);
        }
    }
}
