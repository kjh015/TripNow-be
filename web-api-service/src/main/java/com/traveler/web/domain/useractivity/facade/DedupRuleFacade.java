package com.traveler.web.domain.useractivity.facade;

import com.traveler.common.core.response.PageResponse;
import com.traveler.web.domain.useractivity.adapter.DedupRuleClientAdapter;
import com.traveler.web.domain.useractivity.client.dto.response.DedupRuleClientResponse;
import com.traveler.web.domain.useractivity.dto.request.DedupRuleRequest;
import com.traveler.web.domain.useractivity.dto.response.DedupRuleResponse;
import com.traveler.web.domain.useractivity.mapper.DedupRuleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DedupRuleFacade {
    private final DedupRuleClientAdapter dedupRuleClientAdapter;
    private final DedupRuleMapper dedupRuleMapper;

    public DedupRuleResponse.CreateDTO createDedupRule(Long logProcessId, DedupRuleRequest.CreateDTO dto) {
        DedupRuleClientResponse.CreateDTO clientResponse =
                dedupRuleClientAdapter.createDedupRule(logProcessId, dedupRuleMapper.toClientCreateDTO(dto));
        return dedupRuleMapper.toResponseCreateDTO(clientResponse);
    }

    public DedupRuleResponse.UpdateDTO updateDedupRule(Long dedupRuleId, DedupRuleRequest.UpdateDTO dto) {
        DedupRuleClientResponse.UpdateDTO clientResponse =
                dedupRuleClientAdapter.updateDedupRule(dedupRuleId, dedupRuleMapper.toClientUpdateDTO(dto));
        return dedupRuleMapper.toResponseUpdateDTO(clientResponse);
    }

    public DedupRuleResponse.DeleteDTO deleteDedupRule(Long dedupRuleId) {
        DedupRuleClientResponse.DeleteDTO clientResponse = dedupRuleClientAdapter.deleteDedupRule(dedupRuleId);
        return dedupRuleMapper.toResponseDeleteDTO(clientResponse);
    }

    public PageResponse<DedupRuleResponse.ListDTO> getDedupRules(Long logProcessId, Pageable pageable) {
        PageResponse<DedupRuleClientResponse.ListDTO> clientResponse =
                dedupRuleClientAdapter.getDedupRules(logProcessId, pageable);
        return clientResponse.map(dedupRuleMapper::toResponseListDTO);
    }

    public DedupRuleResponse.DetailDTO getDedupRule(Long dedupRuleId) {
        DedupRuleClientResponse.DetailDTO clientResponse = dedupRuleClientAdapter.getDedupRule(dedupRuleId);
        return dedupRuleMapper.toResponseDetailDTO(clientResponse);
    }
}
