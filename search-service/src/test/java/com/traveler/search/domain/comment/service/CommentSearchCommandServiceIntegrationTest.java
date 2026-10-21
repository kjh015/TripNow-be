package com.traveler.search.domain.comment.service;

import static com.traveler.search.testsupport.fixture.SearchFixture.READABLE_TIMESTAMP;
import static com.traveler.search.testsupport.fixture.SearchFixture.nextId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import com.traveler.search.domain.comment.document.CommentDocument;
import com.traveler.search.domain.comment.dto.message.CommentSearchMessage;
import com.traveler.search.domain.comment.repository.CommentDocumentRepository;
import com.traveler.search.testsupport.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.NestedExceptionUtils;

class CommentSearchCommandServiceIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private CommentSearchCommandService commandService;

    @Autowired
    private CommentDocumentRepository commentDocumentRepository;

    @Test
    @DisplayName("생성 메시지를 두 번 받아도 문서는 하나이고, 부분 갱신은 바뀐 필드만 반영한다")
    void createTwiceThenUpdate() {
        long commentId = nextId();
        CommentSearchMessage.CreatedDTO created =
                new CommentSearchMessage.CreatedDTO(commentId, 10L, 1L, "처음 댓글", 5, READABLE_TIMESTAMP);

        commandService.createDocument(created);
        commandService.createDocument(created);
        commandService.updateDocument(new CommentSearchMessage.UpdatedDTO(commentId, null, 3, READABLE_TIMESTAMP));

        CommentDocument doc = commentDocumentRepository.findById(commentId).orElseThrow();
        assertThat(doc.getContent()).isEqualTo("처음 댓글");
        assertThat(doc.getStar()).isEqualTo(3);
    }

    @Test
    @DisplayName("삭제 후 도착한 갱신 이벤트는 404로 실패하고, 삭제를 두 번 받아도 예외가 없다")
    void updateAfterDelete() {
        long commentId = nextId();
        commandService.createDocument(
                new CommentSearchMessage.CreatedDTO(commentId, 10L, 1L, "댓글", 4, READABLE_TIMESTAMP));

        commandService.deleteDocument(new CommentSearchMessage.DeletedDTO(commentId));
        commandService.deleteDocument(new CommentSearchMessage.DeletedDTO(commentId));

        assertThatThrownBy(() -> commandService.updateDocument(
                        new CommentSearchMessage.UpdatedDTO(commentId, "늦은 수정", 1, READABLE_TIMESTAMP)))
                .satisfies(ex -> {
                    Throwable cause = NestedExceptionUtils.getMostSpecificCause(ex);
                    assertThat(cause).isInstanceOf(ElasticsearchException.class);
                    assertThat(((ElasticsearchException) cause).status()).isEqualTo(404);
                });
        assertThat(commentDocumentRepository.findById(commentId)).isEmpty();
    }
}
