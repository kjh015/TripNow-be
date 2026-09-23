package com.traveler.post.domain.post.service;

import com.traveler.post.domain.post.dto.response.PostImageResponse;
import com.traveler.post.domain.post.mapper.PostImageMapper;
import com.traveler.post.domain.post.support.PostImageKey;
import com.traveler.post.global.exception.PostServiceException;
import com.traveler.post.global.exception.code.PostServiceErrorCode;
import com.traveler.post.global.s3.ImageType;
import com.traveler.post.global.s3.S3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
