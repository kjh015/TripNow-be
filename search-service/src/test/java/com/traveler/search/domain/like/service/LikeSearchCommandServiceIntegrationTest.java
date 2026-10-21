package com.traveler.search.domain.like.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.traveler.search.domain.like.dto.message.LikeSearchMessage;
import com.traveler.search.domain.like.repository.LikeDocumentRepository;
import com.traveler.search.support.IntegrationTestSupport;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

class LikeSearchCommandServiceIntegrationTest extends IntegrationTestSupport {

    private static final AtomicLong IDS = new AtomicLong(System.currentTimeMillis());

    @Autowired
    private LikeSearchCommandService commandService;

    @Autowired
    private LikeDocumentRepository likeDocumentRepository;

    @Test
    @DisplayName("추가·삭제 메시지를 각각 두 번 받아도 결과는 한 번 받은 것과 같다")
    void addAndRemoveAreIdempotent() {
        long likeId = IDS.incrementAndGet();
        long memberId = IDS.incrementAndGet();
        LikeSearchMessage.AddedDTO added = new LikeSearchMessage.AddedDTO(likeId, 10L, memberId, now());

        commandService.addDocument(added);
        commandService.addDocument(added);
        assertThat(likeDocumentRepository.findById(likeId)).isPresent();

        commandService.removeDocument(new LikeSearchMessage.RemovedDTO(likeId));
        commandService.removeDocument(new LikeSearchMessage.RemovedDTO(likeId));
        assertThat(likeDocumentRepository.findById(likeId)).isEmpty();
        assertThat(likeDocumentRepository.findByMemberId(memberId, PageRequest.of(0, 10)))
                .isEmpty();
    }

    // 부분 갱신은 ES 클라이언트가 Instant를 그대로 직렬화하는데, Spring Data ES는 소수 6자리 형식만 다시 읽는다
    // (소수 0·3·9자리는 조회 시 ConversionException, 보고서 "추가 발견"). 여기서는 읽을 수 있는 형식으로 고정한다
    private static Instant now() {
        return Instant.parse("2026-10-17T09:00:00.123456Z");
    }
}
