package com.traveler.web.domain.post.facade;

import com.traveler.common.core.response.PageResponse;
import com.traveler.web.domain.post.adapter.AdminPostClientAdapter;
import com.traveler.web.domain.post.dto.response.AdminPostResponse;
import com.traveler.web.domain.post.mapper.PostMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminPostFacade {
    private final AdminPostClientAdapter adminPostClientAdapter;
    private final PostMapper postMapper;

    public PageResponse<AdminPostResponse.ListDTO> getPosts(Boolean deleted, Pageable pageable) {
        return adminPostClientAdapter.getPosts(deleted, pageable).map(postMapper::toAdminListResponse);
    }

    public AdminPostResponse.DetailDTO getPost(Long postId) {
        return postMapper.toAdminDetailResponse(adminPostClientAdapter.getPost(postId));
    }

    public AdminPostResponse.DeleteDTO deletePost(Long postId) {
        return postMapper.toAdminDeleteResponse(adminPostClientAdapter.deletePost(postId));
    }

    public AdminPostResponse.RestoreDTO restorePost(Long postId) {
        return postMapper.toAdminRestoreResponse(adminPostClientAdapter.restorePost(postId));
    }

    public AdminPostResponse.PermanentDeleteDTO permanentlyDeletePost(Long postId) {
        return postMapper.toAdminPermanentDeleteResponse(adminPostClientAdapter.permanentlyDeletePost(postId));
    }
}
