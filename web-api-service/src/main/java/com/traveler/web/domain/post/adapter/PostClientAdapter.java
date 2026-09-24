package com.traveler.web.domain.post.adapter;

import com.traveler.web.domain.post.client.PostClient;
import com.traveler.web.domain.post.client.dto.request.PostClientRequest;
import com.traveler.web.domain.post.client.dto.response.PostClientResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PostClientAdapter {
    private final PostClient postClient;

    public PostClientResponse.CreateDTO createPost(PostClientRequest.CreateDTO dto) {
        return postClient.createPost(dto).result();
    }

    public PostClientResponse.UpdateDTO updatePost(Long postId, PostClientRequest.UpdateDTO dto) {
        return postClient.updatePost(postId, dto).result();
    }

    public PostClientResponse.DeleteDTO deletePost(Long postId) {
        return postClient.deletePost(postId).result();
    }

    public PostClientResponse.PresignedUrlDTO getPresignedUrl(String fileName, String contentType) {
        return postClient.getPresignedUrl(fileName, contentType).result();
    }
}
