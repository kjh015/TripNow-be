package com.traveler.web.domain.useractivity.facade;

import com.traveler.common.core.response.PageResponse;
import com.traveler.web.domain.useractivity.adapter.HistoryClientAdapter;
import com.traveler.web.domain.useractivity.client.dto.response.HistoryClientResponse;
import com.traveler.web.domain.useractivity.dto.response.HistoryResponse;
import com.traveler.web.domain.useractivity.enums.FailStage;
import com.traveler.web.domain.useractivity.enums.HistoryStatus;
import com.traveler.web.domain.useractivity.mapper.HistoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HistoryFacade {
    private final HistoryClientAdapter historyClientAdapter;

    private final HistoryMapper historyMapper;

    public PageResponse<HistoryResponse.ListDTO> getHistories(
            HistoryStatus status, FailStage stage, Pageable pageable) {
        PageResponse<HistoryClientResponse.ListDTO> clientResponse =
                historyClientAdapter.getHistories(status, stage, pageable);
        return clientResponse.map(historyMapper::toResponseListDTO);
    }

    public HistoryResponse.DetailDTO getHistory(String historyId) {
        HistoryClientResponse.DetailDTO clientResponse = historyClientAdapter.getHistory(historyId);
        return historyMapper.toResponseDetailDTO(clientResponse);
    }
}
