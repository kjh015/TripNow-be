package com.traveler.useractivity.domain.rule.filter.service.command;

import static com.traveler.useractivity.global.audit.UserActivityServiceAuditAction.CREATE_FILTER_RULE;
import static com.traveler.useractivity.global.audit.UserActivityServiceAuditAction.DELETE_FILTER_RULE;
import static com.traveler.useractivity.global.audit.UserActivityServiceAuditAction.UPDATE_FILTER_RULE;

import com.traveler.common.support.logging.audit.AuditLogger;
import com.traveler.useractivity.domain.rule.filter.converter.SpelExpressionConverter;
import com.traveler.useractivity.domain.rule.filter.dto.event.FilterRuleEvent;
import com.traveler.useractivity.domain.rule.filter.dto.request.FilterRuleRequest;
import com.traveler.useractivity.domain.rule.filter.dto.response.FilterRuleResponse;
import com.traveler.useractivity.domain.rule.filter.entity.FilterRule;
import com.traveler.useractivity.domain.rule.filter.mapper.FilterRuleMapper;
import com.traveler.useractivity.domain.rule.filter.repository.FilterRuleRepository;
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
public class FilterRuleCommandService {
    private final LogProcessRepository logProcessRepository;
    private final FilterRuleRepository filterRuleRepository;
    private final FilterRuleMapper filterRuleMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditLogger auditLogger;

    public FilterRuleResponse.CreateDTO createFilterRule(Long logProcessId, FilterRuleRequest.CreateDTO dto) {
        LogProcess logProcess = logProcessRepository
                .findById(logProcessId)
                .orElseThrow(
                        () -> new UserActivityServiceException(UserActivityServiceErrorCode.LOG_PROCESS_NOT_FOUND));

        // SpEL 표현식 변환
        String expression = SpelExpressionConverter.convert(dto.conditions());

        FilterRule filterRule = filterRuleMapper.toCreateEntity(dto, logProcess, expression);

        FilterRule savedRule = filterRuleRepository.save(filterRule);
        auditLogger
                .event(CREATE_FILTER_RULE, savedRule.getId())
                .detail("logProcessId", logProcessId)
                .detail("isActive", savedRule.isActive())
                .log();

        if (savedRule.isActive()) {
            eventPublisher.publishEvent(new FilterRuleEvent.Evict(logProcessId));
        }

        return filterRuleMapper.toCreateDTO(savedRule);
    }

    public FilterRuleResponse.UpdateDTO updateFilterRule(Long filterRuleId, FilterRuleRequest.UpdateDTO dto) {
        FilterRule filterRule = filterRuleRepository
                .findById(filterRuleId)
                .orElseThrow(
                        () -> new UserActivityServiceException(UserActivityServiceErrorCode.FILTER_RULE_NOT_FOUND));

        Long logProcessId = filterRule.getLogProcess().getId();
        String expression = SpelExpressionConverter.convert(dto.conditions());

        boolean wasActive = filterRule.isActive();
        filterRule.update(dto.name(), expression, dto.conditions(), dto.isActive());
        auditLogger
                .event(UPDATE_FILTER_RULE, filterRuleId)
                .detail("logProcessId", logProcessId)
                .detail("isActive", wasActive + "->" + filterRule.isActive())
                .log();

        eventPublisher.publishEvent(new FilterRuleEvent.Evict(logProcessId));

        return filterRuleMapper.toUpdateDTO(filterRule);
    }

    public FilterRuleResponse.DeleteDTO deleteFilterRule(Long filterRuleId) {
        FilterRule filterRule = filterRuleRepository
                .findById(filterRuleId)
                .orElseThrow(
                        () -> new UserActivityServiceException(UserActivityServiceErrorCode.FILTER_RULE_NOT_FOUND));
        Long logProcessId = filterRule.getLogProcess().getId();

        filterRule.delete();
        auditLogger
                .event(DELETE_FILTER_RULE, filterRuleId)
                .detail("logProcessId", logProcessId)
                .log();

        eventPublisher.publishEvent(new FilterRuleEvent.Evict(logProcessId));

        return filterRuleMapper.toDeleteDTO(filterRule);
    }
}
