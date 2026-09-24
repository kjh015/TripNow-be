package com.traveler.useractivity.domain.process.core.code;

import com.traveler.useractivity.domain.history.enums.FailStage;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProcessFailCode {

    // FORMAT 단계 (포맷팅 실패)
    FORMAT_RULE_NOT_FOUND(FailStage.FORMAT, "활성화된 포맷 규칙이 없거나 조건에 매칭되지 않습니다."),

    // FILTER 단계 (조건 탈락)
    FILTER_CONDITION_MISMATCH(FailStage.FILTER, "로그가 필터 규칙 조건을 통과하지 못했습니다."),

    // DEDUP 단계 (중복 탈락)
    DEDUP_DUPLICATED_LOG(FailStage.DEDUP, "기존에 적재된 로그와 중복된 로그로 판별되었습니다.");

    private final FailStage stage; // 실패한 파이프라인 단계
    private final String description; // 실패의 상세/기본 사유
}
