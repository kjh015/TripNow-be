package com.traveler.web.domain.search.adapter;

import com.traveler.common.core.response.PageResponse;
import com.traveler.web.domain.search.client.PostSearchClient;
import com.traveler.web.domain.search.client.dto.response.PostSearchClientResponse;
import com.traveler.web.domain.search.dto.request.PostSearchRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PostSearchClientAdapter {
    private final PostSearchClient postSearchClient;

    public PageResponse<PostSearchClientResponse.SearchDTO> search(PostSearchRequest.SearchDTO dto) {
        return postSearchClient
                .search(
                        dto.keyword(),
                        dto.category(),
                        dto.region(),
                        dto.sort(),
                        dto.direction(),
                        dto.page(),
                        dto.size())
                .result();
    }

    public PostSearchClientResponse.DetailDTO getPost(Long postId) {
        return postSearchClient.getPost(postId).result();
    }

    public PostSearchClientResponse.AutocompleteDTO autocomplete(String keyword) {
        return postSearchClient.autocomplete(keyword).result();
    }

    public PageResponse<PostSearchClientResponse.MyDTO> getMyPosts(Pageable pageable) {
        return postSearchClient.getMyPosts(pageable).result();
    }
}
