package com.traveler.useractivity.domain.rule.filter.dto.response;

import com.traveler.useractivity.domain.rule.filter.definition.FilterToken;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

public final class FilterRuleResponse {
    private FilterRuleResponse() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public record CreateDTO(
            @Schema(description = "생성된 필터 규칙 ID", example = "1") Long filterRuleId,
            @Schema(description = "생성 일시") Instant createdAt) {}

    public record UpdateDTO(
            @Schema(description = "수정된 필터 규칙 ID", example = "1") Long filterRuleId,
            @Schema(description = "수정 일시") Instant updatedAt) {}

    public record DeleteDTO(
            @Schema(description = "삭제된 필터 규칙 ID", example = "1") Long filterRuleId,
            @Schema(description = "삭제 일시") Instant deletedAt) {}

    public record ListDTO(
            @Schema(description = "필터 규칙 ID", example = "1") Long filterRuleId,
            @Schema(description = "필터 규칙 이름", example = "에러 로그만 필터링") String name,
            @Schema(description = "생성 일시") Instant createdAt,
            @Schema(description = "수정 일시") Instant updatedAt,
            @Schema(description = "활성화 여부", example = "true") boolean isActive) {}

    public record DetailDTO(
            @Schema(description = "필터 규칙 ID", example = "1") Long filterRuleId,
            @Schema(description = "필터 규칙 이름", example = "에러 로그만 필터링") String name,
            @Schema(description = "필터 규칙의 SpEL 표현식", example = "field == 'ERROR'") String expression,
            @Schema(description = "필터 조건 토큰 목록") List<FilterToken> conditions,
            @Schema(description = "활성화 여부", example = "true") boolean isActive) {}
}
