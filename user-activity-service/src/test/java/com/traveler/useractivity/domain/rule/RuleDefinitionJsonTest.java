package com.traveler.useractivity.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.traveler.useractivity.domain.rule.dedup.definition.DedupSpec;
import com.traveler.useractivity.domain.rule.filter.definition.FilterToken;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 규칙은 DB JSON 컬럼에 저장된다. 타입을 바꿔도 이미 저장된 JSON을 읽을 수 있어야 한다
class RuleDefinitionJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("저장된 필터 규칙 JSON을 토큰 타입별로 읽고 같은 형식으로 다시 쓴다")
    void filterTokensKeepJsonFormat() throws Exception {
        String json =
                """
                [
                  {"type":"left-paren","groupId":1},
                  {"type":"condition","groupId":1,"field":"user_id","operator":"==","value":"1","valueType":"INT"},
                  {"type":"operator","groupId":1,"value":"&&"},
                  {"type":"right-paren","groupId":1}
                ]
                """;

        List<FilterToken> tokens = objectMapper.readValue(json, new TypeReference<>() {});

        assertThat(tokens)
                .hasExactlyElementsOfTypes(
                        FilterToken.LeftParen.class,
                        FilterToken.Condition.class,
                        FilterToken.Operator.class,
                        FilterToken.RightParen.class);
        assertThat(objectMapper
                        .writerFor(new TypeReference<List<FilterToken>>() {})
                        .writeValueAsString(tokens))
                .satisfies(written -> assertSameJson(written, json));
    }

    @Test
    @DisplayName("저장된 중복 제거 규칙 JSON을 읽고 같은 형식으로 다시 쓴다")
    void dedupSpecKeepsJsonFormat() throws Exception {
        String json =
                """
                [{
                  "conditions":[{"field":"user_id","value":"1","matchType":"Exact"}],
                  "expirationTime":{"days":0,"hours":1,"minutes":0,"seconds":0}
                }]
                """;

        List<DedupSpec> specs = objectMapper.readValue(json, new TypeReference<>() {});

        assertThat(specs.getFirst().expirationTime().toTotalSeconds()).isEqualTo(3600);
        assertSameJson(objectMapper.writeValueAsString(specs), json);
    }

    private void assertSameJson(String actual, String expected) throws Exception {
        JsonNode actualNode = objectMapper.readTree(actual);
        assertThat(actualNode).isEqualTo(objectMapper.readTree(expected));
    }
}
