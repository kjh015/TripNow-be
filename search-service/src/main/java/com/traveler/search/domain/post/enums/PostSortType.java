package com.traveler.search.domain.post.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;

@Getter
@RequiredArgsConstructor
public enum PostSortType {
    ACCURACY(null),
    POPULAR("popularityScoreValue"),
    LATEST("createdAt"),
    STAR_AVG("starAvg"),
    VIEW_COUNT("viewCount");
    private final String field;

    /** 피드 정렬 키 개수. ACCURACY는 [_score, id], LATEST는 [createdAt, id], 나머지는 [field, createdAt, id] */
    public int getFeedSortKeyCount() {
        return (this == ACCURACY || this == LATEST) ? 2 : 3;
    }

    public Sort getSort(Sort.Direction direction) {
        if (this == ACCURACY) {
            return Sort.unsorted();
        }
        return Sort.by(direction, this.field);
    }
}
