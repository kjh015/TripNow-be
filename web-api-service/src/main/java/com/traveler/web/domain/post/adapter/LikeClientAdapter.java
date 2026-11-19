package com.traveler.web.domain.post.adapter;

import com.traveler.web.domain.post.client.LikeClient;
import com.traveler.web.domain.post.client.dto.request.LikeClientRequest;
import com.traveler.web.domain.post.client.dto.response.LikeClientResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LikeClientAdapter {
    private final LikeClient likeClient;

    public LikeClientResponse.StatsDTO addLike(LikeClientRequest.AddDTO dto) {
        return likeClient.addLike(dto).result();
    }

    public LikeClientResponse.StatsDTO removeLike(Long postId) {
        return likeClient.removeLike(postId).result();
    }
}
