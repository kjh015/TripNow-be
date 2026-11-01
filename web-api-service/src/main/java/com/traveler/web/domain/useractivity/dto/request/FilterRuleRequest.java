package com.traveler.web.domain.useractivity.dto.request;

import com.traveler.web.domain.useractivity.dto.FilterToken;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public final class FilterRuleRequest {
    private FilterRuleRequest() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    @Schema(name = "FilterRuleCreateRequest")
    public record CreateDTO(
            @NotBlank(message = "필터 규칙 이름은 필수입니다.") @Schema(description = "필터 규칙 이름", example = "에러 로그만 필터링")
                    String name,
            @NotEmpty(message = "최소 하나 이상의 조건 노드가 필요합니다.") @Valid @Schema(description = "필터 조건 토큰 목록")
                    List<FilterToken> conditions,
            @NotNull(message = "활성화 여부는 필수입니다.") @Schema(description = "활성화 여부", example = "true") Boolean isActive) {}

    @Schema(name = "FilterRuleUpdateRequest")
    public record UpdateDTO(
            @NotBlank(message = "필터 규칙 이름은 필수입니다.") @Schema(description = "필터 규칙 이름", example = "에러 로그만 필터링")
                    String name,
            @NotEmpty(message = "최소 하나 이상의 조건 노드가 필요합니다.") @Valid @Schema(description = "필터 조건 토큰 목록")
                    List<FilterToken> conditions,
            @NotNull(message = "활성화 여부는 필수입니다.") @Schema(description = "활성화 여부", example = "true") Boolean isActive) {}
}
