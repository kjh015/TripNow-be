package com.traveler.web.domain.useractivity.client;

import com.traveler.common.core.response.ApiResponse;
import com.traveler.common.core.response.PageResponse;
import com.traveler.web.domain.useractivity.client.dto.response.HistoryClientResponse;
import com.traveler.web.domain.useractivity.enums.FailStage;
import com.traveler.web.domain.useractivity.enums.HistoryStatus;
import com.traveler.web.global.feign.config.FeignClientConfig;
import com.traveler.web.global.resilience.DownstreamServiceNames;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
        name = DownstreamServiceNames.USER_ACTIVITY,
        contextId = "HistoryClient",
        path = "/v1/admin/histories",
        configuration = FeignClientConfig.class)
public interface HistoryClient {
    @GetMapping
    ApiResponse<PageResponse<HistoryClientResponse.ListDTO>> getHistories(
            @RequestParam HistoryStatus status, @RequestParam FailStage stage, @SpringQueryMap Pageable pageable);

    @GetMapping("/{historyId}")
    ApiResponse<HistoryClientResponse.DetailDTO> getHistory(@PathVariable String historyId);
}
