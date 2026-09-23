package com.traveler.post.domain.post.controller;

import com.traveler.common.api.auth.annotation.LoginUser;
import com.traveler.common.api.auth.annotation.RequireAuth;
import com.traveler.common.core.auth.UserContext;
import com.traveler.common.core.code.SuccessCode;
import com.traveler.common.core.response.ApiResponse;
import com.traveler.post.domain.post.dto.response.PostImageResponse;
import com.traveler.post.domain.post.service.PostImageService;
import com.traveler.post.global.s3.validation.AllowedImageContentType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/posts/images")
@RequiredArgsConstructor
public class PostImageController {
    private final PostImageService postImageService;

    @Operation(summary = "이미지 업로드용 Presigned URL 발급", description = "S3 이미지 업로드를 위한 presigned URL을 생성합니다.")
    @RequireAuth
    @GetMapping("/presigned-url")
    public ApiResponse<PostImageResponse.PresignedUrlDTO> getPresignedUrl(
            @Parameter(description = "파일명 (확장자 포함)", example = "photo.jpg")
                    @RequestParam
                    @NotBlank(message = "파일명은 필수입니다")
                    @Size(min = 1, max = 255, message = "파일명은 1-255자 사이여야 합니다")
                    @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "파일명은 영문, 숫자, '.', '_', '-'만 허용됩니다")
                    String fileName,
            @Parameter(description = "Content-Type", example = "image/jpeg")
                    @RequestParam
                    @NotBlank(message = "Content-Type은 필수입니다")
                    @AllowedImageContentType
                    String contentType,
            @Parameter(hidden = true) @LoginUser UserContext user) {
        return ApiResponse.onSuccess(
                SuccessCode.OK, postImageService.getPresignedUrl(user.id(), fileName, contentType));
    }
}
