package com.traveler.web.domain.useractivity.adapter;

import com.traveler.common.core.response.PageResponse;
import com.traveler.web.domain.useractivity.client.FilterRuleClient;
import com.traveler.web.domain.useractivity.client.dto.request.FilterRuleClientRequest;
import com.traveler.web.domain.useractivity.client.dto.response.FilterRuleClientResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FilterRuleClientAdapter {
    private final FilterRuleClient filterRuleClient;

    public FilterRuleClientResponse.CreateDTO createFilterRule(
            Long logProcessId, FilterRuleClientRequest.CreateDTO dto) {
        return filterRuleClient.createFilterRule(logProcessId, dto).result();
    }

    public FilterRuleClientResponse.UpdateDTO updateFilterRule(
            Long filterRuleId, FilterRuleClientRequest.UpdateDTO dto) {
        return filterRuleClient.updateFilterRule(filterRuleId, dto).result();
    }

    public FilterRuleClientResponse.DeleteDTO deleteFilterRule(Long filterRuleId) {
        return filterRuleClient.deleteFilterRule(filterRuleId).result();
    }

    public PageResponse<FilterRuleClientResponse.ListDTO> getFilterRules(Long logProcessId, Pageable pageable) {
        return filterRuleClient.getFilterRules(logProcessId, pageable).result();
    }

    public FilterRuleClientResponse.DetailDTO getFilterRule(Long filterRuleId) {
        return filterRuleClient.getFilterRule(filterRuleId).result();
    }
}
