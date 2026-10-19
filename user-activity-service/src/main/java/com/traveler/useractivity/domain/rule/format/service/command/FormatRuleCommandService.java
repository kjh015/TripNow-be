package com.traveler.useractivity.domain.rule.format.service.command;

import static com.traveler.useractivity.global.audit.UserActivityServiceAuditAction.CREATE_FORMAT_RULE;
import static com.traveler.useractivity.global.audit.UserActivityServiceAuditAction.DELETE_FORMAT_RULE;
import static com.traveler.useractivity.global.audit.UserActivityServiceAuditAction.UPDATE_FORMAT_RULE;

import com.traveler.common.support.logging.audit.AuditLogger;
import com.traveler.useractivity.domain.rule.format.dto.event.FormatRuleEvent;
import com.traveler.useractivity.domain.rule.format.dto.request.FormatRuleRequest;
import com.traveler.useractivity.domain.rule.format.dto.response.FormatRuleResponse;
import com.traveler.useractivity.domain.rule.format.entity.FormatRule;
import com.traveler.useractivity.domain.rule.format.mapper.FormatRuleMapper;
import com.traveler.useractivity.domain.rule.format.repository.FormatRuleRepository;
import com.traveler.useractivity.domain.rule.process.entity.LogProcess;
import com.traveler.useractivity.domain.rule.process.repository.LogProcessRepository;
import com.traveler.useractivity.global.exception.UserActivityServiceException;
import com.traveler.useractivity.global.exception.code.UserActivityServiceErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class FormatRuleCommandService {
    private final LogProcessRepository logProcessRepository;
    private final FormatRuleRepository formatRuleRepository;
    private final FormatRuleMapper formatRuleMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditLogger auditLogger;

    public FormatRuleResponse.CreateDTO createFormatRule(Long logProcessId, FormatRuleRequest.CreateDTO dto) {
        LogProcess logProcess = logProcessRepository
                .findById(logProcessId)
                .orElseThrow(
                        () -> new UserActivityServiceException(UserActivityServiceErrorCode.LOG_PROCESS_NOT_FOUND));

        FormatRule formatRule = formatRuleMapper.toCreateEntity(dto, logProcess);

        FormatRule savedFormatRule = formatRuleRepository.save(formatRule);
        auditLogger
                .event(CREATE_FORMAT_RULE, savedFormatRule.getId())
                .detail("logProcessId", logProcessId)
                .detail("isActive", savedFormatRule.isActive())
                .log();

        if (savedFormatRule.isActive()) {
            eventPublisher.publishEvent(new FormatRuleEvent.Evict(logProcessId));
        }

        return formatRuleMapper.toCreateDTO(savedFormatRule);
    }

    public FormatRuleResponse.UpdateDTO updateFormatRule(Long formatRuleId, FormatRuleRequest.UpdateDTO dto) {
        FormatRule formatRule = formatRuleRepository
                .findById(formatRuleId)
                .orElseThrow(
                        () -> new UserActivityServiceException(UserActivityServiceErrorCode.FORMAT_RULE_NOT_FOUND));

        Long logProcessId = formatRule.getLogProcess().getId();
        boolean wasActive = formatRule.isActive();
        formatRule.update(dto.name(), dto.defaultValues(), dto.fieldMappings(), dto.isActive());
        auditLogger
                .event(UPDATE_FORMAT_RULE, formatRuleId)
                .detail("logProcessId", logProcessId)
                .detail("isActive", wasActive + "->" + formatRule.isActive())
                .log();

        eventPublisher.publishEvent(new FormatRuleEvent.Evict(logProcessId));

        return formatRuleMapper.toUpdateDTO(formatRule);
    }

    public FormatRuleResponse.DeleteDTO deleteFormatRule(Long formatRuleId) {
        FormatRule formatRule = formatRuleRepository
                .findById(formatRuleId)
                .orElseThrow(
                        () -> new UserActivityServiceException(UserActivityServiceErrorCode.FORMAT_RULE_NOT_FOUND));
        Long logProcessId = formatRule.getLogProcess().getId();
        formatRule.delete();
        auditLogger
                .event(DELETE_FORMAT_RULE, formatRuleId)
                .detail("logProcessId", logProcessId)
                .log();

        eventPublisher.publishEvent(new FormatRuleEvent.Evict(logProcessId));
        return formatRuleMapper.toDeleteDTO(formatRule);
    }
}
