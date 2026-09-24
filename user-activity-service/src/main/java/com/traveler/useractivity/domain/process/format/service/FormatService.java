package com.traveler.useractivity.domain.process.format.service;

import com.traveler.useractivity.domain.process.format.engine.UrlQueryParser;
import com.traveler.useractivity.domain.process.format.message.RawLog;
import com.traveler.useractivity.domain.process.format.model.ActiveFormatRule;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class FormatService {
    private final UrlQueryParser urlQueryParser;

    /**
     * 원본 로그에서 매핑의 재료가 될 데이터를 추출하고,
     * JSON 형태의 포맷 규칙(FormatRule)에 맞게 표준화된 로그 맵을 생성합니다.
     */
    public Map<String, String> formatLog(RawLog rawLog, List<ActiveFormatRule> activeFormatRules) {
        // 매핑의 재료가 될 출처(Source) 데이터 추출
        Map<String, String> source = extractSource(rawLog);

        if (activeFormatRules == null || activeFormatRules.isEmpty()) {
            log.warn("활성화된 포맷 규칙(FormatRule)이 없습니다. 로그를 폐기합니다.");
            return Map.of();
        }

        // 기본값을 먼저 깔고(뒤 규칙의 기본값이 우선), 그 위에 매핑 값을 덮어써 최종 포맷팅된 로그 생성
        Map<String, String> formattedLog = new HashMap<>();
        for (ActiveFormatRule rule : activeFormatRules) {
            putDefaultValues(rule.defaultValues(), formattedLog);
        }
        for (ActiveFormatRule rule : activeFormatRules) {
            putFieldMappings(rule.fieldMappings(), source, formattedLog);
        }

        return formattedLog;
    }

    // =========================================================================
    // 💡 Private Helper Methods
    // =========================================================================

    /**
     * RawLog에서 매핑에 사용할 원본 데이터(Source)를 추출하여 반환합니다.
     * (현재는 path의 쿼리 파라미터를 파싱하여 사용합니다)
     */
    private Map<String, String> extractSource(RawLog rawLog) {
        String path = rawLog.path() != null ? rawLog.path() : "";
        return urlQueryParser.extractQueryParams(path);
    }

    /**
     * 설정된 기본값(defaultValues)을 포맷팅된 로그에 추가합니다.
     * 매핑 적용 전에 호출되어, 매핑 값이 없는 필드의 바닥값 역할을 합니다.
     */
    private void putDefaultValues(Map<String, String> defaultValues, Map<String, String> formattedLog) {
        if (defaultValues == null || defaultValues.isEmpty()) {
            return;
        }
        // JsonNode 루프를 돌 필요 없이 putAll 한 줄로 끝납니다.
        formattedLog.putAll(defaultValues);
    }

    /**
     * 추출된 원본 데이터(Source)를 기반으로 필드 매핑(fieldMappings) 규칙을 적용합니다.
     * 유효한 값이 존재할 경우 포맷팅된 로그에 추가(덮어쓰기)합니다.
     */
    private void putFieldMappings(
            Map<String, String> fieldMappings, Map<String, String> source, Map<String, String> formattedLog) {

        if (fieldMappings == null || fieldMappings.isEmpty()) {
            return;
        }

        // 순수 Java Map 루프 처리
        fieldMappings.forEach((targetKey, sourceKey) -> {
            String value = source.get(sourceKey);
            if (value != null && !value.isBlank()) {
                formattedLog.put(targetKey, value);
            }
        });
    }
}
