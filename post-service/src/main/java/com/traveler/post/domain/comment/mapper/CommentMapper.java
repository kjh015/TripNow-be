package com.traveler.post.domain.comment.mapper;

import com.traveler.post.domain.comment.dto.event.CommentEvent;
import com.traveler.post.domain.comment.dto.message.CommentMessage;
import com.traveler.post.domain.comment.dto.request.CommentRequest;
import com.traveler.post.domain.comment.dto.response.CommentResponse;
import com.traveler.post.domain.comment.entity.Comment;
import com.traveler.post.domain.post.dto.result.PostStatsSummary;
import com.traveler.post.domain.post.entity.Post;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CommentMapper {

    @Mapping(source = "dto.content", target = "content")
    @Mapping(target = "star", source = "dto.star") // 별점 매핑 누락 방지
    @Mapping(target = "post", source = "post")
    @Mapping(target = "memberId", source = "memberId")
    @Mapping(target = "id", ignore = true)
    Comment toCreateEntity(CommentRequest.CreateDTO dto, Post post, Long memberId);

    default CommentResponse.CreateDTO toCreateDTO(Comment comment, PostStatsSummary stats) {
        return new CommentResponse.CreateDTO(
                comment.getId(), comment.getCreatedAt(), stats.commentCount(), stats.starAvg());
    }

    default CommentResponse.UpdateDTO toUpdateDTO(Comment comment, PostStatsSummary stats) {
        return new CommentResponse.UpdateDTO(
                comment.getId(), comment.getUpdatedAt(), stats.commentCount(), stats.starAvg());
    }

    default CommentResponse.DeleteDTO toDeleteDTO(Comment comment, PostStatsSummary stats) {
        return new CommentResponse.DeleteDTO(
                comment.getId(), comment.getDeletedAt(), stats.commentCount(), stats.starAvg());
    }

    // Message
    @Mapping(source = "comment.id", target = "commentId")
    CommentMessage.CreatedDTO toCreatedMsgDTO(Comment comment, Long postId);

    @Mapping(source = "id", target = "commentId")
    CommentMessage.UpdatedDTO toUpdatedMsgDTO(Comment comment);

    @Mapping(source = "id", target = "commentId")
    CommentMessage.DeletedDTO toDeletedMsgDTO(Comment comment);

    // Event
    default CommentEvent.Created toCreatedEvent(Comment comment) {
        return new CommentEvent.Created(
                toCreatedMsgDTO(comment, comment.getPost().getId()));
    }

    default CommentEvent.Updated toUpdatedEvent(Comment comment) {
        return new CommentEvent.Updated(
                toUpdatedMsgDTO(comment), comment.getPost().getId());
    }

    default CommentEvent.Deleted toDeletedEvent(Comment comment) {
        return new CommentEvent.Deleted(
                toDeletedMsgDTO(comment), comment.getPost().getId());
    }
}
