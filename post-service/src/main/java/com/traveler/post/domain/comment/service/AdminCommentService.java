package com.traveler.post.domain.comment.service;

import static com.traveler.post.global.audit.PostServiceAuditAction.DELETE_COMMENT;
import static com.traveler.post.global.audit.PostServiceAuditAction.RESTORE_COMMENT;

import com.traveler.common.api.converter.PageConverter;
import com.traveler.common.core.response.PageResponse;
import com.traveler.common.support.logging.audit.AuditLogger;
import com.traveler.post.domain.comment.dto.event.CommentEvent;
import com.traveler.post.domain.comment.dto.response.AdminCommentResponse;
import com.traveler.post.domain.comment.entity.Comment;
import com.traveler.post.domain.comment.mapper.AdminCommentMapper;
import com.traveler.post.domain.comment.mapper.CommentMapper;
import com.traveler.post.domain.comment.repository.CommentRepository;
import com.traveler.post.domain.post.entity.Post;
import com.traveler.post.domain.post.repository.PostRepository;
import com.traveler.post.domain.post.support.PostStatsSlots;
import com.traveler.post.global.exception.PostServiceException;
import com.traveler.post.global.exception.code.PostServiceErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminCommentService {
    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final PostStatsSlots postStatsSlots;
    private final AdminCommentMapper adminCommentMapper;
    private final CommentMapper commentMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditLogger auditLogger;

    @Transactional(readOnly = true)
    public PageResponse<AdminCommentResponse.ListDTO> getComments(Long postId, Boolean deleted, Pageable pageable) {
        // 네이티브 쿼리에 ORDER BY가 고정되어 있으므로 Pageable의 sort는 제거하고 page/size만 사용
        Pageable unsorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        Page<Comment> comments = commentRepository.findAllForAdmin(postId, deleted, unsorted);
        return PageConverter.toPageResponse(comments, adminCommentMapper::toListDTO);
    }

    public AdminCommentResponse.DeleteDTO deleteComment(Long commentId) {
        Comment comment = commentRepository
                .findByIdForAdminWithLock(commentId)
                .orElseThrow(() -> new PostServiceException(PostServiceErrorCode.COMMENT_NOT_FOUND));

        if (comment.isDeleted()) {
            throw new PostServiceException(PostServiceErrorCode.COMMENT_ALREADY_DELETED);
        }

        Post post = postRepository
                .findByIdForAdminWithLock(comment.getPost().getId())
                .orElseThrow(() -> new PostServiceException(PostServiceErrorCode.POST_NOT_FOUND));

        comment.delete();
        auditLogger
                .event(DELETE_COMMENT, commentId)
                .detail("postId", post.getId())
                .log();

        if (post.isDeleted()) {
            // 삭제된 게시물은 검색 문서가 없으므로 통계 갱신 없이 댓글 삭제 메시지만 발행
            eventPublisher.publishEvent(
                    new CommentEvent.AdminDeleted(commentMapper.toDeletedMsgDTO(comment), post.getId()));
        } else {
            postStatsSlots.getRandom(post.getId()).removeComment(comment.getStar());
            eventPublisher.publishEvent(commentMapper.toDeletedEvent(comment));
        }

        return adminCommentMapper.toDeleteDTO(comment);
    }

    public AdminCommentResponse.RestoreDTO restoreComment(Long commentId) {
        Comment comment = commentRepository
                .findByIdForAdminWithLock(commentId)
                .orElseThrow(() -> new PostServiceException(PostServiceErrorCode.COMMENT_NOT_FOUND));

        if (!comment.isDeleted()) {
            throw new PostServiceException(PostServiceErrorCode.COMMENT_NOT_DELETED);
        }

        Post post = postRepository
                .findByIdForAdminWithLock(comment.getPost().getId())
                .orElseThrow(() -> new PostServiceException(PostServiceErrorCode.POST_NOT_FOUND));

        if (post.isDeleted()) {
            throw new PostServiceException(PostServiceErrorCode.COMMENT_PARENT_POST_DELETED);
        }

        comment.restore();
        auditLogger
                .event(RESTORE_COMMENT, commentId)
                .detail("postId", post.getId())
                .log();
        postStatsSlots.getRandom(post.getId()).addComment(comment.getStar());

        // 검색 문서 재색인(Created) 및 게시글 통계 변경
        eventPublisher.publishEvent(commentMapper.toCreatedEvent(comment));

        return adminCommentMapper.toRestoreDTO(comment);
    }
}
