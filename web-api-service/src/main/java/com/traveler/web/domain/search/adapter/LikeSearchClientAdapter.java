package com.traveler.web.domain.search.adapter;

import com.traveler.common.core.response.PageResponse;
import com.traveler.web.domain.search.client.LikeSearchClient;
import com.traveler.web.domain.search.client.dto.response.LikeSearchClientResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LikeSearchClientAdapter {
    private final LikeSearchClient likeSearchClient;

    public PageResponse<LikeSearchClientResponse.MyDTO> getMyLikePosts(Pageable pageable) {
        return likeSearchClient.getMyLikePosts(pageable).result();
    }
}
