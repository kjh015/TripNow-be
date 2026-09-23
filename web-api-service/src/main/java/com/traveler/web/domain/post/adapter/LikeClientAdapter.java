package com.traveler.web.domain.post.adapter;

import com.traveler.web.domain.post.client.LikeClient;
import com.traveler.web.domain.post.client.dto.request.LikeClientRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LikeClientAdapter {
    private final LikeClient likeClient;

    public void addLike(LikeClientRequest.AddDTO dto) {
        likeClient.addLike(dto);
    }

    public void removeLike(Long postId) {
        likeClient.removeLike(postId);
    }
}
