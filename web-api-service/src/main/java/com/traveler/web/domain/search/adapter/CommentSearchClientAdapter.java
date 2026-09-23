package com.traveler.web.domain.search.adapter;

import com.traveler.common.core.response.PageResponse;
import com.traveler.web.domain.search.client.CommentSearchClient;
import com.traveler.web.domain.search.client.dto.response.CommentSearchClientResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CommentSearchClientAdapter {
    private final CommentSearchClient commentSearchClient;

    public PageResponse<CommentSearchClientResponse.ListDTO> getComments(Long postId, Pageable pageable) {
        return commentSearchClient.getComments(postId, pageable).result();
    }

    public PageResponse<CommentSearchClientResponse.MyDTO> getMyComments(Pageable pageable) {
        return commentSearchClient.getMyComments(pageable).result();
    }
}
