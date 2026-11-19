package com.traveler.web.domain.useractivity.client;

import com.traveler.common.core.response.ApiResponse;
import com.traveler.common.core.response.PageResponse;
import com.traveler.web.domain.useractivity.client.dto.request.FormatRuleClientRequest;
import com.traveler.web.domain.useractivity.client.dto.response.FormatRuleClientResponse;
import com.traveler.web.global.feign.config.FeignClientConfig;
import com.traveler.web.global.resilience.DownstreamServiceNames;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@FeignClient(
        name = DownstreamServiceNames.USER_ACTIVITY,
        contextId = "FormatRuleClient",
        path = "/v1/admin",
        configuration = FeignClientConfig.class)
public interface FormatRuleClient {
    @PostMapping("/log-processes/{logProcessId}/format-rules")
    ApiResponse<FormatRuleClientResponse.CreateDTO> createFormatRule(
            @PathVariable Long logProcessId, @RequestBody FormatRuleClientRequest.CreateDTO dto);

    @PatchMapping("/format-rules/{formatRuleId}")
    ApiResponse<FormatRuleClientResponse.UpdateDTO> updateFormatRule(
            @PathVariable Long formatRuleId, @RequestBody FormatRuleClientRequest.UpdateDTO dto);

    @DeleteMapping("/format-rules/{formatRuleId}")
    ApiResponse<FormatRuleClientResponse.DeleteDTO> deleteFormatRule(@PathVariable Long formatRuleId);

    @GetMapping("/log-processes/{logProcessId}/format-rules")
    ApiResponse<PageResponse<FormatRuleClientResponse.ListDTO>> getFormatRules(
            @PathVariable Long logProcessId, @SpringQueryMap Pageable pageable);

    @GetMapping("/format-rules/{formatRuleId}")
    ApiResponse<FormatRuleClientResponse.DetailDTO> getFormatRule(@PathVariable Long formatRuleId);

    @GetMapping("/log-processes/{logProcessId}/format-rules/fields")
    ApiResponse<FormatRuleClientResponse.FieldDTO> getActiveFormatRuleFields(@PathVariable Long logProcessId);
}
