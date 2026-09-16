package com.traveler.useractivity.domain.rule.process.service.command;

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

        eventPublisher.publishEvent(new LogProcessEvent.EvictByCode());

        return logProcessMapper.toDeleteDTO(logProcess);
    }
}
