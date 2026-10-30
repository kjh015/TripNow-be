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
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
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
        postStatsSlots.getRandom(postId).addComment(dto.star());

        eventPublisher.publishEvent(commentMapper.toCreatedEvent(savedComment));

        return commentMapper.toCreateDTO(savedComment, postStatsSlots.summarize(postId));
    }

    public CommentResponse.UpdateDTO updateComment(Long commentId, Long memberId, CommentRequest.UpdateDTO dto) {
        Comment comment = getOwnedComment(commentId, memberId);
        Long postId = comment.getPost().getId();
        validatePostExists(postId);

        int oldStar = comment.getStar();
        comment.update(dto.content(), dto.star());
        postStatsSlots.getRandom(postId).updateCommentStar(oldStar, dto.star());

        eventPublisher.publishEvent(commentMapper.toUpdatedEvent(comment));

        return commentMapper.toUpdateDTO(comment, postStatsSlots.summarize(postId));
    }

    public CommentResponse.DeleteDTO deleteComment(Long commentId, Long memberId) {
        // 탈퇴 정리 같은 다른 삭제 요청과 겹쳐도 PostStats가 두 번 갱신되지 않게 댓글을 잠가 조회한다
        Comment comment = validateOwner(
                commentRepository
                        .findByIdWithLock(commentId)
                        .orElseThrow(() -> new PostServiceException(PostServiceErrorCode.COMMENT_NOT_FOUND)),
                memberId);
        Long postId = comment.getPost().getId();
        validatePostExists(postId);

        comment.delete();
        postStatsSlots.getRandom(postId).removeComment(comment.getStar());

        eventPublisher.publishEvent(commentMapper.toDeletedEvent(comment));

        return commentMapper.toDeleteDTO(comment, postStatsSlots.summarize(postId));
    }

    /**
     * 탈퇴 회원의 댓글을 최대 {@code limit}개까지 소프트 삭제하고 PostStats를 갱신한다. 삭제한 댓글 수를 반환한다.
     *
     * <p>PostStats가 두 번 갱신되지 않게 삭제되지 않은 댓글만 잠가 조회한다. 교착을 막기 위해 슬롯은
     * 게시글마다 하나씩 게시글 ID 순으로 잠그고, 삭제 이벤트는 게시글별로 묶어 발행한다.
     */
    public int deleteByMember(Long memberId, int limit) {
        List<Comment> comments = commentRepository.findByMemberIdOrderByIdAsc(memberId, Limit.of(limit));
        if (comments.isEmpty()) {
            return 0;
        }
        commentRepository.softDeleteAll(comments.stream().map(Comment::getId).toList(), Instant.now());

        Map<Long, List<Comment>> deletedByPostId = comments.stream()
                .collect(
                        Collectors.groupingBy(comment -> comment.getPost().getId(), TreeMap::new, Collectors.toList()));
        deletedByPostId.forEach((postId, deleted) -> {
            long starSum = deleted.stream().mapToLong(Comment::getStar).sum();
            postStatsSlots.getRandom(postId).removeComments(deleted.size(), starSum);
            eventPublisher.publishEvent(commentMapper.toBatchDeletedEvent(postId, deleted));
        });
        return comments.size();
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

        return validateOwner(comment, memberId);
    }

    private Comment validateOwner(Comment comment, Long memberId) {
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
