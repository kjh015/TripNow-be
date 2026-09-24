package com.traveler.useractivity.domain.rule.filter.converter;

import com.traveler.useractivity.domain.rule.filter.enums.ComparisonOperator;
import com.traveler.useractivity.domain.rule.filter.enums.ValueType;
import com.traveler.useractivity.domain.rule.filter.vo.FilterNode;
import com.traveler.useractivity.global.exception.UserActivityServiceException;
import com.traveler.useractivity.global.exception.code.UserActivityServiceErrorCode;
import java.util.List;
import java.util.regex.Pattern;

public final class SpelExpressionConverter {

    private static final String SPEL_PREFIX = "#";
    private static final String SPEL_EQUALS_FORMAT = "(%1$s%2$s != null && %1$s%2$s matches '(?i)%3$s')";
    private static final String SPEL_DEFAULT_FORMAT = "(%1$s%2$s != null && %1$s%2$s %3$s %4$s)";
    // 필드명 스크립트 주입 방지를 위한 정규식 (영문 대소문자, 숫자, 언더바만 허용)
    private static final Pattern STRICT_FIELD_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

    private SpelExpressionConverter() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static String convert(List<FilterNode.Element> conditions) {
        if (conditions == null || conditions.isEmpty()) return "";

        StringBuilder spel = new StringBuilder();

        for (FilterNode.Element element : conditions) {
            switch (element) {
                case FilterNode.Condition cond -> appendCondition(spel, cond);
                case FilterNode.Operator op -> spel.append(" ")
                        .append(op.value().getValue())
                        .append(" ");
                case FilterNode.LeftParen lp -> spel.append("(");
                case FilterNode.RightParen rp -> spel.append(")");
                default -> throw new UserActivityServiceException(
                        UserActivityServiceErrorCode.FILTER_UNSUPPORTED_NODE_TYPE);
            }
        }
        return spel.toString();
    }

    private static void appendCondition(StringBuilder spel, FilterNode.Condition cond) {
        if (!STRICT_FIELD_PATTERN.matcher(cond.field()).matches()) {
            throw new UserActivityServiceException(UserActivityServiceErrorCode.FILTER_UNSUPPORTED_NODE_TYPE);
        }

        String safeValue = escapeSpelString(cond.value());

        if (cond.operator() == ComparisonOperator.EQUALS) {
            String regexSafeValue = Pattern.quote(safeValue);
            spel.append(SPEL_EQUALS_FORMAT.formatted(SPEL_PREFIX, cond.field(), regexSafeValue));
        } else {
            String formattedValue = formatValue(cond.valueType(), safeValue);
            spel.append(SPEL_DEFAULT_FORMAT.formatted(
                    SPEL_PREFIX, cond.field(), cond.operator().getValue(), formattedValue));
        }
    }

    private static String formatValue(ValueType valueType, String safeValue) {
        return switch (valueType) {
            case STRING -> "'%s'".formatted(safeValue);
            case INT -> {
                yield String.valueOf(Integer.parseInt(safeValue));
            }
            case DOUBLE -> {
                yield String.valueOf(Double.parseDouble(safeValue));
            }
            case BOOLEAN -> {
                if (!"true".equalsIgnoreCase(safeValue) && !"false".equalsIgnoreCase(safeValue)) {
                    throw new UserActivityServiceException(UserActivityServiceErrorCode.FILTER_UNSUPPORTED_NODE_TYPE);
                }
                yield safeValue.toLowerCase();
            }
        };
    }

    private static String escapeSpelString(String value) {
        if (value == null) return "";
        return value.replace("'", "''");
    }
}
