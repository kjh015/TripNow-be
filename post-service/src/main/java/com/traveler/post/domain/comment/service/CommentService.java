package com.traveler.post.domain.comment.service;

import com.traveler.common.core.code.ErrorCode;
import com.traveler.post.domain.comment.dto.request.CommentRequest;
import com.traveler.post.domain.comment.dto.response.CommentResponse;
import com.traveler.post.domain.comment.entity.Comment;
import com.traveler.post.domain.comment.mapper.CommentMapper;
import com.traveler.post.domain.comment.repository.CommentRepository;
import com.traveler.post.domain.post.entity.Post;
import com.traveler.post.domain.post.repository.PostRepository;
import com.traveler.post.domain.post.support.PostStatsSlots;
import com.traveler.post.global.exception.PostServiceException;
import com.traveler.post.global.exception.code.PostServiceErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 댓글 작성·수정·삭제. 게시글 행을 잠그지 않고, 댓글을 먼저 쓴 뒤 마지막에 통계 슬롯(post_stats) 하나를 잠가 갱신한다.
 * 응답에는 처리 직후의 게시글 통계(슬롯 합계)를 싣는다.
 *
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CommentService {
    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final PostStatsSlots postStatsSlots;
    private final CommentMapper commentMapper;

    private final ApplicationEventPublisher eventPublisher;

    public CommentResponse.CreateDTO createComment(Long memberId, CommentRequest.CreateDTO dto) {
        Long postId = dto.postId();
        validatePostExists(postId);
        Post post = postRepository.getReferenceById(postId);

        Comment savedComment = commentRepository.save(commentMapper.toCreateEntity(dto, post, memberId));
        postStatsSlots.getRandomForUpdate(postId).addComment(dto.star());

        eventPublisher.publishEvent(commentMapper.toCreatedEvent(savedComment));

        return commentMapper.toCreateDTO(savedComment, postStatsSlots.summarize(postId));
    }

    public CommentResponse.UpdateDTO updateComment(Long commentId, Long memberId, CommentRequest.UpdateDTO dto) {
        Comment comment = getOwnedComment(commentId, memberId);
        Long postId = comment.getPost().getId();
        validatePostExists(postId);

        int oldStar = comment.getStar();
        comment.update(dto.content(), dto.star());
        postStatsSlots.getRandomForUpdate(postId).updateCommentStar(oldStar, dto.star());

        eventPublisher.publishEvent(commentMapper.toUpdatedEvent(comment));

        return commentMapper.toUpdateDTO(comment, postStatsSlots.summarize(postId));
    }

    public CommentResponse.DeleteDTO deleteComment(Long commentId, Long memberId) {
        Comment comment = getOwnedComment(commentId, memberId);
        Long postId = comment.getPost().getId();
        validatePostExists(postId);

        comment.delete();
        postStatsSlots.getRandomForUpdate(postId).removeComment(comment.getStar());

        eventPublisher.publishEvent(commentMapper.toDeletedEvent(comment));

        return commentMapper.toDeleteDTO(comment, postStatsSlots.summarize(postId));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteBatch(List<Long> ids) {
        if (ids.isEmpty()) return;
        commentRepository.hardDeleteCommentsByIds(ids);
    }

    private Comment getOwnedComment(Long commentId, Long memberId) {
        Comment comment = commentRepository
                .findById(commentId)
                .orElseThrow(() -> new PostServiceException(PostServiceErrorCode.COMMENT_NOT_FOUND));

        if (!comment.getMemberId().equals(memberId)) {
            throw new PostServiceException(ErrorCode.FORBIDDEN);
        }
        return comment;
    }

    // 소프트 삭제된 게시글은 @SQLRestriction으로 걸러져 없는 게시글로 본다
    private void validatePostExists(Long postId) {
        if (!postRepository.existsById(postId)) {
            throw new PostServiceException(PostServiceErrorCode.POST_NOT_FOUND);
        }
    }
}
