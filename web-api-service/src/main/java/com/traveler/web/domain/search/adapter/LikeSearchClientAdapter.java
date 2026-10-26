package com.traveler.web.domain.search.adapter;

import com.traveler.common.core.response.PageResponse;
import com.traveler.web.domain.search.client.LikeSearchClient;
import com.traveler.web.domain.search.client.dto.response.LikeSearchClientResponse;
import com.traveler.web.global.resilience.DownstreamCallExecutor;
import com.traveler.web.global.resilience.DownstreamServiceNames;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LikeSearchClientAdapter {
    private final LikeSearchClient likeSearchClient;
    private final DownstreamCallExecutor downstreamCallExecutor;

    public PageResponse<LikeSearchClientResponse.MyDTO> getMyLikePosts(Pageable pageable) {
        return downstreamCallExecutor.execute(
                DownstreamServiceNames.SEARCH,
                () -> likeSearchClient.getMyLikePosts(pageable).result());
    }
}
