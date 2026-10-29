package com.traveler.post.domain.post.service;

import com.traveler.common.core.code.ErrorCode;
import com.traveler.post.domain.post.dto.request.PostRequest;
import com.traveler.post.domain.post.dto.response.PostResponse;
import com.traveler.post.domain.post.dto.result.PostStatsSummary;
import com.traveler.post.domain.post.dto.result.PostStatsSyncBatch;
import com.traveler.post.domain.post.entity.Post;
import com.traveler.post.domain.post.entity.PostStats;
import com.traveler.post.domain.post.entity.TravelPlace;
import com.traveler.post.domain.post.mapper.PostMapper;
import com.traveler.post.domain.post.mapper.TravelPlaceMapper;
import com.traveler.post.domain.post.repository.PostRepository;
import com.traveler.post.domain.post.repository.PostStatsRepository;
import com.traveler.post.domain.post.support.PostHardDeleter;
import com.traveler.post.domain.post.support.PostImageKeyValidator;
import com.traveler.post.domain.post.support.PostStatsSlots;
import com.traveler.post.global.exception.PostServiceException;
import com.traveler.post.global.exception.code.PostServiceErrorCode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PostService {
    private final PostRepository postRepository;
    private final PostStatsRepository postStatsRepository;
    private final PostStatsSlots postStatsSlots;
    private final PostMapper postMapper;
    private final TravelPlaceMapper travelPlaceMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final PostImageKeyValidator postImageKeyValidator;
    private final PostHardDeleter postHardDeleter;
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
            postStatsRepository.saveAll(PostStats.createSlots(savedPost));

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
        // 사용자 삭제 복구 문의 대응용
        log.info("[Post] 게시글 삭제 postId={} memberId={}", postId, memberId);

        return postMapper.toDeleteDTO(post);
    }

    // Batch Delete
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        // DB 벌크 삭제(자식 행 포함)
        List<String> imageUrls = postHardDeleter.hardDelete(ids);

        // S3 이미지 삭제 이벤트 발행
        if (!imageUrls.isEmpty()) {
            eventPublisher.publishEvent(postMapper.toImageDeleteBatchEvent(ids.getFirst(), imageUrls));
        }
    }

    /**
     * 조회수를 DB에 더하고, 조회수·좋아요·댓글이 바뀐 게시글의 통계를 검색 서버로 보낸다(통계 동기화 스케줄러, 5초마다).
     *
     * <p>통계는 커밋된 슬롯 합계라 정확하다. syncedAt(이번 발행 시각)을 실어, 검색 서버가 이보다 오래된 통계(재전송 등)로 덮어쓰지
     * 않게 한다.
     */
    public void syncStats(PostStatsSyncBatch batch) {
        batch.viewCounts().forEach(postRepository::incrementViewCount);

        Set<Long> postIds = batch.postIds();
        if (postIds.isEmpty()) return;

        long syncedAt = System.currentTimeMillis();
        Map<Long, PostStatsSummary> statsByPostId = postStatsSlots.summarizeByPostId(postIds);
        // 삭제된 게시글은 검색 문서가 없어 제외(@SQLRestriction)
        for (Post post : postRepository.findAllById(postIds)) {
            eventPublisher.publishEvent(postMapper.toStatUpdatedEvent(post, statsByPostId.get(post.getId()), syncedAt));
        }
    }
}
