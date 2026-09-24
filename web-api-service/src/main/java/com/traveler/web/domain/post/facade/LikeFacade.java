package com.traveler.web.domain.post.facade;

import com.traveler.web.domain.post.adapter.LikeClientAdapter;
import com.traveler.web.domain.post.dto.request.LikeRequest;
import com.traveler.web.domain.post.mapper.LikeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LikeFacade {
    private final LikeClientAdapter likeClientAdapter;
    private final LikeMapper likeMapper;

    public void addLike(LikeRequest.AddDTO dto) {
        likeClientAdapter.addLike(likeMapper.toAddClientRequest(dto));
    }

    public void removeLike(Long postId) {
        likeClientAdapter.removeLike(postId);
    }
}
