package com.traveler.web.domain.useractivity.adapter;

import com.traveler.common.core.response.PageResponse;
import com.traveler.web.domain.useractivity.client.FormatRuleClient;
import com.traveler.web.domain.useractivity.client.dto.request.FormatRuleClientRequest;
import com.traveler.web.domain.useractivity.client.dto.response.FormatRuleClientResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FormatRuleClientAdapter {
    private final FormatRuleClient formatRuleClient;

    public FormatRuleClientResponse.CreateDTO createFormatRule(
            Long logProcessId, FormatRuleClientRequest.CreateDTO dto) {
        return formatRuleClient.createFormatRule(logProcessId, dto).result();
    }

    public FormatRuleClientResponse.UpdateDTO updateFormatRule(
            Long formatRuleId, FormatRuleClientRequest.UpdateDTO dto) {
        return formatRuleClient.updateFormatRule(formatRuleId, dto).result();
    }

    public FormatRuleClientResponse.DeleteDTO deleteFormatRule(Long formatRuleId) {
        return formatRuleClient.deleteFormatRule(formatRuleId).result();
    }

    public PageResponse<FormatRuleClientResponse.ListDTO> getFormatRules(Long logProcessId, Pageable pageable) {
        return formatRuleClient.getFormatRules(logProcessId, pageable).result();
    }

    public FormatRuleClientResponse.DetailDTO getFormatRule(Long formatRuleId) {
        return formatRuleClient.getFormatRule(formatRuleId).result();
    }

    public FormatRuleClientResponse.FieldDTO getActiveFormatRuleFields(Long logProcessId) {
        return formatRuleClient.getActiveFormatRuleFields(logProcessId).result();
    }
}
