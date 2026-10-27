package com.traveler.web.domain.useractivity.adapter;

import com.traveler.common.core.response.PageResponse;
import com.traveler.web.domain.useractivity.client.FormatRuleClient;
import com.traveler.web.domain.useractivity.client.dto.request.FormatRuleClientRequest;
import com.traveler.web.domain.useractivity.client.dto.response.FormatRuleClientResponse;
import com.traveler.web.global.resilience.DownstreamCallExecutor;
import com.traveler.web.global.resilience.DownstreamServiceNames;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FormatRuleClientAdapter {
    private final FormatRuleClient formatRuleClient;
    private final DownstreamCallExecutor downstreamCallExecutor;

    public FormatRuleClientResponse.CreateDTO createFormatRule(
            Long logProcessId, FormatRuleClientRequest.CreateDTO dto) {
        return downstreamCallExecutor.execute(
                DownstreamServiceNames.USER_ACTIVITY,
                () -> formatRuleClient.createFormatRule(logProcessId, dto).result());
    }

    public FormatRuleClientResponse.UpdateDTO updateFormatRule(
            Long formatRuleId, FormatRuleClientRequest.UpdateDTO dto) {
        return downstreamCallExecutor.execute(
                DownstreamServiceNames.USER_ACTIVITY,
                () -> formatRuleClient.updateFormatRule(formatRuleId, dto).result());
    }

    public FormatRuleClientResponse.DeleteDTO deleteFormatRule(Long formatRuleId) {
        return downstreamCallExecutor.execute(
                DownstreamServiceNames.USER_ACTIVITY,
                () -> formatRuleClient.deleteFormatRule(formatRuleId).result());
    }

    public PageResponse<FormatRuleClientResponse.ListDTO> getFormatRules(Long logProcessId, Pageable pageable) {
        return downstreamCallExecutor.execute(
                DownstreamServiceNames.USER_ACTIVITY,
                () -> formatRuleClient.getFormatRules(logProcessId, pageable).result());
    }

    public FormatRuleClientResponse.DetailDTO getFormatRule(Long formatRuleId) {
        return downstreamCallExecutor.execute(
                DownstreamServiceNames.USER_ACTIVITY,
                () -> formatRuleClient.getFormatRule(formatRuleId).result());
    }

    public FormatRuleClientResponse.FieldDTO getActiveFormatRuleFields(Long logProcessId) {
        return downstreamCallExecutor.execute(
                DownstreamServiceNames.USER_ACTIVITY,
                () -> formatRuleClient.getActiveFormatRuleFields(logProcessId).result());
    }
}
