package com.traveler.web.domain.post.facade;

import com.traveler.web.domain.member.adapter.MemberClientAdapter;
import com.traveler.web.domain.post.adapter.PostClientAdapter;
import com.traveler.web.domain.post.client.dto.response.PostClientResponse;
import com.traveler.web.domain.post.dto.request.PostRequest;
import com.traveler.web.domain.post.dto.response.PostResponse;
import com.traveler.web.domain.post.mapper.PostMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PostFacade {
    private final PostClientAdapter postClientAdapter;
    private final MemberClientAdapter memberClientAdapter;
    private final PostMapper postMapper;

    public PostResponse.CreateDTO createPost(PostRequest.CreateDTO dto) {
        PostClientResponse.CreateDTO created = postClientAdapter.createPost(postMapper.toCreateClientRequest(dto));
        return postMapper.toCreateResponse(created, myNicknameOrNull(created.postId()));
    }

    public PostResponse.UpdateDTO updatePost(Long postId, PostRequest.UpdateDTO dto) {
        PostClientResponse.UpdateDTO updated =
                postClientAdapter.updatePost(postId, postMapper.toUpdateClientRequest(dto));
        return postMapper.toUpdateResponse(updated, myNicknameOrNull(postId));
    }

    public PostResponse.DeleteDTO deletePost(Long postId) {
        return postMapper.toDeleteResponse(postClientAdapter.deletePost(postId));
    }

    public PostResponse.PresignedUrlDTO getPresignedUrl(String fileName, String contentType) {
        return postMapper.toPresignedUrlResponse(postClientAdapter.getPresignedUrl(fileName, contentType));
    }

    // 게시글 저장은 이미 완료된 상태다.
    // 닉네임 조회 실패로 성공한 쓰기를 실패로 응답하면 사용자가 중복 작성할 수 있으므로, 조회 실패 시 닉네임 없이 응답한다.
    private String myNicknameOrNull(Long postId) {
        try {
            return memberClientAdapter.getMyNickname();
        } catch (RuntimeException e) {
            log.warn(
                    "[Post] 작성자 닉네임 조회 실패, 응답 닉네임 없이 반환 postId={} type={}",
                    postId,
                    e.getClass().getSimpleName());
            return null;
        }
    }
}
