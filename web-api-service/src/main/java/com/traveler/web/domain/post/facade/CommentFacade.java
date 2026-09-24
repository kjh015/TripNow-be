package com.traveler.web.domain.post.facade;

import com.traveler.web.domain.post.adapter.CommentClientAdapter;
import com.traveler.web.domain.post.dto.request.CommentRequest;
import com.traveler.web.domain.post.dto.response.CommentResponse;
import com.traveler.web.domain.post.mapper.CommentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CommentFacade {
    private final CommentClientAdapter commentClientAdapter;
    private final CommentMapper commentMapper;

    public CommentResponse.CreateDTO createComment(CommentRequest.CreateDTO dto) {
        return commentMapper.toCreateResponse(
                commentClientAdapter.createComment(commentMapper.toCreateClientRequest(dto)));
    }

    public CommentResponse.UpdateDTO updateComment(Long commentId, CommentRequest.UpdateDTO dto) {
        return commentMapper.toUpdateResponse(
                commentClientAdapter.updateComment(commentId, commentMapper.toUpdateClientRequest(dto)));
    }

    public CommentResponse.DeleteDTO deleteComment(Long commentId) {
        return commentMapper.toDeleteResponse(commentClientAdapter.deleteComment(commentId));
    }
}
