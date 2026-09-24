package com.traveler.web.domain.useractivity.facade;

import com.traveler.common.core.response.PageResponse;
import com.traveler.web.domain.useractivity.adapter.FilterRuleClientAdapter;
import com.traveler.web.domain.useractivity.client.dto.response.FilterRuleClientResponse;
import com.traveler.web.domain.useractivity.dto.request.FilterRuleRequest;
import com.traveler.web.domain.useractivity.dto.response.FilterRuleResponse;
import com.traveler.web.domain.useractivity.mapper.FilterRuleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FilterRuleFacade {
    private final FilterRuleClientAdapter filterRuleClientAdapter;
    private final FilterRuleMapper filterRuleMapper;

    public FilterRuleResponse.CreateDTO createFilterRule(Long logProcessId, FilterRuleRequest.CreateDTO dto) {
        FilterRuleClientResponse.CreateDTO clientResponse =
                filterRuleClientAdapter.createFilterRule(logProcessId, filterRuleMapper.toClientCreateDTO(dto));
        return filterRuleMapper.toResponseCreateDTO(clientResponse);
    }

    public FilterRuleResponse.UpdateDTO updateFilterRule(Long filterRuleId, FilterRuleRequest.UpdateDTO dto) {
        FilterRuleClientResponse.UpdateDTO clientResponse =
                filterRuleClientAdapter.updateFilterRule(filterRuleId, filterRuleMapper.toClientUpdateDTO(dto));
        return filterRuleMapper.toResponseUpdateDTO(clientResponse);
    }

    public FilterRuleResponse.DeleteDTO deleteFilterRule(Long filterRuleId) {
        FilterRuleClientResponse.DeleteDTO clientResponse = filterRuleClientAdapter.deleteFilterRule(filterRuleId);
        return filterRuleMapper.toResponseDeleteDTO(clientResponse);
    }

    public PageResponse<FilterRuleResponse.ListDTO> getFilterRules(Long logProcessId, Pageable pageable) {
        PageResponse<FilterRuleClientResponse.ListDTO> clientResponse =
                filterRuleClientAdapter.getFilterRules(logProcessId, pageable);
        return clientResponse.map(filterRuleMapper::toResponseListDTO);
    }

    public FilterRuleResponse.DetailDTO getFilterRule(Long filterRuleId) {
        FilterRuleClientResponse.DetailDTO clientResponse = filterRuleClientAdapter.getFilterRule(filterRuleId);
        return filterRuleMapper.toResponseDetailDTO(clientResponse);
    }
}
