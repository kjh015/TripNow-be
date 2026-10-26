package com.traveler.web.domain.post.adapter;

import com.traveler.web.domain.post.client.PostClient;
import com.traveler.web.domain.post.client.dto.request.PostClientRequest;
import com.traveler.web.domain.post.client.dto.response.PostClientResponse;
import com.traveler.web.global.resilience.DownstreamCallExecutor;
import com.traveler.web.global.resilience.DownstreamServiceNames;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PostClientAdapter {
    private final PostClient postClient;
    private final DownstreamCallExecutor downstreamCallExecutor;

    public PostClientResponse.CreateDTO createPost(PostClientRequest.CreateDTO dto) {
        return downstreamCallExecutor.execute(
                DownstreamServiceNames.POST, () -> postClient.createPost(dto).result());
    }

    public PostClientResponse.UpdateDTO updatePost(Long postId, PostClientRequest.UpdateDTO dto) {
        return downstreamCallExecutor.execute(
                DownstreamServiceNames.POST,
                () -> postClient.updatePost(postId, dto).result());
    }

    public PostClientResponse.DeleteDTO deletePost(Long postId) {
        return downstreamCallExecutor.execute(
                DownstreamServiceNames.POST, () -> postClient.deletePost(postId).result());
    }

    public PostClientResponse.PresignedUrlDTO getPresignedUrl(String fileName, String contentType) {
        return downstreamCallExecutor.execute(
                DownstreamServiceNames.POST,
                () -> postClient.getPresignedUrl(fileName, contentType).result());
    }
}
