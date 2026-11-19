package com.traveler.web.domain.useractivity.adapter;

import com.traveler.common.core.response.PageResponse;
import com.traveler.web.domain.useractivity.client.DedupRuleClient;
import com.traveler.web.domain.useractivity.client.dto.request.DedupRuleClientRequest;
import com.traveler.web.domain.useractivity.client.dto.response.DedupRuleClientResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DedupRuleClientAdapter {
    private final DedupRuleClient dedupRuleClient;

    public DedupRuleClientResponse.CreateDTO createDedupRule(Long logProcessId, DedupRuleClientRequest.CreateDTO dto) {
        return dedupRuleClient.createDedupRule(logProcessId, dto).result();
    }

    public DedupRuleClientResponse.UpdateDTO updateDedupRule(Long dedupRuleId, DedupRuleClientRequest.UpdateDTO dto) {
        return dedupRuleClient.updateDedupRule(dedupRuleId, dto).result();
    }

    public DedupRuleClientResponse.DeleteDTO deleteDedupRule(Long dedupRuleId) {
        return dedupRuleClient.deleteDedupRule(dedupRuleId).result();
    }

    public PageResponse<DedupRuleClientResponse.ListDTO> getDedupRules(Long logProcessId, Pageable pageable) {
        return dedupRuleClient.getDedupRules(logProcessId, pageable).result();
    }

    public DedupRuleClientResponse.DetailDTO getDedupRule(Long dedupRuleId) {
        return dedupRuleClient.getDedupRule(dedupRuleId).result();
    }
}
