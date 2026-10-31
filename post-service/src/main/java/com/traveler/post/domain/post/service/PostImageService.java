package com.traveler.post.domain.post.service;

import static com.traveler.post.domain.post.support.PostImageKey.POST_PREFIX;
import static com.traveler.post.domain.post.support.PostImageKey.TEMP_PREFIX;

import com.traveler.post.domain.post.dto.response.PostImageResponse;
import com.traveler.post.domain.post.mapper.PostImageMapper;
import com.traveler.post.domain.post.support.PostImageKey;
import com.traveler.post.global.exception.PostServiceException;
import com.traveler.post.global.exception.code.PostServiceErrorCode;
import com.traveler.post.global.s3.ImageType;
import com.traveler.post.global.s3.S3Service;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostImageService {
    private final S3Service s3Service;
    private final PostImageMapper postImageMapper;

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

        Set<String> attached = new HashSet<>(currentKeys);
        List<String> confirmed = new ArrayList<>();
        try {
            for (String key : keys) {
                confirmed.add(attached.contains(key) ? key : confirm(memberId, key));
            }
        } catch (RuntimeException e) {
            // 일부만 복사한 뒤 실패하면 앞서 만든 사본을 되돌린다. posts/는 lifecycle 대상이 아니라 남으면 지워지지 않는다
            deleteCopies(findCopiedKeys(confirmed, attached));
            throw e;
        }
        return confirmed;
    }

    /** 확정된 키 중 이번 요청에서 새로 복사한 키. 같은 임시 키를 다시 보내 이미 붙은 보관 키가 된 경우는 뺀다. */
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

    private String confirm(Long memberId, String tempKey) {
        String ownedPrefix = TEMP_PREFIX + memberId + "/";
        // 1. 이 사용자에게 발급된 키인가
        if (!PostImageKey.isIssuedFor(memberId, tempKey)) {
            throw new PostServiceException(PostServiceErrorCode.S3_KEY_FORBIDDEN);
        }
        // 2. 실제로 올라와 있는가, 크기는 허용 범위인가
        HeadObjectResponse metadata = s3Service.headObject(tempKey);
        if (metadata.contentLength() > S3Service.MAX_FILE_SIZE) {
            log.warn("Oversized post image deleted. key={}, size={}", tempKey, metadata.contentLength());
            s3Service.deleteFilesByKeys(List.of(tempKey));
            throw new PostServiceException(PostServiceErrorCode.S3_FILE_TOO_LARGE);
        }
        // 3. 보관 자리로 복사. temp/ 원본은 lifecycle 규칙이 지운다
        String finalKey = POST_PREFIX + memberId + "/" + tempKey.substring(ownedPrefix.length());
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
