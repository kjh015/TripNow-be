package com.traveler.search.testsupport.fixture;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/** search-service 통합 테스트가 함께 쓰는 데이터. */
public final class SearchFixture {

    /**
     * 부분 갱신 메시지에 넣을 시각.
     * 부분 갱신은 ES 클라이언트가 Instant를 그대로 직렬화하는데, Spring Data ES는 소수 6자리 형식만 다시 읽는다
     * (소수 0·3·9자리는 조회 시 ConversionException, 작업기록 WP-C 추가 발견). 테스트에서는 읽을 수 있는 형식으로 고정한다.
     */
    public static final Instant READABLE_TIMESTAMP = Instant.parse("2026-10-17T09:00:00.123456Z");

    // 컨테이너(ES)를 테스트 클래스끼리 공유하므로, 현재 시각에서 시작해 이전 실행·다른 테스트와 ID가 겹치지 않게 한다
    private static final AtomicLong IDS = new AtomicLong(System.currentTimeMillis());

    private SearchFixture() {}

    /** 다른 테스트와 겹치지 않는 문서 ID */
    public static long nextId() {
        return IDS.incrementAndGet();
    }
}
