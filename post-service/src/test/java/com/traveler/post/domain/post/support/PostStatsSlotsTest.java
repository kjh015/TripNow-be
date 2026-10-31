package com.traveler.post.domain.post.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.traveler.post.domain.post.dto.result.PostStatsSummary;
import com.traveler.post.domain.post.repository.PostStatsRepository;
import com.traveler.post.global.exception.PostServiceException;
import com.traveler.post.global.exception.code.PostServiceErrorCode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PostStatsSlotsTest {

    @Mock
    private PostStatsRepository postStatsRepository;

    @InjectMocks
    private PostStatsSlots postStatsSlots;

    @Test
    @DisplayName("통계 행이 없는 게시글은 0 통계로 채워, 요청한 게시글 ID가 모두 들어 있다")
    void summarizeByPostIdFillsMissing() {
        List<Long> postIds = List.of(1L, 2L);
        given(postStatsRepository.summarizeAll(postIds)).willReturn(List.of(new PostStatsSummary(1L, 3L, 2L, 9L)));

        Map<Long, PostStatsSummary> summaries = postStatsSlots.summarizeByPostId(postIds);

        assertThat(summaries).containsOnlyKeys(1L, 2L);
        assertThat(summaries.get(1L).starAvg()).isEqualTo(4.5);
        assertThat(summaries.get(2L)).isEqualTo(PostStatsSummary.empty(2L));
    }

    @Test
    @DisplayName("고른 슬롯이 없으면(게시글 없음) POST_NOT_FOUND")
    void randomSlotMissing() {
        given(postStatsRepository.findSlotWithLock(eq(10L), anyInt())).willReturn(Optional.empty());

        assertThatThrownBy(() -> postStatsSlots.getRandom(10L))
                .isInstanceOf(PostServiceException.class)
                .extracting("code")
                .isEqualTo(PostServiceErrorCode.POST_NOT_FOUND);
    }
}
