package com.traveler.web.domain.post.adapter;

import com.traveler.common.core.response.PageResponse;
import com.traveler.web.domain.post.client.AdminPostClient;
import com.traveler.web.domain.post.client.dto.response.AdminPostClientResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminPostClientAdapter {
    private final AdminPostClient adminPostClient;

    public PageResponse<AdminPostClientResponse.ListDTO> getPosts(Boolean deleted, Pageable pageable) {
        return adminPostClient.getPosts(deleted, pageable).result();
    }

    public AdminPostClientResponse.DetailDTO getPost(Long postId) {
        return adminPostClient.getPost(postId).result();
    }

    public AdminPostClientResponse.DeleteDTO deletePost(Long postId) {
        return adminPostClient.deletePost(postId).result();
    }

    public AdminPostClientResponse.RestoreDTO restorePost(Long postId) {
        return adminPostClient.restorePost(postId).result();
    }

    public AdminPostClientResponse.PermanentDeleteDTO permanentlyDeletePost(Long postId) {
        return adminPostClient.permanentlyDeletePost(postId).result();
    }
}
