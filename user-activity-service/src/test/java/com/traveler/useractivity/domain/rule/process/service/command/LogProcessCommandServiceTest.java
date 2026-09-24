package com.traveler.useractivity.domain.rule.process.service.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

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
import com.traveler.useractivity.domain.rule.process.entity.LogProcess;
import com.traveler.useractivity.domain.rule.process.mapper.LogProcessMapper;
import com.traveler.useractivity.domain.rule.process.repository.LogProcessRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class LogProcessCommandServiceTest {

    private static final Long LOG_PROCESS_ID = 1L;

    @Mock
    private LogProcessRepository logProcessRepository;

    @Mock
    private FormatRuleRepository formatRuleRepository;

    @Mock
    private FilterRuleRepository filterRuleRepository;

    @Mock
    private DedupRuleRepository dedupRuleRepository;

    @Mock
    private LogProcessMapper logProcessMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private LogProcessCommandService logProcessCommandService;

    @Test
    @DisplayName("프로세스를 삭제하면 하위 규칙도 함께 소프트 삭제하고 규칙 캐시를 비운다")
    void deleteLogProcessSoftDeletesChildRules() {
        LogProcess logProcess = LogProcess.builder().name("process").build();
        FormatRule formatRule =
                FormatRule.builder().logProcess(logProcess).isActive(true).build();
        FilterRule filterRule =
                FilterRule.builder().logProcess(logProcess).isActive(true).build();
        DedupRule dedupRule =
                DedupRule.builder().logProcess(logProcess).isActive(true).build();

        given(logProcessRepository.findById(LOG_PROCESS_ID)).willReturn(Optional.of(logProcess));
        given(formatRuleRepository.findAllByLogProcessId(LOG_PROCESS_ID)).willReturn(List.of(formatRule));
        given(filterRuleRepository.findAllByLogProcessId(LOG_PROCESS_ID)).willReturn(List.of(filterRule));
        given(dedupRuleRepository.findAllByLogProcessId(LOG_PROCESS_ID)).willReturn(List.of(dedupRule));

        logProcessCommandService.deleteLogProcess(LOG_PROCESS_ID);

        assertThat(logProcess.isDeleted()).isTrue();
        assertThat(formatRule.isDeleted()).isTrue();
        assertThat(filterRule.isDeleted()).isTrue();
        assertThat(dedupRule.isDeleted()).isTrue();

        verify(eventPublisher).publishEvent(new LogProcessEvent.EvictByCode());
        verify(eventPublisher).publishEvent(new FormatRuleEvent.Evict(LOG_PROCESS_ID));
        verify(eventPublisher).publishEvent(new FilterRuleEvent.Evict(LOG_PROCESS_ID));
        verify(eventPublisher).publishEvent(new DedupRuleEvent.Evict(LOG_PROCESS_ID));
    }
}
