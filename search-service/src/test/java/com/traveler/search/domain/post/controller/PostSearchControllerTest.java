package com.traveler.search.domain.post.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.traveler.search.domain.post.service.PostSearchQueryService;
import com.traveler.search.global.exception.SearchServiceException;
import com.traveler.search.global.exception.code.SearchServiceErrorCode;
import com.traveler.search.global.handler.SearchServiceExceptionAdvice;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PostSearchControllerTest {

    private final PostSearchQueryService queryService = mock(PostSearchQueryService.class);

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new PostSearchController(queryService))
            .setControllerAdvice(new SearchServiceExceptionAdvice())
            .build();

    @Test
    @DisplayName("잘못된 cursor는 400 POST400_2로 응답한다")
    void invalidCursor() throws Exception {
        given(queryService.getFeed(any()))
                .willThrow(new SearchServiceException(SearchServiceErrorCode.POST_INVALID_CURSOR));

        mockMvc.perform(get("/v1/search/posts/feed").param("cursor", "broken"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("POST400_2"));
    }

    @Test
    @DisplayName("조회 크기가 50을 넘으면 400으로 응답한다")
    void sizeOverLimit() throws Exception {
        mockMvc.perform(get("/v1/search/posts/feed").param("size", "51")).andExpect(status().isBadRequest());
    }
}
