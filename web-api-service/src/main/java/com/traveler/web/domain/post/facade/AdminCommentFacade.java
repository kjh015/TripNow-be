package com.traveler.web.domain.post.facade;

import com.traveler.common.core.response.PageResponse;
import com.traveler.web.domain.post.adapter.AdminCommentClientAdapter;
import com.traveler.web.domain.post.dto.response.AdminCommentResponse;
import com.traveler.web.domain.post.mapper.CommentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminCommentFacade {
    private final AdminCommentClientAdapter adminCommentClientAdapter;
    private final CommentMapper commentMapper;

    public PageResponse<AdminCommentResponse.ListDTO> getComments(Long postId, Boolean deleted, Pageable pageable) {
        return adminCommentClientAdapter.getComments(postId, deleted, pageable).map(commentMapper::toAdminListResponse);
    }

    public AdminCommentResponse.DeleteDTO deleteComment(Long commentId) {
        return commentMapper.toAdminDeleteResponse(adminCommentClientAdapter.deleteComment(commentId));
    }

    public AdminCommentResponse.RestoreDTO restoreComment(Long commentId) {
        return commentMapper.toAdminRestoreResponse(adminCommentClientAdapter.restoreComment(commentId));
    }
}
