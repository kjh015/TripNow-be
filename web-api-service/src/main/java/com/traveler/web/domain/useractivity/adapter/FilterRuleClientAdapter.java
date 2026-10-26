package com.traveler.web.domain.useractivity.adapter;

import com.traveler.common.core.response.PageResponse;
import com.traveler.web.domain.useractivity.client.FilterRuleClient;
import com.traveler.web.domain.useractivity.client.dto.request.FilterRuleClientRequest;
import com.traveler.web.domain.useractivity.client.dto.response.FilterRuleClientResponse;
import com.traveler.web.global.resilience.DownstreamCallExecutor;
import com.traveler.web.global.resilience.DownstreamServiceNames;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FilterRuleClientAdapter {
    private final FilterRuleClient filterRuleClient;
    private final DownstreamCallExecutor downstreamCallExecutor;

    public FilterRuleClientResponse.CreateDTO createFilterRule(
            Long logProcessId, FilterRuleClientRequest.CreateDTO dto) {
        return downstreamCallExecutor.execute(
                DownstreamServiceNames.USER_ACTIVITY,
                () -> filterRuleClient.createFilterRule(logProcessId, dto).result());
    }

    public FilterRuleClientResponse.UpdateDTO updateFilterRule(
            Long filterRuleId, FilterRuleClientRequest.UpdateDTO dto) {
        return downstreamCallExecutor.execute(
                DownstreamServiceNames.USER_ACTIVITY,
                () -> filterRuleClient.updateFilterRule(filterRuleId, dto).result());
    }

    public FilterRuleClientResponse.DeleteDTO deleteFilterRule(Long filterRuleId) {
        return downstreamCallExecutor.execute(
                DownstreamServiceNames.USER_ACTIVITY,
                () -> filterRuleClient.deleteFilterRule(filterRuleId).result());
    }

    public PageResponse<FilterRuleClientResponse.ListDTO> getFilterRules(Long logProcessId, Pageable pageable) {
        return downstreamCallExecutor.execute(
                DownstreamServiceNames.USER_ACTIVITY,
                () -> filterRuleClient.getFilterRules(logProcessId, pageable).result());
    }

    public FilterRuleClientResponse.DetailDTO getFilterRule(Long filterRuleId) {
        return downstreamCallExecutor.execute(
                DownstreamServiceNames.USER_ACTIVITY,
                () -> filterRuleClient.getFilterRule(filterRuleId).result());
    }
}
