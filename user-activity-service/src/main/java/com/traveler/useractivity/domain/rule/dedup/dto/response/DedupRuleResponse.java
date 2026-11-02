package com.traveler.useractivity.domain.rule.dedup.dto.response;

import com.traveler.useractivity.domain.rule.dedup.definition.DedupSpec;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

public final class DedupRuleResponse {
    private DedupRuleResponse() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public record CreateDTO(
            @Schema(description = "생성된 중복제거 규칙 ID", example = "1") Long dedupRuleId,
            @Schema(description = "생성 일시") Instant createdAt) {}

    public record UpdateDTO(
            @Schema(description = "수정된 중복제거 규칙 ID", example = "1") Long dedupRuleId,
            @Schema(description = "수정 일시") Instant updatedAt) {}

    public record DeleteDTO(
            @Schema(description = "삭제된 중복제거 규칙 ID", example = "1") Long dedupRuleId,
            @Schema(description = "삭제 일시") Instant deletedAt) {}

    public record ListDTO(
            @Schema(description = "중복제거 규칙 ID", example = "1") Long dedupRuleId,
            @Schema(description = "중복제거 규칙 이름", example = "IP 기준 중복제거") String name,
            @Schema(description = "생성 일시") Instant createdAt,
            @Schema(description = "수정 일시") Instant updatedAt,
            @Schema(description = "활성화 여부", example = "true") boolean isActive) {}

    public record DetailDTO(
            @Schema(description = "중복제거 규칙 ID", example = "1") Long dedupRuleId,
            @Schema(description = "중복제거 규칙 이름", example = "IP 기준 중복제거") String name,
            @Schema(description = "중복제거 규칙 목록") List<DedupSpec> rules,
            @Schema(description = "활성화 여부", example = "true") boolean isActive) {}
}
