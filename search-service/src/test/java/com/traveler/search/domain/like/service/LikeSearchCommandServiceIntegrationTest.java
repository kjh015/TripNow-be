package com.traveler.search.domain.like.service;

import static com.traveler.search.testsupport.fixture.SearchFixture.READABLE_TIMESTAMP;
import static com.traveler.search.testsupport.fixture.SearchFixture.nextId;
import static org.assertj.core.api.Assertions.assertThat;

import com.traveler.search.domain.like.dto.message.LikeSearchMessage;
import com.traveler.search.domain.like.repository.LikeDocumentRepository;
import com.traveler.search.testsupport.IntegrationTestSupport;
import java.util.List;
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

    @Test
    @DisplayName("묶음 삭제는 지정한 문서만 지우고, 없는 문서가 섞여 있거나 두 번 받아도 예외가 없다")
    void removeDocuments() {
        long first = nextId();
        long second = nextId();
        long kept = nextId();
        for (long likeId : List.of(first, second, kept)) {
            commandService.addDocument(new LikeSearchMessage.AddedDTO(likeId, 10L, nextId(), READABLE_TIMESTAMP));
        }
        LikeSearchMessage.BatchRemovedDTO batch =
                new LikeSearchMessage.BatchRemovedDTO(10L, List.of(first, second, nextId()));

        commandService.removeDocuments(batch);
        commandService.removeDocuments(batch);

        assertThat(likeDocumentRepository.findById(first)).isEmpty();
        assertThat(likeDocumentRepository.findById(second)).isEmpty();
        assertThat(likeDocumentRepository.findById(kept)).isPresent();
    }
}
