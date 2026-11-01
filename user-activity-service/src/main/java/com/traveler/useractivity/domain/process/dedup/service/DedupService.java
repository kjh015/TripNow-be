package com.traveler.useractivity.domain.process.dedup.service;

import com.traveler.useractivity.domain.process.dedup.model.ActiveDedupRule;
import com.traveler.useractivity.domain.process.dedup.model.DedupResult;
import com.traveler.useractivity.domain.process.dedup.repository.DedupHistoryRepository;
import com.traveler.useractivity.domain.rule.dedup.definition.DedupSpec;
import com.traveler.useractivity.domain.rule.dedup.enums.MatchType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class DedupService {
    private final DedupHistoryRepository dedupHistoryRepository;

    /**
     * 로그를 평가하여, 중복으로 판정된 첫 번째 DedupRule을 반환합니다.
     */
    public Optional<DedupResult> findFirstDuplicatedRule(
            Map<String, String> logData, List<ActiveDedupRule> activeDedupRules) {

        if (activeDedupRules == null || activeDedupRules.isEmpty()) {
            return Optional.empty();
        }

        for (ActiveDedupRule rule : activeDedupRules) {
            for (DedupSpec spec : rule.specs()) {
                // 1. 현재 로그가 해당 스펙의 조건 대상인지 확인
                if (!matchesConditions(spec.conditions(), logData)) {
                    continue; // 대상이 아니면 다음 스펙 검사
                }

                // 2. 대상인 경우 Redis 원자적 저장(중복 검사) 수행
                if (isDuplicateEntry(rule, spec, logData)) {
                    // 중복 발견 시 런타임 컨텍스트를 즉시 반환
                    return Optional.of(new DedupResult(rule, spec));
                }
            }
        }

        return Optional.empty();
    }

    // =========================================================================
    // 💡 Private Helper Methods
    // =========================================================================
    /**
     * 현재 로그가 해당 조건식들을 모두 만족하여 '중복 검사 대상'이 되는지 확인합니다.
     */
    private boolean matchesConditions(List<DedupSpec.Condition> conditions, Map<String, String> logData) {
        // "모든(allMatch) 조건이 충족되어야 검사 대상이다"
        return conditions.stream().allMatch(cond -> matchesCondition(cond, logData));
    }

    /**
     * 단일 조건(Condition)을 만족하는지 검사합니다.
     */
    private boolean matchesCondition(DedupSpec.Condition cond, Map<String, String> logData) {
        String logValue = logData.get(cond.field());

        if (logValue == null) {
            return false;
        }
        if (cond.matchType() == MatchType.EXACT_MATCH) {
            return logValue.equals(cond.value());
        }
        return true; // ANY_VALUE 인 경우
    }

    /**
     * Redis에 원자적 저장을 시도하고 실제 중복 여부를 반환합니다.
     */
    private boolean isDuplicateEntry(ActiveDedupRule rule, DedupSpec spec, Map<String, String> logData) {
        String dedupKey = generateDedupKey(spec.conditions(), logData);
        long ttlSeconds = spec.expirationTime().toTotalSeconds();

        boolean isSaved = dedupHistoryRepository.saveIfAbsent(rule.dedupRuleId(), dedupKey, ttlSeconds);

        if (!isSaved) {
            log.debug("중복 로그 발견 (규칙명: {}, 키해시: {})", rule.name(), dedupKey);
            return true; // 중복됨
        }
        return false; // 중복 아님 (최초 진입으로 저장 성공)
    }

    /**
     * 조건식의 필드명과 로그의 실제 값을 조합하여 고유한 해시/키를 생성합니다.
     * 예: "action:click|user_id:user123"
     */
    private String generateDedupKey(List<DedupSpec.Condition> conditions, Map<String, String> logData) {
        String canonical = conditions.stream()
                .collect(Collectors.toMap(
                        DedupSpec.Condition::field,
                        cond -> logData.getOrDefault(cond.field(), ""),
                        (left, right) -> left,
                        TreeMap::new))
                .entrySet()
                .stream()
                .map(entry -> entry.getKey() + ":" + entry.getValue())
                .collect(Collectors.joining("|"));
        return sha256(canonical);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
