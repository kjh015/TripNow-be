package com.traveler.web.domain.search.adapter;

import com.traveler.common.core.response.PageResponse;
import com.traveler.web.domain.search.client.CommentSearchClient;
import com.traveler.web.domain.search.client.dto.response.CommentSearchClientResponse;
import com.traveler.web.global.resilience.DownstreamCallExecutor;
import com.traveler.web.global.resilience.DownstreamServiceNames;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CommentSearchClientAdapter {
    private final CommentSearchClient commentSearchClient;
    private final DownstreamCallExecutor downstreamCallExecutor;

    public PageResponse<CommentSearchClientResponse.ListDTO> getComments(Long postId, Pageable pageable) {
        return downstreamCallExecutor.execute(
                DownstreamServiceNames.SEARCH,
                () -> commentSearchClient.getComments(postId, pageable).result());
    }

    public PageResponse<CommentSearchClientResponse.MyDTO> getMyComments(Pageable pageable) {
        return downstreamCallExecutor.execute(
                DownstreamServiceNames.SEARCH,
                () -> commentSearchClient.getMyComments(pageable).result());
    }
}
