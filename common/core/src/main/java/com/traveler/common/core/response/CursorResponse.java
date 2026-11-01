package com.traveler.common.core.response;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record CursorResponse<T>(List<T> content, String nextCursor, boolean hasNext) {
    public CursorResponse {
        if (content == null) {
            throw new IllegalArgumentException("content must not be null");
        }
    }

    /**
     * 내부 content의 요소를 변환 함수를 통해 가공한 새로운 CursorResponse를 반환합니다.
     */
    public <U> CursorResponse<U> map(Function<? super T, U> converter) {
        Objects.requireNonNull(converter, "converter must not be null");
        return new CursorResponse<>(this.content.stream().map(converter).toList(), this.nextCursor, this.hasNext);
    }
}
