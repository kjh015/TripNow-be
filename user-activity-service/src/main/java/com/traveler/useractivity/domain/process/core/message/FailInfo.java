package com.traveler.useractivity.domain.process.core.message;

import com.traveler.useractivity.domain.history.enums.FailStage;
import com.traveler.useractivity.domain.process.core.code.ProcessFailCode;

// stage는 Logstash가 fail_info.stage로 옮겨 색인한다. 실패 코드에서 파생하므로 호출부는 코드만 넘긴다.
public record FailInfo(ProcessFailCode code, FailStage stage, Long failRuleId, String failRuleName, String detail) {

    public FailInfo(ProcessFailCode code, Long failRuleId, String failRuleName, String detail) {
        this(code, code.getStage(), failRuleId, failRuleName, detail);
    }
}
