package com.traveler.web.domain.useractivity.adapter;

import com.traveler.common.core.response.PageResponse;
import com.traveler.web.domain.useractivity.client.LogProcessClient;
import com.traveler.web.domain.useractivity.client.dto.request.LogProcessClientRequest;
import com.traveler.web.domain.useractivity.client.dto.response.LogProcessClientResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LogProcessClientAdapter {
    private final LogProcessClient logProcessClient;

    public LogProcessClientResponse.CreateDTO createLogProcess(LogProcessClientRequest.CreateDTO dto) {
        return logProcessClient.createLogProcess(dto).result();
    }

    public LogProcessClientResponse.UpdateDTO updateLogProcess(
            Long logProcessId, LogProcessClientRequest.UpdateDTO dto) {
        return logProcessClient.updateLogProcess(logProcessId, dto).result();
    }

    public LogProcessClientResponse.DeleteDTO deleteLogProcess(Long logProcessId) {
        return logProcessClient.deleteLogProcess(logProcessId).result();
    }

    public PageResponse<LogProcessClientResponse.ListDTO> getLogProcesses(Pageable pageable) {
        return logProcessClient.getLogProcesses(pageable).result();
    }
}
