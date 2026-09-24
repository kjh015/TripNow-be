package com.traveler.useractivity.domain.rule.process.service.command;

import com.traveler.useractivity.domain.rule.dedup.dto.event.DedupRuleEvent;
import com.traveler.useractivity.domain.rule.dedup.entity.DedupRule;
import com.traveler.useractivity.domain.rule.dedup.repository.DedupRuleRepository;
import com.traveler.useractivity.domain.rule.filter.dto.event.FilterRuleEvent;
import com.traveler.useractivity.domain.rule.filter.entity.FilterRule;
import com.traveler.useractivity.domain.rule.filter.repository.FilterRuleRepository;
import com.traveler.useractivity.domain.rule.format.dto.event.FormatRuleEvent;
import com.traveler.useractivity.domain.rule.format.entity.FormatRule;
import com.traveler.useractivity.domain.rule.format.repository.FormatRuleRepository;
import com.traveler.useractivity.domain.rule.process.dto.event.LogProcessEvent;
import com.traveler.useractivity.domain.rule.process.dto.request.LogProcessRequest;
import com.traveler.useractivity.domain.rule.process.dto.response.LogProcessResponse;
import com.traveler.useractivity.domain.rule.process.entity.LogProcess;
import com.traveler.useractivity.domain.rule.process.mapper.LogProcessMapper;
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
public class LogProcessCommandService {
    private final LogProcessRepository logProcessRepository;
    private final FormatRuleRepository formatRuleRepository;
    private final FilterRuleRepository filterRuleRepository;
    private final DedupRuleRepository dedupRuleRepository;
    private final LogProcessMapper logProcessMapper;
    private final ApplicationEventPublisher eventPublisher;

    public LogProcessResponse.CreateDTO createLogProcess(LogProcessRequest.CreateDTO dto) {
        // 이름은 파이프라인이 프로세스를 찾는 코드이므로 중복을 막음 (검사 없이 두면 DB 제약 위반이 500으로 나감)
        if (logProcessRepository.existsByName(dto.name())) {
            throw new UserActivityServiceException(UserActivityServiceErrorCode.LOG_PROCESS_NAME_DUPLICATED);
        }

        LogProcess logProcess = logProcessMapper.toCreateEntity(dto);

        LogProcess savedLogProcess = logProcessRepository.save(logProcess);

        return logProcessMapper.toCreateDTO(savedLogProcess);
    }

    public LogProcessResponse.UpdateDTO updateLogProcess(Long logProcessId, LogProcessRequest.UpdateDTO dto) {
        LogProcess logProcess = logProcessRepository
                .findById(logProcessId)
                .orElseThrow(
                        () -> new UserActivityServiceException(UserActivityServiceErrorCode.LOG_PROCESS_NOT_FOUND));

        if (logProcessRepository.existsByNameAndIdNot(dto.name(), logProcessId)) {
            throw new UserActivityServiceException(UserActivityServiceErrorCode.LOG_PROCESS_NAME_DUPLICATED);
        }

        logProcess.update(dto.name(), dto.description());

        eventPublisher.publishEvent(new LogProcessEvent.EvictByCode());

        return logProcessMapper.toUpdateDTO(logProcess);
    }

    public LogProcessResponse.DeleteDTO deleteLogProcess(Long logProcessId) {
        LogProcess logProcess = logProcessRepository
                .findById(logProcessId)
                .orElseThrow(
                        () -> new UserActivityServiceException(UserActivityServiceErrorCode.LOG_PROCESS_NOT_FOUND));

        logProcess.delete();

        // 하위 규칙도 함께 소프트 삭제해 관리 화면·파이프라인 어디에도 남지 않게 한다
        formatRuleRepository.findAllByLogProcessId(logProcessId).forEach(FormatRule::delete);
        filterRuleRepository.findAllByLogProcessId(logProcessId).forEach(FilterRule::delete);
        dedupRuleRepository.findAllByLogProcessId(logProcessId).forEach(DedupRule::delete);

        eventPublisher.publishEvent(new LogProcessEvent.EvictByCode());
        eventPublisher.publishEvent(new FormatRuleEvent.Evict(logProcessId));
        eventPublisher.publishEvent(new FilterRuleEvent.Evict(logProcessId));
        eventPublisher.publishEvent(new DedupRuleEvent.Evict(logProcessId));

        return logProcessMapper.toDeleteDTO(logProcess);
    }
}
