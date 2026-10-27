package com.traveler.web.domain.post.adapter;

import com.traveler.common.core.response.PageResponse;
import com.traveler.web.domain.post.client.AdminCommentClient;
import com.traveler.web.domain.post.client.dto.response.AdminCommentClientResponse;
import com.traveler.web.global.resilience.DownstreamCallExecutor;
import com.traveler.web.global.resilience.DownstreamServiceNames;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminCommentClientAdapter {
    private final AdminCommentClient adminCommentClient;
    private final DownstreamCallExecutor downstreamCallExecutor;

    public PageResponse<AdminCommentClientResponse.ListDTO> getComments(
            Long postId, Boolean deleted, Pageable pageable) {
        return downstreamCallExecutor.execute(
                DownstreamServiceNames.POST,
                () -> adminCommentClient.getComments(postId, deleted, pageable).result());
    }

    public AdminCommentClientResponse.DeleteDTO deleteComment(Long commentId) {
        return downstreamCallExecutor.execute(
                DownstreamServiceNames.POST,
                () -> adminCommentClient.deleteComment(commentId).result());
    }

    public AdminCommentClientResponse.RestoreDTO restoreComment(Long commentId) {
        return downstreamCallExecutor.execute(
                DownstreamServiceNames.POST,
                () -> adminCommentClient.restoreComment(commentId).result());
    }
}
