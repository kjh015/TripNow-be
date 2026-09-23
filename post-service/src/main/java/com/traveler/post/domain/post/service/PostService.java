package com.traveler.post.domain.post.service;

import com.traveler.common.core.code.ErrorCode;
import com.traveler.post.domain.post.dto.request.PostRequest;
import com.traveler.post.domain.post.dto.response.PostResponse;
import com.traveler.post.domain.post.entity.Post;
import com.traveler.post.domain.post.entity.TravelPlace;
import com.traveler.post.domain.post.mapper.PostMapper;
import com.traveler.post.domain.post.mapper.TravelPlaceMapper;
import com.traveler.post.domain.post.repository.PostRepository;
import com.traveler.post.domain.post.support.PostImageKeyValidator;
import com.traveler.post.global.exception.PostServiceException;
import com.traveler.post.global.exception.code.PostServiceErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
@Transactional
public class PostService {
    private final PostRepository postRepository;
    private final PostMapper postMapper;
    private final TravelPlaceMapper travelPlaceMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final PostImageKeyValidator postImageKeyValidator;
    private final TransactionTemplate transactionTemplate;

    // S3 확인(headObject)이 DB 커넥션을 붙잡지 않도록 이미지 검증을 트랜잭션 밖에서 끝낸다
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PostResponse.CreateDTO createPost(Long memberId, PostRequest.CreateDTO dto) {
        postImageKeyValidator.validateNewKeys(memberId, dto.images());

        return transactionTemplate.execute(status -> {
            TravelPlace travelPlace = travelPlaceMapper.toCreateEntity(dto);

            Post post = postMapper.toCreateEntity(dto, travelPlace, memberId);

            post.setImages(dto.images());

            Post savedPost = postRepository.save(post);

            eventPublisher.publishEvent(postMapper.toCreatedEvent(savedPost));

            return postMapper.toCreateDTO(savedPost);
        });
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PostResponse.UpdateDTO updatePost(Long postId, Long memberId, PostRequest.UpdateDTO dto) {
        // 이미 붙어 있는 키는 저장 시 검증을 통과한 본인 키이므로 새로 추가되는 키만 검증한다
        Set<String> validatedKeys = getOwnedPost(postId, memberId).findNewImageKeys(dto.images());
        postImageKeyValidator.validateNewKeys(memberId, validatedKeys);

        return transactionTemplate.execute(status -> {
            Post post = getOwnedPost(postId, memberId);

            post.getTravelPlace().update(dto.category(), dto.region(), dto.travelPlace(), dto.address());
            post.update(dto.title(), dto.content());

            if (dto.images() != null) {
                // 검증 이후 트랜잭션 시작 전에 이미지 구성이 바뀌어 검증하지 않은 키가 생긴 경우 거부
                if (!validatedKeys.containsAll(post.findNewImageKeys(dto.images()))) {
                    throw new PostServiceException(PostServiceErrorCode.POST_IMAGE_INVALID_KEY);
                }

                List<String> keysToDelete = post.setImages(dto.images());

                if (!keysToDelete.isEmpty()) {
                    eventPublisher.publishEvent(postMapper.toImageDeleteEvent(post.getId(), keysToDelete));
                }
            }

            eventPublisher.publishEvent(postMapper.toUpdatedEvent(post));

            return postMapper.toUpdateDTO(post);
        });
    }

    private Post getOwnedPost(Long postId, Long memberId) {
        Post post = postRepository
                .findByIdWithDetails(postId)
                .orElseThrow(() -> new PostServiceException(PostServiceErrorCode.POST_NOT_FOUND));

        if (!post.getMemberId().equals(memberId)) {
            throw new PostServiceException(ErrorCode.FORBIDDEN);
        }
        return post;
    }

    public PostResponse.DeleteDTO deletePost(Long postId, Long memberId) {
        Post post = postRepository
                .findById(postId)
                .orElseThrow(() -> new PostServiceException(PostServiceErrorCode.POST_NOT_FOUND));

        if (!post.getMemberId().equals(memberId)) {
            throw new PostServiceException(ErrorCode.FORBIDDEN);
        }

        post.delete();
        eventPublisher.publishEvent(postMapper.toDeletedEvent(post));

        return postMapper.toDeleteDTO(post);
    }

    // Batch Delete
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        // S3 이미지 조회
        List<String> imageUrls = postRepository.findImageKeysByPostIds(ids);

        // DB 벌크 삭제
        postRepository.hardDeletePostsByIds(ids);

        // S3 이미지 삭제 이벤트 발행
        if (!imageUrls.isEmpty()) {
            eventPublisher.publishEvent(postMapper.toImageDeleteBatchEvent(ids.getFirst(), imageUrls));
        }
    }

    public void flushViewCountsToDB(Map<Object, Object> viewCountMap) {
        if (viewCountMap == null || viewCountMap.isEmpty()) return;

        List<Long> postIds = new ArrayList<>(viewCountMap.size());

        // 게시글별 조회수 벌크 증가
        for (Map.Entry<Object, Object> entry : viewCountMap.entrySet()) {
            Long postId = Long.valueOf(String.valueOf(entry.getKey()));
            Long increment = Long.valueOf(String.valueOf(entry.getValue()));

            postRepository.incrementViewCount(postId, increment);
            postIds.add(postId);
        }

        // 증가가 반영된 최신 상태를 한 번에 조회하여 검색 서버 동기화 이벤트 발행
        List<Post> updatedPosts = postRepository.findAllById(postIds);
        for (Post post : updatedPosts) {
            eventPublisher.publishEvent(postMapper.toStatUpdatedEvent(post));
        }
    }
}
