package com.traveler.useractivity.domain.process.filter.service;

import com.traveler.useractivity.domain.process.filter.engine.SpelExpressionEvaluator;
import com.traveler.useractivity.domain.process.filter.model.ActiveFilterRule;
import com.traveler.useractivity.global.exception.UserActivityServiceException;
import com.traveler.useractivity.global.exception.code.UserActivityServiceErrorCode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.ParseException;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class FilterService {

    /**
     * 로그를 평가하여, 통과하지 못한 첫 번째 필터 규칙(FilterRule)을 반환합니다.
     * 모든 규칙을 무사히 통과했다면 Optional.empty()를 반환합니다.
     */
    public Optional<ActiveFilterRule> findFirstFailedRule(
            Map<String, String> logData, List<ActiveFilterRule> activeFilterRules) {

        if (activeFilterRules == null || activeFilterRules.isEmpty()) {
            return Optional.empty(); // 적용할 필터가 없으면 모두 통과한 것으로 간주
        }

        // activeFilterRules 중 로그와 매치되지 않는(실패한) 첫 번째 룰을 찾아 반환
        return activeFilterRules.stream()
                .filter(rule -> !evaluateLog(rule, logData))
                .findFirst();
    }

    // =========================================================================
    // 💡 Private Helper Methods
    // =========================================================================

    /**
     * 필터 규칙(FilterRule)을 기준으로 로그를 평가합니다.
     */
    private boolean evaluateLog(ActiveFilterRule rule, Map<String, String> logData) {
        String expression = rule.expression();

        // SpEL 엔진으로 조건식 평가
        boolean isPassed = executeEvaluation(rule, logData);

        if (!isPassed) {
            log.debug("필터 조건 불일치로 로그가 Drop 되었습니다. (필터명: {}, 조건식: {})", rule.name(), expression);
        }

        return isPassed;
    }

    private boolean executeEvaluation(ActiveFilterRule rule, Map<String, String> logData) {
        try {
            return SpelExpressionEvaluator.evaluate(rule.expression(), logData);
        } catch (ParseException e) {
            log.error("[SpEL Syntax Error] 필터 규칙 문법 오류 발생. 규칙명: [{}], 조건식: [{}]", rule.name(), rule.expression(), e);
            throw new UserActivityServiceException(UserActivityServiceErrorCode.INVALID_FILTER_RULE_SYNTAX, e);

        } catch (EvaluationException e) {
            log.warn(
                    "[SpEL Evaluation Error] 로그 데이터 필드 불일치 발생. 규칙명: [{}], 조건식: [{}]",
                    rule.name(),
                    rule.expression(),
                    e);
            throw new UserActivityServiceException(UserActivityServiceErrorCode.LOG_DATA_MISMATCH, e);
        }
    }
}
