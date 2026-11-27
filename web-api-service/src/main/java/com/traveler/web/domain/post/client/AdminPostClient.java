package com.traveler.web.domain.post.client;

import com.traveler.common.core.response.ApiResponse;
import com.traveler.common.core.response.PageResponse;
import com.traveler.web.domain.post.client.dto.response.AdminPostClientResponse;
import com.traveler.web.global.feign.config.FeignClientConfig;
import com.traveler.web.global.resilience.DownstreamServiceNames;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@FeignClient(
        name = DownstreamServiceNames.POST,
        contextId = "AdminPostClient",
        path = "/v1/admin/posts",
        configuration = FeignClientConfig.class)
public interface AdminPostClient {
    @GetMapping
    ApiResponse<PageResponse<AdminPostClientResponse.ListDTO>> getPosts(
            @RequestParam(value = "deleted", required = false) Boolean deleted, @SpringQueryMap Pageable pageable);

    @GetMapping("/{postId}")
    ApiResponse<AdminPostClientResponse.DetailDTO> getPost(@PathVariable Long postId);

    @DeleteMapping("/{postId}")
    ApiResponse<AdminPostClientResponse.DeleteDTO> deletePost(@PathVariable Long postId);

    @PatchMapping("/{postId}/restore")
    ApiResponse<AdminPostClientResponse.RestoreDTO> restorePost(@PathVariable Long postId);

    @DeleteMapping("/{postId}/permanent")
    ApiResponse<AdminPostClientResponse.PermanentDeleteDTO> permanentlyDeletePost(@PathVariable Long postId);
}
