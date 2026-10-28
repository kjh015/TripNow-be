package com.traveler.web.domain.post.controller;

import com.traveler.common.api.auth.annotation.RequireAuth;
import com.traveler.common.core.code.SuccessCode;
import com.traveler.common.core.response.ApiResponse;
import com.traveler.web.domain.post.dto.request.LikeRequest;
import com.traveler.web.domain.post.dto.response.LikeResponse;
import com.traveler.web.domain.post.facade.LikeFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Like", description = "좋아요 관련 API")
@RestController
@RequestMapping("/api/v1/likes")
@RequiredArgsConstructor
public class LikeController {
    private final LikeFacade likeFacade;

    @Operation(
            summary = "좋아요 등록",
            description = "특정 게시글에 좋아요를 추가하고 처리 직후의 좋아요 수를 돌려줍니다. 이미 좋아요를 누른 상태에서 재요청 시 추가 처리없이 성공 응답을 반환합니다.")
    @RequireAuth
    @PostMapping
    public ApiResponse<LikeResponse.StatsDTO> addLike(@Valid @RequestBody LikeRequest.AddDTO dto) {
        return ApiResponse.onSuccess(SuccessCode.CREATED, likeFacade.addLike(dto));
    }

    @Operation(
            summary = "좋아요 취소",
            description = "특정 게시글에 누른 좋아요를 취소하고 처리 직후의 좋아요 수를 돌려줍니다. 좋아요 기록이 없으면 아무 동작도 수행하지 않습니다.")
    @RequireAuth
    @DeleteMapping
    public ApiResponse<LikeResponse.StatsDTO> removeLike(
            @Parameter(description = "게시물 ID", example = "1") @RequestParam Long postId) {
        return ApiResponse.onSuccess(SuccessCode.OK, likeFacade.removeLike(postId));
    }
}
