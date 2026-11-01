package com.traveler.common.api.converter;

import static org.assertj.core.api.Assertions.assertThat;

import com.traveler.common.core.response.CursorResponse;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CursorConverterTest {

    @Test
    @DisplayName("size를 넘게 조회되면 초과분을 버리고 응답의 마지막 항목으로 cursor를 만든다")
    void hasNext() {
        CursorResponse<String> result =
                CursorConverter.toCursorResponse(List.of(1, 2, 3), 2, last -> "after-" + last, String::valueOf);

        assertThat(result.content()).containsExactly("1", "2");
        assertThat(result.nextCursor()).isEqualTo("after-2");
        assertThat(result.hasNext()).isTrue();
    }

    @Test
    @DisplayName("size만큼만 조회되면 다음 페이지가 없다")
    void exactlySize() {
        CursorResponse<String> result =
                CursorConverter.toCursorResponse(List.of(1, 2), 2, last -> "after-" + last, String::valueOf);

        assertThat(result.content()).containsExactly("1", "2");
        assertThat(result.nextCursor()).isNull();
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    @DisplayName("조회 결과가 없으면 빈 목록을 돌려준다")
    void empty() {
        CursorResponse<String> result =
                CursorConverter.toCursorResponse(List.<Integer>of(), 2, last -> "after-" + last, String::valueOf);

        assertThat(result.content()).isEmpty();
        assertThat(result.nextCursor()).isNull();
        assertThat(result.hasNext()).isFalse();
    }
}
