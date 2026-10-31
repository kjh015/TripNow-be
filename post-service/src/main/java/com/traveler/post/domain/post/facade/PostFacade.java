package com.traveler.post.domain.post.facade;

import com.traveler.post.domain.post.dto.request.PostRequest;
import com.traveler.post.domain.post.dto.response.PostResponse;
import com.traveler.post.domain.post.service.PostImageService;
import com.traveler.post.domain.post.service.PostService;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 게시글 저장의 S3 단계와 DB 단계를 잇는다. S3 호출이 DB 커넥션을 붙잡지 않도록 트랜잭션을 두지 않는다.
 *
 * <p>저장이 실패하면 서버가 만든 posts/ 사본만 되돌린다(보상). temp/ 원본은 클라이언트가 같은 키로 다시 요청할 수 있도록 남기고 lifecycle 규칙이
 * 지운다.
 */
@Service
@RequiredArgsConstructor
public class PostFacade {
    private final PostService postService;
    private final PostImageService postImageService;

    public PostResponse.CreateDTO createPost(Long memberId, PostRequest.CreateDTO dto) {
        List<String> imageKeys = postImageService.confirmUploads(memberId, dto.images());
        try {
            return postService.createPost(memberId, dto, imageKeys);
        } catch (RuntimeException e) {
            postImageService.deleteCopies(postImageService.findCopiedKeys(imageKeys, Set.of()));
            throw e;
        }
    }

    public PostResponse.UpdateDTO updatePost(Long postId, Long memberId, PostRequest.UpdateDTO dto) {
        List<String> currentKeys = postService.getOwnedImageKeys(postId, memberId);
        List<String> imageKeys = postImageService.confirmUploads(memberId, dto.images(), currentKeys);
        // 원래 붙어 있던 키는 저장이 실패해도 지우면 안 되므로 이번 요청에서 복사한 키만 따로 둔다
        List<String> copiedKeys = postImageService.findCopiedKeys(imageKeys, new HashSet<>(currentKeys));
        try {
            return postService.updatePost(postId, memberId, dto, imageKeys, copiedKeys);
        } catch (RuntimeException e) {
            postImageService.deleteCopies(copiedKeys);
            throw e;
        }
    }

    public PostResponse.DeleteDTO deletePost(Long postId, Long memberId) {
        return postService.deletePost(postId, memberId);
    }
}
