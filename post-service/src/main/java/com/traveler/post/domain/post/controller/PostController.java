package com.traveler.post.domain.post.controller;

import com.traveler.common.api.auth.annotation.LoginUser;
import com.traveler.common.api.auth.annotation.RequireAuth;
import com.traveler.common.core.auth.UserContext;
import com.traveler.common.core.code.ErrorCode;
import com.traveler.common.core.code.SuccessCode;
import com.traveler.common.core.response.ApiResponse;
import com.traveler.post.domain.post.dto.request.PostRequest;
import com.traveler.post.domain.post.dto.response.PostResponse;
import com.traveler.post.domain.post.service.PostService;
import com.traveler.post.global.exception.code.PostServiceErrorCode;
import com.traveler.post.global.swagger.ApiErrorCodeExamples;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Post", description = "게시글 관련 API (작성, 수정, 삭제)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/posts")
public class PostController {
    private final PostService postService;

    @Operation(summary = "게시글 생성", description = "새로운 게시글을 작성합니다. 이미지는 S3 키 리스트로 전달합니다.")
    @ApiErrorCodeExamples(
            post = {
                PostServiceErrorCode.POST_IMAGE_DUPLICATE,
                PostServiceErrorCode.POST_IMAGE_INVALID_KEY,
                PostServiceErrorCode.POST_IMAGE_NOT_UPLOADED,
                PostServiceErrorCode.POST_IMAGE_TOO_LARGE
            })
    @RequireAuth
    @PostMapping
    public ApiResponse<PostResponse.CreateDTO> createPost(
            @Valid @RequestBody PostRequest.CreateDTO dto, @Parameter(hidden = true) @LoginUser UserContext user) {
        return ApiResponse.onSuccess(SuccessCode.CREATED, postService.createPost(user.id(), dto));
    }

    @Operation(summary = "게시글 수정", description = "기존 게시글의 제목, 본문, 장소 및 이미지를 수정합니다.")
    @ApiErrorCodeExamples(
            value = {ErrorCode.FORBIDDEN},
            post = {
                PostServiceErrorCode.POST_NOT_FOUND,
                PostServiceErrorCode.POST_IMAGE_DUPLICATE,
                PostServiceErrorCode.POST_IMAGE_INVALID_KEY,
                PostServiceErrorCode.POST_IMAGE_NOT_UPLOADED,
                PostServiceErrorCode.POST_IMAGE_TOO_LARGE,
                PostServiceErrorCode.POST_ALREADY_DELETED
            })
    @RequireAuth
    @PatchMapping("/{postId}")
    public ApiResponse<PostResponse.UpdateDTO> updatePost(
            @Parameter(description = "게시글 ID", example = "1") @PathVariable Long postId,
            @Valid @RequestBody PostRequest.UpdateDTO dto,
            @Parameter(hidden = true) @LoginUser UserContext user) {
        return ApiResponse.onSuccess(SuccessCode.OK, postService.updatePost(postId, user.id(), dto));
    }

    @Operation(summary = "게시글 삭제", description = "게시글을 삭제(Soft Delete) 처리합니다.")
    @ApiErrorCodeExamples(
            value = {ErrorCode.FORBIDDEN},
            post = {PostServiceErrorCode.POST_NOT_FOUND})
    @RequireAuth
    @DeleteMapping("/{postId}")
    public ApiResponse<PostResponse.DeleteDTO> deletePost(
            @Parameter(description = "게시글 ID", example = "1") @PathVariable Long postId,
            @Parameter(hidden = true) @LoginUser UserContext user) {
        return ApiResponse.onSuccess(SuccessCode.OK, postService.deletePost(postId, user.id()));
    }
}
