package com.traveler.web.domain.useractivity.facade;

import com.traveler.common.core.response.PageResponse;
import com.traveler.web.domain.useractivity.adapter.LogProcessClientAdapter;
import com.traveler.web.domain.useractivity.client.dto.response.LogProcessClientResponse;
import com.traveler.web.domain.useractivity.dto.request.LogProcessRequest;
import com.traveler.web.domain.useractivity.dto.response.LogProcessResponse;
import com.traveler.web.domain.useractivity.mapper.LogProcessMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LogProcessFacade {
    private final LogProcessClientAdapter logProcessClientAdapter;
    private final LogProcessMapper logProcessMapper;

    public LogProcessResponse.CreateDTO createLogProcess(LogProcessRequest.CreateDTO dto) {
        LogProcessClientResponse.CreateDTO clientResponse =
                logProcessClientAdapter.createLogProcess(logProcessMapper.toClientCreateDTO(dto));
        return logProcessMapper.toResponseCreateDTO(clientResponse);
    }

    public LogProcessResponse.UpdateDTO updateLogProcess(Long logProcessId, LogProcessRequest.UpdateDTO dto) {
        LogProcessClientResponse.UpdateDTO clientResponse =
                logProcessClientAdapter.updateLogProcess(logProcessId, logProcessMapper.toClientUpdateDTO(dto));
        return logProcessMapper.toResponseUpdateDTO(clientResponse);
    }

    public LogProcessResponse.DeleteDTO deleteLogProcess(Long logProcessId) {
        LogProcessClientResponse.DeleteDTO clientResponse = logProcessClientAdapter.deleteLogProcess(logProcessId);
        return logProcessMapper.toResponseDeleteDTO(clientResponse);
    }

    public PageResponse<LogProcessResponse.ListDTO> getLogProcesses(Pageable pageable) {
        PageResponse<LogProcessClientResponse.ListDTO> clientResponse =
                logProcessClientAdapter.getLogProcesses(pageable);
        return clientResponse.map(logProcessMapper::toResponseListDTO);
    }
}
