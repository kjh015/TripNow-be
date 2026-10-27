package com.traveler.web.domain.post.adapter;

import com.traveler.common.core.response.PageResponse;
import com.traveler.web.domain.post.client.AdminPostClient;
import com.traveler.web.domain.post.client.dto.response.AdminPostClientResponse;
import com.traveler.web.global.resilience.DownstreamCallExecutor;
import com.traveler.web.global.resilience.DownstreamServiceNames;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminPostClientAdapter {
    private final AdminPostClient adminPostClient;
    private final DownstreamCallExecutor downstreamCallExecutor;

    public PageResponse<AdminPostClientResponse.ListDTO> getPosts(Boolean deleted, Pageable pageable) {
        return downstreamCallExecutor.execute(
                DownstreamServiceNames.POST,
                () -> adminPostClient.getPosts(deleted, pageable).result());
    }

    public AdminPostClientResponse.DetailDTO getPost(Long postId) {
        return downstreamCallExecutor.execute(
                DownstreamServiceNames.POST,
                () -> adminPostClient.getPost(postId).result());
    }

    public AdminPostClientResponse.DeleteDTO deletePost(Long postId) {
        return downstreamCallExecutor.execute(
                DownstreamServiceNames.POST,
                () -> adminPostClient.deletePost(postId).result());
    }

    public AdminPostClientResponse.RestoreDTO restorePost(Long postId) {
        return downstreamCallExecutor.execute(
                DownstreamServiceNames.POST,
                () -> adminPostClient.restorePost(postId).result());
    }

    public AdminPostClientResponse.PermanentDeleteDTO permanentlyDeletePost(Long postId) {
        return downstreamCallExecutor.execute(
                DownstreamServiceNames.POST,
                () -> adminPostClient.permanentlyDeletePost(postId).result());
    }
}
