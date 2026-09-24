package com.traveler.web.domain.post.facade;

import com.traveler.web.domain.post.adapter.PostClientAdapter;
import com.traveler.web.domain.post.dto.request.PostRequest;
import com.traveler.web.domain.post.dto.response.PostResponse;
import com.traveler.web.domain.post.mapper.PostMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PostFacade {
    private final PostClientAdapter postClientAdapter;
    private final PostMapper postMapper;

    public PostResponse.CreateDTO createPost(PostRequest.CreateDTO dto) {
        return postMapper.toCreateResponse(postClientAdapter.createPost(postMapper.toCreateClientRequest(dto)));
    }

    public PostResponse.UpdateDTO updatePost(Long postId, PostRequest.UpdateDTO dto) {
        return postMapper.toUpdateResponse(postClientAdapter.updatePost(postId, postMapper.toUpdateClientRequest(dto)));
    }

    public PostResponse.DeleteDTO deletePost(Long postId) {
        return postMapper.toDeleteResponse(postClientAdapter.deletePost(postId));
    }

    public PostResponse.PresignedUrlDTO getPresignedUrl(String fileName, String contentType) {
        return postMapper.toPresignedUrlResponse(postClientAdapter.getPresignedUrl(fileName, contentType));
    }
}
