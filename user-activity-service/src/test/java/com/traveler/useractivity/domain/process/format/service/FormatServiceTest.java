package com.traveler.useractivity.domain.process.format.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.traveler.useractivity.domain.process.format.engine.UrlQueryParser;
import com.traveler.useractivity.domain.process.format.message.RawLog;
import com.traveler.useractivity.domain.process.format.model.ActiveFormatRule;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FormatServiceTest {

    private final FormatService formatService = new FormatService(new UrlQueryParser());

    @Test
    @DisplayName("뒤 규칙의 기본값이 앞 규칙의 매핑 값을 덮어쓰지 않는다")
    void mappingWinsOverLaterRuleDefault() {
        List<ActiveFormatRule> rules = List.of(
                new ActiveFormatRule(1L, Map.of(), Map.of("postId", "dimension1")),
                new ActiveFormatRule(2L, Map.of("postId", "unknown"), Map.of()));

        Map<String, String> result = formatService.formatLog(rawLog("/matomo.php?dimension1=42"), rules);

        assertThat(result).containsEntry("postId", "42");
    }

    @Test
    @DisplayName("매핑할 원본 값이 없으면 기본값이 남는다")
    void defaultRemainsWhenSourceMissing() {
        List<ActiveFormatRule> rules = List.of(
                new ActiveFormatRule(1L, Map.of(), Map.of("postId", "dimension1")),
                new ActiveFormatRule(2L, Map.of("postId", "unknown"), Map.of()));

        Map<String, String> result = formatService.formatLog(rawLog("/matomo.php?dimension2=x"), rules);

        assertThat(result).containsEntry("postId", "unknown");
    }

    @Test
    @DisplayName("기본값끼리는 뒤 규칙의 값이 우선한다")
    void laterDefaultWinsOverEarlierDefault() {
        List<ActiveFormatRule> rules = List.of(
                new ActiveFormatRule(1L, Map.of("a", "1"), Map.of()),
                new ActiveFormatRule(2L, Map.of("a", "2"), Map.of()));

        Map<String, String> result = formatService.formatLog(rawLog("/matomo.php"), rules);

        assertThat(result).containsExactlyEntriesOf(Map.of("a", "2"));
    }

    @Test
    @DisplayName("규칙이 하나면 기본값 위에 매핑 값을 적용하고, 빈 원본 값은 무시한다")
    void singleRuleBehavesAsBefore() {
        List<ActiveFormatRule> rules = List.of(new ActiveFormatRule(
                1L,
                Map.of("postId", "unknown", "category", "none", "eventType", "View"),
                Map.of("postId", "dimension1", "category", "dimension2", "keyword", "dimension3")));

        Map<String, String> result =
                formatService.formatLog(rawLog("/matomo.php?dimension1=42&dimension2=&dimension3=food"), rules);

        assertThat(result)
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "postId", "42",
                        "category", "none",
                        "eventType", "View",
                        "keyword", "food"));
    }

    @Test
    @DisplayName("활성 규칙이 없으면 빈 Map을 반환한다")
    void returnsEmptyWhenNoRules() {
        assertThat(formatService.formatLog(rawLog("/matomo.php?dimension1=42"), List.of()))
                .isEmpty();
        assertThat(formatService.formatLog(rawLog("/matomo.php?dimension1=42"), null))
                .isEmpty();
    }

    private RawLog rawLog(String path) {
        return new RawLog(null, null, null, "GET", path, "200", null, null, null);
    }
}
