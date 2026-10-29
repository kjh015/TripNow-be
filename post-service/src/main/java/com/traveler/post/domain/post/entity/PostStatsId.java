package com.traveler.post.domain.post.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 게시글 통계 슬롯의 키 (post_id, slot). */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class PostStatsId implements Serializable {

    private Long postId;

    @Column(nullable = false)
    private Integer slot;
}
