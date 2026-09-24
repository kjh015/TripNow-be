package com.traveler.useractivity.domain.process.core.provider;

import com.traveler.useractivity.domain.process.core.model.ActiveLogProcess;
import com.traveler.useractivity.domain.rule.process.entity.LogProcess;
import com.traveler.useractivity.domain.rule.process.repository.LogProcessRepository;
import com.traveler.useractivity.global.cache.constant.CacheConstants;
import com.traveler.useractivity.global.exception.UserActivityServiceException;
import com.traveler.useractivity.global.exception.code.UserActivityServiceErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LogProcessProvider {
    private final LogProcessRepository logProcessRepository;

    @Cacheable(cacheNames = CacheConstants.LOG_PROCESS_BY_CODE, key = "#code")
    public ActiveLogProcess getByCode(String code) {
        return logProcessRepository
                .findByName(code)
                .map(this::toActiveLogProcess)
                .orElseThrow(
                        () -> new UserActivityServiceException(UserActivityServiceErrorCode.LOG_PROCESS_NOT_FOUND));
    }

    private ActiveLogProcess toActiveLogProcess(LogProcess logProcess) {
        return new ActiveLogProcess(logProcess.getId(), logProcess.getName());
    }
}
