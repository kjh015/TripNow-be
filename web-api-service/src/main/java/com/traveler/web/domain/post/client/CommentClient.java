package com.traveler.web.domain.post.client;

import com.traveler.common.core.response.ApiResponse;
import com.traveler.web.domain.post.client.dto.request.CommentClientRequest;
import com.traveler.web.domain.post.client.dto.response.CommentClientResponse;
import com.traveler.web.global.feign.config.FeignClientConfig;
import com.traveler.web.global.resilience.DownstreamServiceNames;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(
        name = DownstreamServiceNames.POST,
        contextId = "CommentClient",
        path = "/v1/comments",
        configuration = FeignClientConfig.class)
public interface CommentClient {

    @PostMapping
    ApiResponse<CommentClientResponse.CreateDTO> createComment(@RequestBody CommentClientRequest.CreateDTO dto);

    @PatchMapping("/{commentId}")
    ApiResponse<CommentClientResponse.UpdateDTO> updateComment(
            @PathVariable Long commentId, @RequestBody CommentClientRequest.UpdateDTO dto);

    @DeleteMapping("/{commentId}")
    ApiResponse<CommentClientResponse.DeleteDTO> deleteComment(@PathVariable Long commentId);
}
