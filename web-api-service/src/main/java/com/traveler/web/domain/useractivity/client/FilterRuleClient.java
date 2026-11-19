package com.traveler.web.domain.useractivity.client;

import com.traveler.common.core.response.ApiResponse;
import com.traveler.common.core.response.PageResponse;
import com.traveler.web.domain.useractivity.client.dto.request.FilterRuleClientRequest;
import com.traveler.web.domain.useractivity.client.dto.response.FilterRuleClientResponse;
import com.traveler.web.global.feign.config.FeignClientConfig;
import com.traveler.web.global.resilience.DownstreamServiceNames;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@FeignClient(
        name = DownstreamServiceNames.USER_ACTIVITY,
        contextId = "FilterRuleClient",
        path = "/v1/admin",
        configuration = FeignClientConfig.class)
public interface FilterRuleClient {
    @PostMapping("/log-processes/{logProcessId}/filter-rules")
    ApiResponse<FilterRuleClientResponse.CreateDTO> createFilterRule(
            @PathVariable Long logProcessId, @RequestBody FilterRuleClientRequest.CreateDTO dto);

    @PatchMapping("/filter-rules/{filterRuleId}")
    ApiResponse<FilterRuleClientResponse.UpdateDTO> updateFilterRule(
            @PathVariable Long filterRuleId, @RequestBody FilterRuleClientRequest.UpdateDTO dto);

    @DeleteMapping("/filter-rules/{filterRuleId}")
    ApiResponse<FilterRuleClientResponse.DeleteDTO> deleteFilterRule(@PathVariable Long filterRuleId);

    @GetMapping("/log-processes/{logProcessId}/filter-rules")
    ApiResponse<PageResponse<FilterRuleClientResponse.ListDTO>> getFilterRules(
            @PathVariable Long logProcessId, @SpringQueryMap Pageable pageable);

    @GetMapping("/filter-rules/{filterRuleId}")
    ApiResponse<FilterRuleClientResponse.DetailDTO> getFilterRule(@PathVariable Long filterRuleId);
}
