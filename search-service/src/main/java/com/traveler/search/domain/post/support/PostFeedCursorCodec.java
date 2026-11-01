package com.traveler.search.domain.post.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.traveler.search.domain.post.enums.PostSortType;
import com.traveler.search.global.exception.SearchServiceException;
import com.traveler.search.global.exception.code.SearchServiceErrorCode;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

/**
 * 피드 cursor를 만들고 읽는다. cursor는 마지막 문서의 ES sort 값과 정렬 조건을 담은 JSON을 base64url로 인코딩한 값이다.
 */
@Component
@RequiredArgsConstructor
public class PostFeedCursorCodec {
    private final ObjectMapper objectMapper;

    private record Payload(PostSortType sort, Sort.Direction direction, List<Object> values) {}

    public String encode(PostSortType sort, Sort.Direction direction, List<Object> sortValues) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(new Payload(sort, direction, sortValues));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("피드 cursor를 만들 수 없습니다.", e);
        }
    }

    /** 요청의 정렬과 다르거나 해석할 수 없는 cursor는 POST_INVALID_CURSOR로 거부한다. */
    public List<Object> decode(String cursor, PostSortType sort, Sort.Direction direction) {
        Payload payload = read(cursor);
        if (payload == null
                || payload.sort() != sort
                || payload.direction() != direction
                || payload.values() == null
                || payload.values().size() != sort.getFeedSortKeyCount()) {
            throw invalidCursor();
        }
        return payload.values().stream().map(PostFeedCursorCodec::toSortValue).toList();
    }

    private Payload read(String cursor) {
        try {
            return objectMapper.readValue(Base64.getUrlDecoder().decode(cursor), Payload.class);
        } catch (IllegalArgumentException | IOException e) {
            throw invalidCursor();
        }
    }

    // 피드 정렬 키는 모두 숫자다. 숫자가 아닌 값이 ES까지 가면 400이 아니라 500이 된다
    private static Object toSortValue(Object value) {
        if (value instanceof Integer || value instanceof Long) {
            return ((Number) value).longValue();
        }
        if (value instanceof Double) {
            return value;
        }
        throw invalidCursor();
    }

    private static SearchServiceException invalidCursor() {
        return new SearchServiceException(SearchServiceErrorCode.POST_INVALID_CURSOR);
    }
}
