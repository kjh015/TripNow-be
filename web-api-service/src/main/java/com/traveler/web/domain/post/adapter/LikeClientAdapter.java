package com.traveler.web.domain.post.adapter;

import com.traveler.web.domain.post.client.LikeClient;
import com.traveler.web.domain.post.client.dto.request.LikeClientRequest;
import com.traveler.web.domain.post.client.dto.response.LikeClientResponse;
import com.traveler.web.global.resilience.DownstreamCallExecutor;
import com.traveler.web.global.resilience.DownstreamServiceNames;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LikeClientAdapter {
    private final LikeClient likeClient;
    private final DownstreamCallExecutor downstreamCallExecutor;

    public LikeClientResponse.StatsDTO addLike(LikeClientRequest.AddDTO dto) {
        return downstreamCallExecutor.execute(
                DownstreamServiceNames.POST, () -> likeClient.addLike(dto).result());
    }

    public LikeClientResponse.StatsDTO removeLike(Long postId) {
        return downstreamCallExecutor.execute(
                DownstreamServiceNames.POST, () -> likeClient.removeLike(postId).result());
    }
}
