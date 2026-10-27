package com.traveler.web.domain.useractivity.adapter;

import com.traveler.common.core.response.PageResponse;
import com.traveler.web.domain.useractivity.client.HistoryClient;
import com.traveler.web.domain.useractivity.client.dto.response.HistoryClientResponse;
import com.traveler.web.domain.useractivity.enums.FailStage;
import com.traveler.web.domain.useractivity.enums.HistoryStatus;
import com.traveler.web.global.resilience.DownstreamCallExecutor;
import com.traveler.web.global.resilience.DownstreamServiceNames;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HistoryClientAdapter {
    private final HistoryClient historyClient;
    private final DownstreamCallExecutor downstreamCallExecutor;

    public PageResponse<HistoryClientResponse.ListDTO> getHistories(
            HistoryStatus status, FailStage stage, Pageable pageable) {
        return downstreamCallExecutor.execute(
                DownstreamServiceNames.USER_ACTIVITY,
                () -> historyClient.getHistories(status, stage, pageable).result());
    }

    public HistoryClientResponse.DetailDTO getHistory(String historyId) {
        return downstreamCallExecutor.execute(
                DownstreamServiceNames.USER_ACTIVITY,
                () -> historyClient.getHistory(historyId).result());
    }
}
