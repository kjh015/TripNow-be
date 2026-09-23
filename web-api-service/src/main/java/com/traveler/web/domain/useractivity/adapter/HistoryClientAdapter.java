package com.traveler.web.domain.useractivity.adapter;

import com.traveler.common.core.response.PageResponse;
import com.traveler.web.domain.useractivity.client.HistoryClient;
import com.traveler.web.domain.useractivity.client.dto.response.HistoryClientResponse;
import com.traveler.web.domain.useractivity.enums.FailStage;
import com.traveler.web.domain.useractivity.enums.HistoryStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HistoryClientAdapter {
    private final HistoryClient historyClient;

    public PageResponse<HistoryClientResponse.ListDTO> getHistories(
            HistoryStatus status, FailStage stage, Pageable pageable) {
        return historyClient.getHistories(status, stage, pageable).result();
    }

    public HistoryClientResponse.DetailDTO getHistory(String historyId) {
        return historyClient.getHistory(historyId).result();
    }
}
