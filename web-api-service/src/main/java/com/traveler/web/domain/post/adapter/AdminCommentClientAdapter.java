package com.traveler.web.domain.post.adapter;

import com.traveler.common.core.response.PageResponse;
import com.traveler.web.domain.post.client.AdminCommentClient;
import com.traveler.web.domain.post.client.dto.response.AdminCommentClientResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminCommentClientAdapter {
    private final AdminCommentClient adminCommentClient;

    public PageResponse<AdminCommentClientResponse.ListDTO> getComments(
            Long postId, Boolean deleted, Pageable pageable) {
        return adminCommentClient.getComments(postId, deleted, pageable).result();
    }

    public AdminCommentClientResponse.DeleteDTO deleteComment(Long commentId) {
        return adminCommentClient.deleteComment(commentId).result();
    }

    public AdminCommentClientResponse.RestoreDTO restoreComment(Long commentId) {
        return adminCommentClient.restoreComment(commentId).result();
    }
}
