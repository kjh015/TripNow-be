package com.traveler.web.domain.search.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.traveler.common.core.response.PageResponse;
import com.traveler.web.domain.member.adapter.MemberClientAdapter;
import com.traveler.web.domain.search.adapter.PostSearchClientAdapter;
import com.traveler.web.domain.search.client.dto.response.PostSearchClientResponse;
import com.traveler.web.domain.search.dto.request.PostSearchRequest;
import com.traveler.web.domain.search.dto.response.PostSearchResponse;
import com.traveler.web.domain.search.mapper.PostSearchMapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PostSearchFacadeTest {

    @Mock
    private PostSearchClientAdapter postSearchClientAdapter;

    @Mock
    private MemberClientAdapter memberClientAdapter;

    private PostSearchFacade facade;

    @BeforeEach
    void setUp() {
        // 매퍼는 MapStruct가 만든 진짜 구현체를 쓴다
        facade = new PostSearchFacade(
                postSearchClientAdapter, Mappers.getMapper(PostSearchMapper.class), memberClientAdapter);
    }

    @Test
    @DisplayName("검색 결과의 작성자 ID를 모아 한 번에 닉네임을 조회하고, 게시글마다 작성자 닉네임을 붙인다")
    void searchCombinesNicknames() {
        given(postSearchClientAdapter.search(any())).willReturn(page(post(1L, 10L), post(2L, 20L), post(3L, 10L)));
        given(memberClientAdapter.getNicknameMap(Set.of(10L, 20L)))
                .willReturn(Map.of(10L, "여행자", 20L, MemberClientAdapter.DEFAULT_NICKNAME));

        PageResponse<PostSearchResponse.ListDTO> result = facade.search(searchRequest());

        verify(memberClientAdapter).getNicknameMap(Set.of(10L, 20L));
        assertThat(result.content())
                .extracting(PostSearchResponse.ListDTO::postId, PostSearchResponse.ListDTO::memberNickname)
                .containsExactly(tuple(1L, "여행자"), tuple(2L, MemberClientAdapter.DEFAULT_NICKNAME), tuple(3L, "여행자"));
        assertThat(result.totalElements()).isEqualTo(3);
    }

    @Test
    @DisplayName("검색 결과가 비어 있어도 빈 페이지를 돌려준다")
    void emptySearch() {
        given(postSearchClientAdapter.search(any())).willReturn(page());
        given(memberClientAdapter.getNicknameMap(Set.of())).willReturn(Map.of());

        assertThat(facade.search(searchRequest()).content()).isEmpty();
    }

    private static PostSearchRequest.SearchDTO searchRequest() {
        return new PostSearchRequest.SearchDTO("맛집", null, null, null, null, 0, 10);
    }

    private static PostSearchClientResponse.SearchDTO post(Long postId, Long memberId) {
        return new PostSearchClientResponse.SearchDTO(
                postId, memberId, "제목" + postId, "FOOD", "SEOUL", 4.0, 1L, 1L, 1L, 1.0, null);
    }

    @SafeVarargs
    private static PageResponse<PostSearchClientResponse.SearchDTO> page(PostSearchClientResponse.SearchDTO... posts) {
        return new PageResponse<>(List.of(posts), 0, 10, posts.length, 1, true, true);
    }
}
