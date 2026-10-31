package com.traveler.post.domain.post.service;

import com.traveler.post.domain.post.dto.response.PostImageResponse;
import com.traveler.post.domain.post.mapper.PostImageMapper;
import com.traveler.post.domain.post.support.PostImageKey;
import com.traveler.post.global.config.AsyncConfig;
import com.traveler.post.global.exception.PostServiceException;
import com.traveler.post.global.exception.code.PostServiceErrorCode;
import com.traveler.post.global.s3.ImageType;
import com.traveler.post.global.s3.S3Service;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

@Slf4j
@Service
public class PostImageService {
    private final S3Service s3Service;
    private final PostImageMapper postImageMapper;
    private final Executor postImageExecutor;

    public PostImageService(
            S3Service s3Service,
            PostImageMapper postImageMapper,
            @Qualifier(AsyncConfig.POST_IMAGE_EXECUTOR) Executor postImageExecutor) {
        this.s3Service = s3Service;
        this.postImageMapper = postImageMapper;
        this.postImageExecutor = postImageExecutor;
    }

    public PostImageResponse.PresignedUrlDTO getPresignedUrl(Long memberId, String fileName, String contentType) {
        ImageType imageType = validateFile(fileName, contentType);
        String key = PostImageKey.create(memberId, imageType);

        String url = s3Service.generatePresignedUrl(key, contentType);

        return postImageMapper.toPresignedUrlDTO(url, key);
    }

    /** 새 게시글의 임시 키를 모두 확정하고 보관 키를 돌려준다. S3 호출이 DB 커넥션을 붙잡지 않도록 트랜잭션 밖에서 호출한다. */
    public List<String> confirmUploads(Long memberId, List<String> keys) {
        return confirmUploads(memberId, keys, List.of());
    }

    /**
     * 수정 요청의 키를 확정한다. 게시글에 이미 붙은 키(currentKeys)는 그대로 통과한다.
     *
     * <p>keys가 null이면 이미지를 바꾸지 않는 요청이라 null을 돌려준다.
     */
    public List<String> confirmUploads(Long memberId, List<String> keys, List<String> currentKeys) {
        if (keys == null) return null;

        // 보관 키는 확정마다 새로 만들어져 저장 단계에서 중복을 찾을 수 없으므로 여기서 거른다
        if (new HashSet<>(keys).size() != keys.size()) {
            throw new PostServiceException(PostServiceErrorCode.POST_IMAGE_DUPLICATE);
        }

        Set<String> attached = new HashSet<>(currentKeys);
        // S3를 호출하기 전에 모든 키의 소유권을 확인한다
        keys.stream().filter(key -> !attached.contains(key)).forEach(key -> validateIssuedFor(memberId, key));

        List<CompletableFuture<String>> confirms = keys.stream()
                .map(key -> attached.contains(key)
                        ? CompletableFuture.completedFuture(key)
                        : CompletableFuture.supplyAsync(() -> confirm(memberId, key), postImageExecutor))
                .toList();

        // 실패가 있어도 모든 작업이 끝날 때까지 기다린다. 먼저 돌아가면 진행 중이던 복사의 사본을 되돌리지 못한다
        CompletableFuture.allOf(confirms.toArray(CompletableFuture[]::new))
                .exceptionally(e -> null)
                .join();

        List<String> confirmed = confirms.stream()
                .filter(confirm -> !confirm.isCompletedExceptionally())
                .map(CompletableFuture::join)
                .toList();
        Optional<CompletableFuture<String>> failed = confirms.stream()
                .filter(CompletableFuture::isCompletedExceptionally)
                .findFirst();
        if (failed.isPresent()) {
            // 일부만 복사하고 실패하면 사본을 되돌린다. posts/는 lifecycle 대상이 아니라 남으면 지워지지 않는다
            deleteCopies(findCopiedKeys(confirmed, attached));
            Throwable cause = failed.get().exceptionNow();
            throw cause instanceof RuntimeException e ? e : new IllegalStateException(cause);
        }
        return confirmed;
    }

    /** 확정된 키 중 이번 요청에서 새로 복사한 키. 게시글에 이미 붙어 있어 그대로 통과한 키는 뺀다. */
    public List<String> findCopiedKeys(List<String> confirmedKeys, Set<String> currentKeys) {
        if (confirmedKeys == null) return List.of();
        return confirmedKeys.stream()
                .filter(key -> !currentKeys.contains(key))
                .distinct()
                .toList();
    }

    /** 저장이 실패했을 때 복사본을 되돌린다. 삭제 실패가 저장 실패 원인을 가리지 않도록 로그만 남긴다. */
    public void deleteCopies(List<String> keys) {
        if (keys.isEmpty()) return;

        try {
            s3Service.deleteFilesByKeys(keys);
        } catch (RuntimeException e) {
            log.error("[Post] 복사한 이미지 삭제 실패 keys={}", keys, e);
        }
    }

    // 이 사용자에게 발급된 키인가
    private void validateIssuedFor(Long memberId, String tempKey) {
        if (!PostImageKey.isIssuedFor(memberId, tempKey)) {
            throw new PostServiceException(PostServiceErrorCode.S3_KEY_FORBIDDEN);
        }
    }

    private String confirm(Long memberId, String tempKey) {
        // 1. 실제로 올라와 있는가, 크기는 허용 범위인가
        HeadObjectResponse metadata = s3Service.headObject(tempKey);
        if (metadata.contentLength() > S3Service.MAX_FILE_SIZE) {
            log.warn("Oversized post image deleted. key={}, size={}", tempKey, metadata.contentLength());
            s3Service.deleteFilesByKeys(List.of(tempKey));
            throw new PostServiceException(PostServiceErrorCode.S3_FILE_TOO_LARGE);
        }
        // 2. 보관 자리로 복사. temp/ 원본은 lifecycle 규칙이 지운다
        String finalKey = PostImageKey.createPostKey(memberId, tempKey);
        s3Service.copyObject(tempKey, finalKey);
        return finalKey;
    }

    private ImageType validateFile(String fileName, String contentType) {
        if (fileName == null || fileName.isBlank()) {
            throw new PostServiceException(PostServiceErrorCode.S3_INVALID_FILE_EXTENSION);
        }
        if (contentType == null || contentType.isBlank()) {
            throw new PostServiceException(PostServiceErrorCode.S3_INVALID_FILE_TYPE);
        }

        int dotIndex = fileName.lastIndexOf(".");
        if (dotIndex == -1) {
            throw new PostServiceException(PostServiceErrorCode.S3_INVALID_FILE_EXTENSION);
        }

        String extension = fileName.substring(dotIndex + 1).toLowerCase();
        ImageType imageType = ImageType.fromExtension(extension)
                .orElseThrow(() -> new PostServiceException(PostServiceErrorCode.S3_INVALID_FILE_EXTENSION));
        if (!imageType.getContentType().equals(contentType)) {
            throw new PostServiceException(PostServiceErrorCode.S3_INVALID_FILE_TYPE);
        }
        return imageType;
    }
}
