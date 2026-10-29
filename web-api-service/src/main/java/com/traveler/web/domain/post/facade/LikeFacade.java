package com.traveler.web.domain.post.facade;

import com.traveler.web.domain.post.adapter.LikeClientAdapter;
import com.traveler.web.domain.post.dto.request.LikeRequest;
import com.traveler.web.domain.post.dto.response.LikeResponse;
import com.traveler.web.domain.post.mapper.LikeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LikeFacade {
    private final LikeClientAdapter likeClientAdapter;
    private final LikeMapper likeMapper;

    public LikeResponse.StatsDTO addLike(LikeRequest.AddDTO dto) {
        return likeMapper.toStatsResponse(likeClientAdapter.addLike(likeMapper.toAddClientRequest(dto)));
    }

    public LikeResponse.StatsDTO removeLike(Long postId) {
        return likeMapper.toStatsResponse(likeClientAdapter.removeLike(postId));
    }
}
