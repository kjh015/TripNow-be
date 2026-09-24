package com.traveler.post.domain.post.service;

import com.traveler.common.api.converter.PageConverter;
import com.traveler.common.core.response.PageResponse;
import com.traveler.post.domain.post.dto.response.AdminPostResponse;
import com.traveler.post.domain.post.entity.Post;
import com.traveler.post.domain.post.mapper.AdminPostMapper;
import com.traveler.post.domain.post.mapper.PostMapper;
import com.traveler.post.domain.post.repository.PostRepository;
import com.traveler.post.domain.post.support.PostHardDeleter;
import com.traveler.post.global.exception.PostServiceException;
import com.traveler.post.global.exception.code.PostServiceErrorCode;
import java.util.List;
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
public class AdminPostService {
    private final PostRepository postRepository;
    private final PostHardDeleter postHardDeleter;
    private final AdminPostMapper adminPostMapper;
    private final PostMapper postMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public PageResponse<AdminPostResponse.ListDTO> getPosts(Boolean deleted, Pageable pageable) {
        // 네이티브 쿼리에 ORDER BY가 고정되어 있으므로 Pageable의 sort는 제거하고 page/size만 사용
        Pageable unsorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        Page<Post> posts = postRepository.findAllForAdmin(deleted, unsorted);
        return PageConverter.toPageResponse(posts, adminPostMapper::toListDTO);
    }

    @Transactional(readOnly = true)
    public AdminPostResponse.DetailDTO getPost(Long postId) {
        Post post = postRepository
                .findByIdForAdmin(postId)
                .orElseThrow(() -> new PostServiceException(PostServiceErrorCode.POST_NOT_FOUND));

        return adminPostMapper.toDetailDTO(post);
    }

    public AdminPostResponse.DeleteDTO deletePost(Long postId) {
        Post post = postRepository
                .findByIdForAdmin(postId)
                .orElseThrow(() -> new PostServiceException(PostServiceErrorCode.POST_NOT_FOUND));

        if (post.isDeleted()) {
            throw new PostServiceException(PostServiceErrorCode.POST_ALREADY_DELETED);
        }

        post.delete();
        eventPublisher.publishEvent(postMapper.toDeletedEvent(post));

        return adminPostMapper.toDeleteDTO(post);
    }

    public AdminPostResponse.RestoreDTO restorePost(Long postId) {
        Post post = postRepository
                .findByIdForAdmin(postId)
                .orElseThrow(() -> new PostServiceException(PostServiceErrorCode.POST_NOT_FOUND));

        post.restore();

        // 삭제 시 검색 문서가 제거되므로 재색인(Created) 후 통계(StatUpdated)를 동기화
        eventPublisher.publishEvent(postMapper.toCreatedEvent(post));
        eventPublisher.publishEvent(postMapper.toStatUpdatedEvent(post));

        return adminPostMapper.toRestoreDTO(post);
    }

    public AdminPostResponse.PermanentDeleteDTO permanentlyDeletePost(Long postId) {
        Post post = postRepository
                .findByIdForAdmin(postId)
                .orElseThrow(() -> new PostServiceException(PostServiceErrorCode.POST_NOT_FOUND));

        // 아직 소프트 삭제되지 않은 게시글은 검색 문서가 살아있으므로 삭제 이벤트 발행
        if (!post.isDeleted()) {
            eventPublisher.publishEvent(postMapper.toDeletedEvent(post));
        }

        List<String> imageKeys = postHardDeleter.hardDelete(List.of(postId));
        if (!imageKeys.isEmpty()) {
            eventPublisher.publishEvent(postMapper.toImageDeleteEvent(postId, imageKeys));
        }

        return new AdminPostResponse.PermanentDeleteDTO(postId);
    }
}
