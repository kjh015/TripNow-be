package com.traveler.web.domain.useractivity.client;

import com.traveler.common.core.response.ApiResponse;
import com.traveler.common.core.response.PageResponse;
import com.traveler.web.domain.useractivity.client.dto.request.DedupRuleClientRequest;
import com.traveler.web.domain.useractivity.client.dto.response.DedupRuleClientResponse;
import com.traveler.web.global.feign.config.FeignClientConfig;
import com.traveler.web.global.resilience.DownstreamServiceNames;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@FeignClient(
        name = DownstreamServiceNames.USER_ACTIVITY,
        contextId = "DedupRuleClient",
        path = "/v1/admin",
        configuration = FeignClientConfig.class)
public interface DedupRuleClient {
    @PostMapping("/log-processes/{logProcessId}/dedup-rules")
    ApiResponse<DedupRuleClientResponse.CreateDTO> createDedupRule(
            @PathVariable Long logProcessId, @RequestBody DedupRuleClientRequest.CreateDTO dto);

    @PatchMapping("/dedup-rules/{dedupRuleId}")
    ApiResponse<DedupRuleClientResponse.UpdateDTO> updateDedupRule(
            @PathVariable Long dedupRuleId, @RequestBody DedupRuleClientRequest.UpdateDTO dto);

    @DeleteMapping("/dedup-rules/{dedupRuleId}")
    ApiResponse<DedupRuleClientResponse.DeleteDTO> deleteDedupRule(@PathVariable Long dedupRuleId);

    @GetMapping("/log-processes/{logProcessId}/dedup-rules")
    ApiResponse<PageResponse<DedupRuleClientResponse.ListDTO>> getDedupRules(
            @PathVariable Long logProcessId, @SpringQueryMap Pageable pageable);

    @GetMapping("/dedup-rules/{dedupRuleId}")
    ApiResponse<DedupRuleClientResponse.DetailDTO> getDedupRule(@PathVariable Long dedupRuleId);
}
