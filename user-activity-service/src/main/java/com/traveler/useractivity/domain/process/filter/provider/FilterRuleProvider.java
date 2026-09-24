package com.traveler.useractivity.domain.process.filter.provider;

import com.traveler.useractivity.domain.process.filter.model.ActiveFilterRule;
import com.traveler.useractivity.domain.rule.filter.repository.FilterRuleRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FilterRuleProvider {
    private final FilterRuleRepository filterRuleRepository;

    @Cacheable(cacheNames = "activeFilterRulesCache", key = "#logProcessId")
    public List<ActiveFilterRule> getActiveFilterRules(Long logProcessId) {
        return filterRuleRepository
                .findAllByLogProcessIdAndLogProcessIsDeletedFalseAndIsActiveTrueOrderByIdAsc(logProcessId)
                .stream()
                .map(rule -> new ActiveFilterRule(rule.getId(), rule.getName(), rule.getExpression()))
                .toList();
    }
}
