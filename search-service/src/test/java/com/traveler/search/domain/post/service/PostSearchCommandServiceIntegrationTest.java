package com.traveler.search.domain.post.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import com.traveler.search.domain.post.document.PostDocument;
import com.traveler.search.domain.post.dto.message.PostSearchMessage;
import com.traveler.search.domain.post.repository.PostDocumentRepository;
import com.traveler.search.global.util.HangulUtils;
import com.traveler.search.support.IntegrationTestSupport;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.NestedExceptionUtils;

class PostSearchCommandServiceIntegrationTest extends IntegrationTestSupport {

    // 컨테이너를 테스트 클래스끼리 공유하므로 문서 ID가 겹치지 않게 한다
    private static final AtomicLong IDS = new AtomicLong(System.currentTimeMillis());

    @Autowired
    private PostSearchCommandService commandService;

    @Autowired
    private PostDocumentRepository postDocumentRepository;

    @Test
    @DisplayName("생성 메시지로 문서를 만들고 초성 필드와 통계 기본값을 채운다")
    void createDocument() {
        long postId = IDS.incrementAndGet();

        commandService.createDocument(created(postId, "경주 황리단길"));

        PostDocument doc = postDocumentRepository.findById(postId).orElseThrow();
        assertThat(doc.getTitle()).isEqualTo("경주 황리단길");
        assertThat(doc.getTitleChosung()).isEqualTo(HangulUtils.extractChosung("경주 황리단길"));
        assertThat(doc.getCategory()).isEqualTo("FOOD");
        assertThat(doc.getLikeCount()).isZero();
        assertThat(doc.getImages()).hasSize(1);
    }

    @Test
    @DisplayName("같은 생성 메시지를 두 번 받아도 문서는 하나이고 내용이 같다")
    void createDocumentTwice() {
        long postId = IDS.incrementAndGet();
        long memberId = IDS.incrementAndGet();
        PostSearchMessage.CreatedDTO message = created(postId, memberId, "중복 수신");

        commandService.createDocument(message);
        commandService.createDocument(message);

        assertThat(postDocumentRepository.findAllByIdIn(List.of(postId))).hasSize(1);
        assertThat(postDocumentRepository.findById(postId).orElseThrow().getTitle())
                .isEqualTo("중복 수신");
    }

    @Test
    @DisplayName("부분 갱신은 메시지에 담긴 필드만 바꾸고 나머지는 유지한다")
    void updateDocumentPartially() {
        long postId = IDS.incrementAndGet();
        commandService.createDocument(created(postId, "원래 제목"));

        commandService.updateDocument(
                new PostSearchMessage.UpdatedDTO(postId, "바뀐 제목", null, null, null, null, null, now(), null));

        PostDocument doc = postDocumentRepository.findById(postId).orElseThrow();
        assertThat(doc.getTitle()).isEqualTo("바뀐 제목");
        assertThat(doc.getContent()).isEqualTo("본문");
        assertThat(doc.getRegion()).isEqualTo("GYEONGGI");
    }

    @Test
    @DisplayName("통계 갱신은 카운트와 인기 점수를 함께 저장하고, 같은 메시지를 다시 받아도 값이 같다")
    void updateStatisticsIsIdempotent() {
        long postId = IDS.incrementAndGet();
        commandService.createDocument(created(postId, "통계"));
        PostSearchMessage.StatUpdatedDTO stat = new PostSearchMessage.StatUpdatedDTO(postId, 4.0, 2L, 3L, 10L);

        commandService.updateStatistics(stat);
        commandService.updateStatistics(stat);

        PostDocument doc = postDocumentRepository.findById(postId).orElseThrow();
        assertThat(doc.getLikeCount()).isEqualTo(3L);
        assertThat(doc.getCommentCount()).isEqualTo(2L);
        // 3*5 + 2*3 + 10*0.1 + 4.0*10
        assertThat(doc.getPopularityScoreValue()).isEqualTo(62.0);
    }

    @Test
    @DisplayName("같은 삭제 메시지를 두 번 받아도 예외 없이 문서가 없는 상태로 끝난다")
    void deleteDocumentTwice() {
        long postId = IDS.incrementAndGet();
        commandService.createDocument(created(postId, "삭제"));

        commandService.deleteDocument(new PostSearchMessage.DeletedDTO(postId));
        commandService.deleteDocument(new PostSearchMessage.DeletedDTO(postId));

        assertThat(postDocumentRepository.findById(postId)).isEmpty();
    }

    @Test
    @DisplayName("삭제 후 도착한 갱신·통계 이벤트는 404로 실패하고 문서를 되살리지 않는다")
    void updateAfterDeleteFails() {
        long postId = IDS.incrementAndGet();
        commandService.createDocument(created(postId, "삭제 후 갱신"));
        commandService.deleteDocument(new PostSearchMessage.DeletedDTO(postId));

        assertDocumentMissing(() -> commandService.updateDocument(
                new PostSearchMessage.UpdatedDTO(postId, "늦게 온 갱신", null, null, null, null, null, null, null)));
        assertDocumentMissing(
                () -> commandService.updateStatistics(new PostSearchMessage.StatUpdatedDTO(postId, 1.0, 1L, 1L, 1L)));

        assertThat(postDocumentRepository.findById(postId)).isEmpty();
    }

    private static void assertDocumentMissing(ThrowingCallable call) {
        assertThatThrownBy(call).satisfies(ex -> {
            Throwable cause = NestedExceptionUtils.getMostSpecificCause(ex);
            assertThat(cause).isInstanceOf(ElasticsearchException.class);
            assertThat(((ElasticsearchException) cause).status()).isEqualTo(404);
        });
    }

    private static PostSearchMessage.CreatedDTO created(long postId, String title) {
        return created(postId, 1L, title);
    }

    private static PostSearchMessage.CreatedDTO created(long postId, long memberId, String title) {
        return new PostSearchMessage.CreatedDTO(
                postId,
                memberId,
                title,
                "본문",
                "FOOD",
                "GYEONGGI",
                "황리단길",
                "경상북도 경주시",
                now(),
                List.of(new PostSearchMessage.ImageInfo("posts/" + memberId + "/a.png", 0)));
    }

    // 부분 갱신은 ES 클라이언트가 Instant를 그대로 직렬화하는데, Spring Data ES는 소수 6자리 형식만 다시 읽는다
    // (소수 0·3·9자리는 조회 시 ConversionException, 보고서 "추가 발견"). 여기서는 읽을 수 있는 형식으로 고정한다
    private static Instant now() {
        return Instant.parse("2026-10-17T09:00:00.123456Z");
    }
}
