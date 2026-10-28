package com.traveler.web.domain.post.client;

import com.traveler.common.core.response.ApiResponse;
import com.traveler.web.domain.post.client.dto.request.LikeClientRequest;
import com.traveler.web.domain.post.client.dto.response.LikeClientResponse;
import com.traveler.web.global.feign.config.FeignClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(
        name = "post-service",
        contextId = "LikeClient",
        path = "/v1/likes",
        configuration = FeignClientConfig.class)
public interface LikeClient {
    @PostMapping
    ApiResponse<LikeClientResponse.StatsDTO> addLike(@RequestBody LikeClientRequest.AddDTO dto);

    @DeleteMapping
    ApiResponse<LikeClientResponse.StatsDTO> removeLike(@RequestParam Long postId);
}
