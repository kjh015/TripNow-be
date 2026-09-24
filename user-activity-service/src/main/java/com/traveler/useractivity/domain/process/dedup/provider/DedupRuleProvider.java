package com.traveler.useractivity.domain.process.dedup.provider;

import com.traveler.useractivity.domain.process.dedup.model.ActiveDedupRule;
import com.traveler.useractivity.domain.rule.dedup.repository.DedupRuleRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DedupRuleProvider {
    private final DedupRuleRepository dedupRuleRepository;

    @Cacheable(cacheNames = "activeDedupRulesCache", key = "#logProcessId")
    public List<ActiveDedupRule> getActiveDedupRules(Long logProcessId) {
        return dedupRuleRepository
                .findAllByLogProcessIdAndLogProcessIsDeletedFalseAndIsActiveTrueOrderByIdAsc(logProcessId)
                .stream()
                .map(rule -> new ActiveDedupRule(rule.getId(), rule.getName(), rule.getRules()))
                .toList();
    }
}
