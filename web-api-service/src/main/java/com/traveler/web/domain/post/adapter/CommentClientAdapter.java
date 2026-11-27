package com.traveler.web.domain.post.adapter;

import com.traveler.web.domain.post.client.CommentClient;
import com.traveler.web.domain.post.client.dto.request.CommentClientRequest;
import com.traveler.web.domain.post.client.dto.response.CommentClientResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CommentClientAdapter {
    private final CommentClient commentClient;

    public CommentClientResponse.CreateDTO createComment(CommentClientRequest.CreateDTO dto) {
        return commentClient.createComment(dto).result();
    }

    public CommentClientResponse.UpdateDTO updateComment(Long commentId, CommentClientRequest.UpdateDTO dto) {
        return commentClient.updateComment(commentId, dto).result();
    }

    public CommentClientResponse.DeleteDTO deleteComment(Long commentId) {
        return commentClient.deleteComment(commentId).result();
    }
}
