package com.traveler.web.domain.post.adapter;

import com.traveler.web.domain.post.client.CommentClient;
import com.traveler.web.domain.post.client.dto.request.CommentClientRequest;
import com.traveler.web.domain.post.client.dto.response.CommentClientResponse;
import com.traveler.web.global.resilience.DownstreamCallExecutor;
import com.traveler.web.global.resilience.DownstreamServiceNames;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CommentClientAdapter {
    private final CommentClient commentClient;
    private final DownstreamCallExecutor downstreamCallExecutor;

    public CommentClientResponse.CreateDTO createComment(CommentClientRequest.CreateDTO dto) {
        return downstreamCallExecutor.execute(
                DownstreamServiceNames.POST,
                () -> commentClient.createComment(dto).result());
    }

    public CommentClientResponse.UpdateDTO updateComment(Long commentId, CommentClientRequest.UpdateDTO dto) {
        return downstreamCallExecutor.execute(
                DownstreamServiceNames.POST,
                () -> commentClient.updateComment(commentId, dto).result());
    }

    public CommentClientResponse.DeleteDTO deleteComment(Long commentId) {
        return downstreamCallExecutor.execute(
                DownstreamServiceNames.POST,
                () -> commentClient.deleteComment(commentId).result());
    }
}
