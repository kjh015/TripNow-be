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
        indexes = {@Index(name = "idx_post_deleted_at_status", columnList = "is_deleted, deleted_at")})
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

    @Builder.Default
    private Long viewCount = 0L;

    @Builder.Default
    private Long commentCount = 0L;

    @Builder.Default
    private Long likeCount = 0L;

    @Builder.Default
    private Long starSum = 0L;

    @Builder.Default
    private Double starAvg = 0.0;

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

    public void addComment(int star) {
        this.commentCount++;
        this.starSum += star;
        this.starAvg = (double) this.starSum / this.commentCount;
    }

    public void updateComment(int oldStar, int newStar) {
        validateNotDeleted();
        this.starSum = this.starSum - oldStar + newStar;
        this.starAvg = this.commentCount > 0 ? (double) this.starSum / this.commentCount : 0.0;
    }

    public void removeComment(int star) {
        if (this.commentCount > 0) {
            this.commentCount--;
            this.starSum -= star;
            this.starAvg = this.commentCount > 0 ? (double) this.starSum / this.commentCount : 0.0;
        }
    }

    public void addLike() {
        validateNotDeleted();
        this.likeCount++;
    }

    public void removeLike() {
        validateNotDeleted();
        if (this.likeCount > 0) {
            this.likeCount--;
        }
    }

    private void validateNotDeleted() {
        if (this.isDeleted) {
            throw new PostServiceException(PostServiceErrorCode.POST_ALREADY_DELETED);
        }
    }
}
