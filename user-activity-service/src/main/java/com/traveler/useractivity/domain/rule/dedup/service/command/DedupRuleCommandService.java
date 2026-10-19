package com.traveler.useractivity.domain.rule.dedup.service.command;

import static com.traveler.useractivity.global.audit.UserActivityServiceAuditAction.CREATE_DEDUP_RULE;
import static com.traveler.useractivity.global.audit.UserActivityServiceAuditAction.DELETE_DEDUP_RULE;
import static com.traveler.useractivity.global.audit.UserActivityServiceAuditAction.UPDATE_DEDUP_RULE;

import com.traveler.common.support.logging.audit.AuditLogger;
import com.traveler.useractivity.domain.rule.dedup.dto.event.DedupRuleEvent;
import com.traveler.useractivity.domain.rule.dedup.dto.request.DedupRuleRequest;
import com.traveler.useractivity.domain.rule.dedup.dto.response.DedupRuleResponse;
import com.traveler.useractivity.domain.rule.dedup.entity.DedupRule;
import com.traveler.useractivity.domain.rule.dedup.mapper.DedupRuleMapper;
import com.traveler.useractivity.domain.rule.dedup.repository.DedupRuleRepository;
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
public class DedupRuleCommandService {
    private final LogProcessRepository logProcessRepository;
    private final DedupRuleRepository dedupRuleRepository;
    private final DedupRuleMapper dedupRuleMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditLogger auditLogger;

    public DedupRuleResponse.CreateDTO createDedupRule(Long logProcessId, DedupRuleRequest.CreateDTO dto) {
        LogProcess logProcess = logProcessRepository
                .findById(logProcessId)
                .orElseThrow(
                        () -> new UserActivityServiceException(UserActivityServiceErrorCode.LOG_PROCESS_NOT_FOUND));

        DedupRule dedupRule = dedupRuleMapper.toCreateEntity(dto, logProcess);

        DedupRule savedDedupRule = dedupRuleRepository.save(dedupRule);
        auditLogger
                .event(CREATE_DEDUP_RULE, savedDedupRule.getId())
                .detail("logProcessId", logProcessId)
                .detail("isActive", savedDedupRule.isActive())
                .log();

        if (savedDedupRule.isActive()) {
            eventPublisher.publishEvent(new DedupRuleEvent.Evict(logProcessId));
        }

        return dedupRuleMapper.toCreateDTO(savedDedupRule);
    }

    public DedupRuleResponse.UpdateDTO updateDedupRule(Long dedupRuleId, DedupRuleRequest.UpdateDTO dto) {
        DedupRule dedupRule = dedupRuleRepository
                .findById(dedupRuleId)
                .orElseThrow(() -> new UserActivityServiceException(UserActivityServiceErrorCode.DEDUP_RULE_NOT_FOUND));

        boolean wasActive = dedupRule.isActive();
        dedupRule.update(dto.name(), dto.rules(), dto.isActive());

        Long logProcessId = dedupRule.getLogProcess().getId();
        auditLogger
                .event(UPDATE_DEDUP_RULE, dedupRuleId)
                .detail("logProcessId", logProcessId)
                .detail("isActive", wasActive + "->" + dedupRule.isActive())
                .log();
        eventPublisher.publishEvent(new DedupRuleEvent.Evict(logProcessId));

        return dedupRuleMapper.toUpdateDTO(dedupRule);
    }

    public DedupRuleResponse.DeleteDTO deleteDedupRule(Long dedupRuleId) {
        DedupRule dedupRule = dedupRuleRepository
                .findById(dedupRuleId)
                .orElseThrow(() -> new UserActivityServiceException(UserActivityServiceErrorCode.DEDUP_RULE_NOT_FOUND));

        dedupRule.delete();

        Long logProcessId = dedupRule.getLogProcess().getId();
        auditLogger
                .event(DELETE_DEDUP_RULE, dedupRuleId)
                .detail("logProcessId", logProcessId)
                .log();
        eventPublisher.publishEvent(new DedupRuleEvent.Evict(logProcessId));

        return dedupRuleMapper.toDeleteDTO(dedupRule);
    }
}
