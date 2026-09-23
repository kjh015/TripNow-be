package com.traveler.post.domain.post.support;

import com.traveler.post.domain.comment.repository.CommentRepository;
import com.traveler.post.domain.like.repository.LikeRepository;
import com.traveler.post.domain.post.repository.PostRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게시글 영구 삭제(배치·어드민 공용).
 *
 * <p>벌크 삭제는 cascade·orphanRemoval을 타지 않으므로 FK로 게시글을 참조하는 자식 행을 먼저 지운다.
 * travel_place는 게시글이 참조하는 쪽이라 게시글을 지운 뒤 정리한다.
 */
@Component
@RequiredArgsConstructor
public class PostHardDeleter {
    private final PostRepository postRepository;
    private final LikeRepository likeRepository;
    private final CommentRepository commentRepository;

    /**
     * 게시글과 자식 행을 삭제하고, S3에서 지워야 할 이미지 키를 돌려준다.
     * 이미지 삭제 이벤트 발행은 호출 측이 경로에 맞는 이벤트로 한다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<String> hardDelete(List<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) return List.of();

        List<String> imageKeys = postRepository.findImageKeysByPostIds(postIds);
        List<Long> travelPlaceIds = postRepository.findTravelPlaceIdsByPostIds(postIds);

        postRepository.hardDeletePostImagesByPostIds(postIds);
        likeRepository.hardDeleteLikesByPostIds(postIds);
        commentRepository.hardDeleteCommentsByPostIds(postIds);
        postRepository.hardDeletePostsByIds(postIds);
        if (!travelPlaceIds.isEmpty()) {
            postRepository.hardDeleteTravelPlacesByIds(travelPlaceIds);
        }

        return imageKeys;
    }
}
