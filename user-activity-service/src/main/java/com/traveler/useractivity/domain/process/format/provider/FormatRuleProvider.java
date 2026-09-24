package com.traveler.useractivity.domain.process.format.provider;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.traveler.useractivity.domain.process.format.model.ActiveFormatRule;
import com.traveler.useractivity.domain.rule.format.repository.FormatRuleRepository;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FormatRuleProvider {
    private final FormatRuleRepository formatRuleRepository;
    private final ObjectMapper objectMapper;

    @Cacheable(cacheNames = "activeFormatRulesCache", key = "#logProcessId")
    public List<ActiveFormatRule> getActiveFormatRules(Long logProcessId) {
        return formatRuleRepository
                .findAllByLogProcessIdAndLogProcessIsDeletedFalseAndIsActiveTrueOrderByIdAsc(logProcessId)
                .stream()
                .map(rule -> new ActiveFormatRule(
                        rule.getId(),
                        convertNodeToMap(rule.getDefaultValues()),
                        convertNodeToMap(rule.getFieldMappings())))
                .toList();
    }

    // JsonNode -> Map<String, String> 변환 헬퍼 메서드
    private Map<String, String> convertNodeToMap(JsonNode node) {
        if (node == null || node.isNull() || node.isEmpty()) {
            return Map.of();
        }
        return objectMapper.convertValue(node, new TypeReference<Map<String, String>>() {});
    }
}
