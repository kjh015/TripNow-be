package com.traveler.useractivity.domain.process.filter.engine;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;

public final class SpelExpressionEvaluator {

    private static final SpelExpressionParser PARSER = new SpelExpressionParser();

    // 파싱된 표현식 캐시. 상한 초과 시 오래 안 쓰인 항목부터 제거하고, 수정·삭제된 규칙의 옛 표현식은 접근이 끊기면 만료된다
    private static final Cache<String, Expression> EXPRESSION_CACHE = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterAccess(Duration.ofHours(1))
            .build();

    private static final Pattern INTEGER_PATTERN = Pattern.compile("-?\\d+");
    private static final Pattern DECIMAL_PATTERN = Pattern.compile("-?\\d+\\.\\d+");

    private SpelExpressionEvaluator() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * SpEL 표현식과 변수를 받아 조건의 참/거짓을 안전하게 평가합니다.
     */
    public static boolean evaluate(String expressionString, Map<String, String> variables) {
        if (expressionString == null || expressionString.isBlank()) {
            return false;
        }

        // 파싱 및 캐싱
        Expression expression = EXPRESSION_CACHE.get(expressionString, PARSER::parseExpression);

        // 변수 바인딩 (문자열 값을 실제 타입으로 변환하여 숫자/불리언 비교가 가능하도록 함)
        EvaluationContext context =
                SimpleEvaluationContext.forReadOnlyDataBinding().build();
        if (variables != null && !variables.isEmpty()) {
            variables.forEach((key, value) -> context.setVariable(key, coerceValue(value)));
        }

        // 평가 실행
        Boolean result = expression.getValue(context, Boolean.class);
        return Boolean.TRUE.equals(result);
    }

    /**
     * 문자열 값을 SpEL 비교에 적합한 타입(Long, Double, Boolean)으로 변환합니다.
     * 변환할 수 없는 값은 문자열 그대로 반환합니다.
     */
    private static Object coerceValue(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        try {
            if (INTEGER_PATTERN.matcher(trimmed).matches()) {
                return Long.parseLong(trimmed);
            }
            if (DECIMAL_PATTERN.matcher(trimmed).matches()) {
                return Double.parseDouble(trimmed);
            }
        } catch (NumberFormatException e) {
            // Long 범위를 벗어나는 등 파싱 불가 시 문자열 유지
            return value;
        }
        if ("true".equalsIgnoreCase(trimmed) || "false".equalsIgnoreCase(trimmed)) {
            return Boolean.parseBoolean(trimmed);
        }
        return value;
    }
}
