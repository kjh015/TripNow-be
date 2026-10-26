package com.traveler.web.domain.useractivity.adapter;

import com.traveler.common.core.response.PageResponse;
import com.traveler.web.domain.useractivity.client.DedupRuleClient;
import com.traveler.web.domain.useractivity.client.dto.request.DedupRuleClientRequest;
import com.traveler.web.domain.useractivity.client.dto.response.DedupRuleClientResponse;
import com.traveler.web.global.resilience.DownstreamCallExecutor;
import com.traveler.web.global.resilience.DownstreamServiceNames;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DedupRuleClientAdapter {
    private final DedupRuleClient dedupRuleClient;
    private final DownstreamCallExecutor downstreamCallExecutor;

    public DedupRuleClientResponse.CreateDTO createDedupRule(Long logProcessId, DedupRuleClientRequest.CreateDTO dto) {
        return downstreamCallExecutor.execute(
                DownstreamServiceNames.USER_ACTIVITY,
                () -> dedupRuleClient.createDedupRule(logProcessId, dto).result());
    }

    public DedupRuleClientResponse.UpdateDTO updateDedupRule(Long dedupRuleId, DedupRuleClientRequest.UpdateDTO dto) {
        return downstreamCallExecutor.execute(
                DownstreamServiceNames.USER_ACTIVITY,
                () -> dedupRuleClient.updateDedupRule(dedupRuleId, dto).result());
    }

    public DedupRuleClientResponse.DeleteDTO deleteDedupRule(Long dedupRuleId) {
        return downstreamCallExecutor.execute(
                DownstreamServiceNames.USER_ACTIVITY,
                () -> dedupRuleClient.deleteDedupRule(dedupRuleId).result());
    }

    public PageResponse<DedupRuleClientResponse.ListDTO> getDedupRules(Long logProcessId, Pageable pageable) {
        return downstreamCallExecutor.execute(
                DownstreamServiceNames.USER_ACTIVITY,
                () -> dedupRuleClient.getDedupRules(logProcessId, pageable).result());
    }

    public DedupRuleClientResponse.DetailDTO getDedupRule(Long dedupRuleId) {
        return downstreamCallExecutor.execute(
                DownstreamServiceNames.USER_ACTIVITY,
                () -> dedupRuleClient.getDedupRule(dedupRuleId).result());
    }
}
