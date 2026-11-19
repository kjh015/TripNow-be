package com.traveler.web.domain.useractivity.client;

import com.traveler.common.core.response.ApiResponse;
import com.traveler.common.core.response.PageResponse;
import com.traveler.web.domain.useractivity.client.dto.request.LogProcessClientRequest;
import com.traveler.web.domain.useractivity.client.dto.response.LogProcessClientResponse;
import com.traveler.web.global.feign.config.FeignClientConfig;
import com.traveler.web.global.resilience.DownstreamServiceNames;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@FeignClient(
        name = DownstreamServiceNames.USER_ACTIVITY,
        contextId = "LogProcessClient",
        path = "/v1/admin/log-processes",
        configuration = FeignClientConfig.class)
public interface LogProcessClient {
    @PostMapping
    ApiResponse<LogProcessClientResponse.CreateDTO> createLogProcess(
            @RequestBody LogProcessClientRequest.CreateDTO dto);

    @PatchMapping("/{logProcessId}")
    ApiResponse<LogProcessClientResponse.UpdateDTO> updateLogProcess(
            @PathVariable Long logProcessId, @RequestBody LogProcessClientRequest.UpdateDTO dto);

    @DeleteMapping("/{logProcessId}")
    ApiResponse<LogProcessClientResponse.DeleteDTO> deleteLogProcess(@PathVariable Long logProcessId);

    @GetMapping
    ApiResponse<PageResponse<LogProcessClientResponse.ListDTO>> getLogProcesses(@SpringQueryMap Pageable pageable);
}
