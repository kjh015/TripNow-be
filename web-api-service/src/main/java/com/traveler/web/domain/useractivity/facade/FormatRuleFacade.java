package com.traveler.web.domain.useractivity.facade;

import com.traveler.common.core.response.PageResponse;
import com.traveler.web.domain.useractivity.adapter.FormatRuleClientAdapter;
import com.traveler.web.domain.useractivity.client.dto.response.FormatRuleClientResponse;
import com.traveler.web.domain.useractivity.dto.request.FormatRuleRequest;
import com.traveler.web.domain.useractivity.dto.response.FormatRuleResponse;
import com.traveler.web.domain.useractivity.mapper.FormatRuleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FormatRuleFacade {
    private final FormatRuleClientAdapter formatRuleClientAdapter;
    private final FormatRuleMapper formatRuleMapper;

    public FormatRuleResponse.CreateDTO createFormatRule(Long logProcessId, FormatRuleRequest.CreateDTO dto) {
        FormatRuleClientResponse.CreateDTO clientResponse =
                formatRuleClientAdapter.createFormatRule(logProcessId, formatRuleMapper.toClientCreateDTO(dto));
        return formatRuleMapper.toResponseCreateDTO(clientResponse);
    }

    public FormatRuleResponse.UpdateDTO updateFormatRule(Long formatRuleId, FormatRuleRequest.UpdateDTO dto) {
        FormatRuleClientResponse.UpdateDTO clientResponse =
                formatRuleClientAdapter.updateFormatRule(formatRuleId, formatRuleMapper.toClientUpdateDTO(dto));
        return formatRuleMapper.toResponseUpdateDTO(clientResponse);
    }

    public FormatRuleResponse.DeleteDTO deleteFormatRule(Long formatRuleId) {
        FormatRuleClientResponse.DeleteDTO clientResponse = formatRuleClientAdapter.deleteFormatRule(formatRuleId);
        return formatRuleMapper.toResponseDeleteDTO(clientResponse);
    }

    public PageResponse<FormatRuleResponse.ListDTO> getFormatRules(Long logProcessId, Pageable pageable) {
        PageResponse<FormatRuleClientResponse.ListDTO> clientResponse =
                formatRuleClientAdapter.getFormatRules(logProcessId, pageable);
        return clientResponse.map(formatRuleMapper::toResponseListDTO);
    }

    public FormatRuleResponse.DetailDTO getFormatRule(Long formatRuleId) {
        FormatRuleClientResponse.DetailDTO clientResponse = formatRuleClientAdapter.getFormatRule(formatRuleId);
        return formatRuleMapper.toResponseDetailDTO(clientResponse);
    }

    public FormatRuleResponse.FieldDTO getActiveFormatRuleFields(Long logProcessId) {
        FormatRuleClientResponse.FieldDTO clientResponse =
                formatRuleClientAdapter.getActiveFormatRuleFields(logProcessId);
        return formatRuleMapper.toResponseFieldDTO(clientResponse);
    }
}
