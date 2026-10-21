package com.traveler.search.domain.like.service;

import static com.traveler.search.testsupport.fixture.SearchFixture.READABLE_TIMESTAMP;
import static com.traveler.search.testsupport.fixture.SearchFixture.nextId;
import static org.assertj.core.api.Assertions.assertThat;

import com.traveler.search.domain.like.dto.message.LikeSearchMessage;
import com.traveler.search.domain.like.repository.LikeDocumentRepository;
import com.traveler.search.testsupport.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

class LikeSearchCommandServiceIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private LikeSearchCommandService commandService;

    @Autowired
    private LikeDocumentRepository likeDocumentRepository;

    @Test
    @DisplayName("추가·삭제 메시지를 각각 두 번 받아도 결과는 한 번 받은 것과 같다")
    void addAndRemoveAreIdempotent() {
        long likeId = nextId();
        long memberId = nextId();
        LikeSearchMessage.AddedDTO added = new LikeSearchMessage.AddedDTO(likeId, 10L, memberId, READABLE_TIMESTAMP);

        commandService.addDocument(added);
        commandService.addDocument(added);
        assertThat(likeDocumentRepository.findById(likeId)).isPresent();

        commandService.removeDocument(new LikeSearchMessage.RemovedDTO(likeId));
        commandService.removeDocument(new LikeSearchMessage.RemovedDTO(likeId));
        assertThat(likeDocumentRepository.findById(likeId)).isEmpty();
        assertThat(likeDocumentRepository.findByMemberId(memberId, PageRequest.of(0, 10)))
                .isEmpty();
    }
}
