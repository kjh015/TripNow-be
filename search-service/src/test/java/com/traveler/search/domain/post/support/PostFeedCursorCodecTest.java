package com.traveler.search.domain.post.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.traveler.search.domain.post.enums.PostSortType;
import com.traveler.search.global.exception.SearchServiceException;
import com.traveler.search.global.exception.code.SearchServiceErrorCode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Sort;

class PostFeedCursorCodecTest {

    private final PostFeedCursorCodec codec = new PostFeedCursorCodec(new ObjectMapper());

    @Test
    @DisplayName("sort 값을 인코딩했다가 그대로 읽는다. 작은 정수도 Long으로 돌아온다")
    void roundTrip() {
        String cursor = codec.encode(PostSortType.STAR_AVG, Sort.Direction.ASC, List.of(4.5, 1760000000000L, 7L));

        assertThat(cursor).matches("[A-Za-z0-9_-]+");
        assertThat(codec.decode(cursor, PostSortType.STAR_AVG, Sort.Direction.ASC))
                .containsExactly(4.5, 1760000000000L, 7L);
    }

    @Test
    @DisplayName("정렬 기준이 요청과 다르면 거부한다")
    void rejectsDifferentSort() {
        String cursor = codec.encode(PostSortType.LATEST, Sort.Direction.DESC, List.of(1L, 2L));

        assertInvalid(() -> codec.decode(cursor, PostSortType.ACCURACY, Sort.Direction.DESC));
    }

    @Test
    @DisplayName("정렬 방향이 요청과 다르면 거부한다")
    void rejectsDifferentDirection() {
        String cursor = codec.encode(PostSortType.LATEST, Sort.Direction.DESC, List.of(1L, 2L));

        assertInvalid(() -> codec.decode(cursor, PostSortType.LATEST, Sort.Direction.ASC));
    }

    @Test
    @DisplayName("sort 값 개수가 정렬 키 개수와 다르면 거부한다")
    void rejectsWrongValueCount() {
        String cursor = codec.encode(PostSortType.POPULAR, Sort.Direction.DESC, List.of(1.0, 2L));

        assertInvalid(() -> codec.decode(cursor, PostSortType.POPULAR, Sort.Direction.DESC));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "not base64!", // base64url 아님
                "bm90LWpzb24", // JSON 아님
                "bnVsbA", // JSON null
                "e30" // 빈 객체
            })
    @DisplayName("해석할 수 없는 cursor는 거부한다")
    void rejectsMalformed(String cursor) {
        assertInvalid(() -> codec.decode(cursor, PostSortType.LATEST, Sort.Direction.DESC));
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"abc\"", "null", "true", "99999999999999999999"})
    @DisplayName("sort 값이 숫자가 아니면 ES에 보내지 않고 거부한다")
    void rejectsNonNumericValue(String value) {
        String json = "{\"sort\":\"LATEST\",\"direction\":\"DESC\",\"values\":[" + value + ",1]}";
        String cursor = Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));

        assertInvalid(() -> codec.decode(cursor, PostSortType.LATEST, Sort.Direction.DESC));
    }

    private static void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(SearchServiceException.class, e -> assertThat(e.getCode())
                .isEqualTo(SearchServiceErrorCode.POST_INVALID_CURSOR));
    }
}
