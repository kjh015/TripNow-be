package com.traveler.web.domain.post.client;

import com.traveler.common.core.response.ApiResponse;
import com.traveler.common.core.response.PageResponse;
import com.traveler.web.domain.post.client.dto.response.AdminCommentClientResponse;
import com.traveler.web.global.feign.config.FeignClientConfig;
import com.traveler.web.global.resilience.DownstreamServiceNames;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@FeignClient(
        name = DownstreamServiceNames.POST,
        contextId = "AdminCommentClient",
        path = "/v1/admin/comments",
        configuration = FeignClientConfig.class)
public interface AdminCommentClient {
    @GetMapping
    ApiResponse<PageResponse<AdminCommentClientResponse.ListDTO>> getComments(
            @RequestParam(value = "postId", required = false) Long postId,
            @RequestParam(value = "deleted", required = false) Boolean deleted,
            @SpringQueryMap Pageable pageable);

    @DeleteMapping("/{commentId}")
    ApiResponse<AdminCommentClientResponse.DeleteDTO> deleteComment(@PathVariable Long commentId);

    @PatchMapping("/{commentId}/restore")
    ApiResponse<AdminCommentClientResponse.RestoreDTO> restoreComment(@PathVariable Long commentId);
}
