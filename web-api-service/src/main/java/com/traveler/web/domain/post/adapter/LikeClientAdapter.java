package com.traveler.web.domain.post.adapter;

import com.traveler.web.domain.post.client.LikeClient;
import com.traveler.web.domain.post.client.dto.request.LikeClientRequest;
import com.traveler.web.global.resilience.DownstreamCallExecutor;
import com.traveler.web.global.resilience.DownstreamServiceNames;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LikeClientAdapter {
    private final LikeClient likeClient;
    private final DownstreamCallExecutor downstreamCallExecutor;

    public void addLike(LikeClientRequest.AddDTO dto) {
        downstreamCallExecutor.execute(DownstreamServiceNames.POST, () -> likeClient.addLike(dto));
    }

    public void removeLike(Long postId) {
        downstreamCallExecutor.execute(DownstreamServiceNames.POST, () -> likeClient.removeLike(postId));
    }
}
