package com.traveler.post.domain.post.support;

import com.traveler.post.global.exception.PostServiceException;
import com.traveler.post.global.exception.code.PostServiceErrorCode;
import com.traveler.post.global.s3.S3Service;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 게시글에 새로 붙는 이미지 키의 소유권·존재·크기를 검증한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostImageKeyValidator {
    private final S3Service s3Service;

    /**
     * 1) 모든 키가 해당 회원에게 발급된 형식인지 확인한 뒤 2) 키마다 S3 객체의 존재와 크기를 확인한다.
     * 크기 초과 객체는 삭제한다. S3 호출이 DB 커넥션을 붙잡지 않도록 트랜잭션 밖에서 호출한다.
     */
    public void validateNewKeys(Long memberId, Collection<String> keys) {
        if (keys == null || keys.isEmpty()) return;

        keys.forEach(key -> {
            if (!PostImageKey.isIssuedFor(memberId, key)) {
                throw new PostServiceException(PostServiceErrorCode.POST_IMAGE_INVALID_KEY);
            }
        });

        keys.forEach(this::validateObject);
    }

    private void validateObject(String key) {
        long size = s3Service
                .findObjectSize(key)
                .orElseThrow(() -> new PostServiceException(PostServiceErrorCode.POST_IMAGE_NOT_UPLOADED));

        if (size > S3Service.MAX_FILE_SIZE) {
            log.warn("Oversized post image deleted. key={}, size={}", key, size);
            s3Service.deleteFilesByKeys(List.of(key));
            throw new PostServiceException(PostServiceErrorCode.POST_IMAGE_TOO_LARGE);
        }
    }
}
