package com.traveler.common.api.converter;

import com.traveler.common.core.response.CursorResponse;
import java.util.List;
import java.util.function.Function;

public final class CursorConverter {
    private CursorConverter() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * List&lt;Entity&gt;를 CursorResponse&lt;DTO&gt;로 변환합니다.
     *
     * <p>size + 1건까지 조회한 결과를 받습니다. size를 넘으면 초과분을 버리고 다음 페이지가 있다고 판단합니다.
     *
     * @param fetched size + 1건까지 조회한 결과
     * @param size 응답할 건수
     * @param cursorOf 응답의 마지막 요소로 다음 cursor를 만드는 함수
     * @param converter 리스트 내부의 각 요소를 변환할 함수 (사용 예: DomainConverter::toDto)
     * @param <E> (Entity) 원본 데이터 타입
     * @param <R> (Result) 변환 후 반환할 데이터 타입
     * @return 변환된 데이터 리스트와 다음 cursor가 담긴 CursorResponse
     */
    public static <E, R> CursorResponse<R> toCursorResponse(
            List<E> fetched, int size, Function<E, String> cursorOf, Function<E, R> converter) {
        boolean hasNext = fetched.size() > size;
        List<E> page = hasNext ? fetched.subList(0, size) : fetched;
        String nextCursor = hasNext ? cursorOf.apply(page.getLast()) : null;

        return new CursorResponse<>(page.stream().map(converter).toList(), nextCursor, hasNext);
    }
}
