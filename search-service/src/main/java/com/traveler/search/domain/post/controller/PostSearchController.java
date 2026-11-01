package com.traveler.search.domain.post.controller;

import com.traveler.common.api.auth.annotation.LoginUser;
import com.traveler.common.api.auth.annotation.RequireAuth;
import com.traveler.common.core.auth.UserContext;
import com.traveler.common.core.code.SuccessCode;
import com.traveler.common.core.response.ApiResponse;
import com.traveler.common.core.response.CursorResponse;
import com.traveler.common.core.response.PageResponse;
import com.traveler.search.domain.post.dto.request.PostSearchRequest;
import com.traveler.search.domain.post.dto.response.PostSearchResponse;
import com.traveler.search.domain.post.service.PostSearchQueryService;
import com.traveler.search.global.exception.code.SearchServiceErrorCode;
import com.traveler.search.global.swagger.ApiErrorCodeExamples;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Post Search", description = "게시글 조회 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/search/posts")
public class PostSearchController {
    private final PostSearchQueryService postSearchQueryService;

    @Operation(summary = "게시글 통합 검색", description = "키워드, 카테고리, 지역 필터를 통해 게시글을 검색합니다. 정렬 조건(정확도, 최신순, 인기순 등)을 지원합니다.")
    @GetMapping
    public ApiResponse<PageResponse<PostSearchResponse.SearchDTO>> search(
            @ParameterObject @Valid @ModelAttribute PostSearchRequest.SearchDTO dto) {
        return ApiResponse.onSuccess(SuccessCode.OK, postSearchQueryService.search(dto));
    }

    @Operation(
            summary = "게시글 피드 조회",
            description =
                    "검색 조건에 맞는 게시글을 cursor 기반으로 조회합니다. 첫 요청에는 cursor를 생략하고, 다음 페이지부터는 응답의 nextCursor를 전달합니다. hasNext가 false이면 더 이상 조회할 게시글이 없습니다.")
    @ApiErrorCodeExamples(search = {SearchServiceErrorCode.POST_INVALID_CURSOR})
    @GetMapping("/feed")
    public ApiResponse<CursorResponse<PostSearchResponse.SearchDTO>> getFeed(
            @ParameterObject @Valid @ModelAttribute PostSearchRequest.FeedDTO dto) {
        return ApiResponse.onSuccess(SuccessCode.OK, postSearchQueryService.getFeed(dto));
    }

    @Operation(summary = "게시글 상세 조회", description = "게시글의 상세 정보를 조회합니다.")
    @ApiErrorCodeExamples(search = {SearchServiceErrorCode.POST_NOT_FOUND})
    @GetMapping("/{postId}")
    public ApiResponse<PostSearchResponse.DetailDTO> getPost(
            @Parameter(description = "게시글 ID", example = "101") @PathVariable Long postId) {
        return ApiResponse.onSuccess(SuccessCode.OK, postSearchQueryService.getPostDetail(postId));
    }

    @Operation(summary = "검색어 자동완성", description = "입력 중인 키워드에 대해 게시글 제목 기반의 자동완성 목록을 제공합니다. 초성 검색을 지원합니다.")
    @GetMapping("/autocomplete")
    public ApiResponse<PostSearchResponse.AutocompleteDTO> autocomplete(
            @Parameter(description = "검색 키워드", example = "경주")
                    @RequestParam
                    @NotBlank(message = "검색어는 공백일 수 없습니다.")
                    @Size(min = 1, message = "최소 1자 이상 입력해야 합니다.")
                    String keyword) {
        return ApiResponse.onSuccess(SuccessCode.OK, postSearchQueryService.autocomplete(keyword));
    }

    @Operation(summary = "내 게시글 검색", description = "내가 작성한 게시글을 검색 엔진 인덱스에서 조회합니다.")
    @RequireAuth
    @GetMapping("/me")
    public ApiResponse<PageResponse<PostSearchResponse.MyDTO>> getMyPosts(
            @Parameter(hidden = true) @LoginUser UserContext user,
            @ParameterObject @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
                    Pageable pageable) {
        return ApiResponse.onSuccess(SuccessCode.OK, postSearchQueryService.getMyPosts(user.id(), pageable));
    }
}
