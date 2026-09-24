package com.traveler.useractivity.domain.process.core.message;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.traveler.useractivity.domain.history.enums.FailStage;
import com.traveler.useractivity.domain.process.core.code.ProcessFailCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class FailInfoTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @ParameterizedTest
    @EnumSource(ProcessFailCode.class)
    @DisplayName("직렬화한 FailInfo JSON에 실패 코드의 stage가 실린다 (Logstash가 fail_info.stage로 옮김)")
    void serializedJsonContainsStage(ProcessFailCode code) throws Exception {
        FailInfo failInfo = new FailInfo(code, 1L, "rule", "detail");

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(failInfo));

        assertThat(json.get("code").asText()).isEqualTo(code.name());
        assertThat(json.get("stage").asText()).isEqualTo(code.getStage().name());
    }

    @Test
    @DisplayName("포맷 실패는 FORMAT 단계로 기록된다")
    void formatFailureStageIsFormat() {
        FailInfo failInfo = new FailInfo(ProcessFailCode.FORMAT_RULE_NOT_FOUND, null, null, "detail");

        assertThat(failInfo.stage()).isEqualTo(FailStage.FORMAT);
    }
}
