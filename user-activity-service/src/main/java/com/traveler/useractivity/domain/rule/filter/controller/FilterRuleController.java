package com.traveler.useractivity.domain.rule.filter.controller;

import com.traveler.common.api.auth.annotation.RequireRole;
import com.traveler.common.core.code.ErrorCode;
import com.traveler.common.core.code.SuccessCode;
import com.traveler.common.core.response.ApiResponse;
import com.traveler.common.core.response.PageResponse;
import com.traveler.useractivity.domain.rule.filter.dto.request.FilterRuleRequest;
import com.traveler.useractivity.domain.rule.filter.dto.response.FilterRuleResponse;
import com.traveler.useractivity.domain.rule.filter.service.command.FilterRuleCommandService;
import com.traveler.useractivity.domain.rule.filter.service.query.FilterRuleQueryService;
import com.traveler.useractivity.global.exception.code.UserActivityServiceErrorCode;
import com.traveler.useractivity.global.swagger.ApiErrorCodeExamples;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Filter", description = "Filter API")
@RestController
@RequiredArgsConstructor
@RequireRole("ROLE_ADMIN")
@RequestMapping("/v1/admin")
public class FilterRuleController {
    private final FilterRuleCommandService filterRuleCommandService;
    private final FilterRuleQueryService filterRuleQueryService;

    @Operation(summary = "필터 규칙 생성", description = "특정 로그 프로세스에 새로운 필터 규칙을 생성합니다.")
    @ApiErrorCodeExamples(
            value = {UserActivityServiceErrorCode.LOG_PROCESS_NOT_FOUND},
            common = {ErrorCode.INVALID_TYPE_VALUE})
    @PostMapping("/log-processes/{logProcessId}/filter-rules")
    public ApiResponse<FilterRuleResponse.CreateDTO> createFilterRule(
            @Parameter(description = "로그 프로세스 ID") @PathVariable Long logProcessId,
            @Valid @RequestBody FilterRuleRequest.CreateDTO dto) {
        return ApiResponse.onSuccess(SuccessCode.OK, filterRuleCommandService.createFilterRule(logProcessId, dto));
    }

    @Operation(summary = "필터 규칙 수정", description = "기존 필터 규칙의 내용을 수정합니다.")
    @ApiErrorCodeExamples(
            value = {UserActivityServiceErrorCode.FILTER_RULE_NOT_FOUND},
            common = {ErrorCode.INVALID_TYPE_VALUE})
    @PatchMapping("/filter-rules/{filterRuleId}")
    public ApiResponse<FilterRuleResponse.UpdateDTO> updateFilterRule(
            @Parameter(description = "필터 규칙 ID") @PathVariable Long filterRuleId,
            @Valid @RequestBody FilterRuleRequest.UpdateDTO dto) {
        return ApiResponse.onSuccess(SuccessCode.OK, filterRuleCommandService.updateFilterRule(filterRuleId, dto));
    }

    @Operation(summary = "필터 규칙 삭제", description = "필터 규칙을 삭제(Soft Delete) 처리합니다.")
    @ApiErrorCodeExamples(UserActivityServiceErrorCode.FILTER_RULE_NOT_FOUND)
    @DeleteMapping("/filter-rules/{filterRuleId}")
    public ApiResponse<FilterRuleResponse.DeleteDTO> deleteFilterRule(
            @Parameter(description = "필터 규칙 ID") @PathVariable Long filterRuleId) {
        return ApiResponse.onSuccess(SuccessCode.OK, filterRuleCommandService.deleteFilterRule(filterRuleId));
    }

    @Operation(summary = "필터 규칙 목록 조회", description = "특정 로그 프로세스에 속한 필터 규칙 목록을 조회합니다.")
    @GetMapping("/log-processes/{logProcessId}/filter-rules")
    public ApiResponse<PageResponse<FilterRuleResponse.ListDTO>> getFilterRules(
            @Parameter(description = "로그 프로세스 ID") @PathVariable Long logProcessId,
            @ParameterObject @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
                    Pageable pageable) {
        return ApiResponse.onSuccess(SuccessCode.OK, filterRuleQueryService.getFilterRules(logProcessId, pageable));
    }

    @Operation(summary = "필터 규칙 상세 조회", description = "특정 필터 규칙의 상세 정보를 조회합니다.")
    @ApiErrorCodeExamples(UserActivityServiceErrorCode.FILTER_RULE_NOT_FOUND)
    @GetMapping("/filter-rules/{filterRuleId}")
    public ApiResponse<FilterRuleResponse.DetailDTO> getFilterRule(
            @Parameter(description = "필터 규칙 ID") @PathVariable Long filterRuleId) {
        return ApiResponse.onSuccess(SuccessCode.OK, filterRuleQueryService.getFilterRule(filterRuleId));
    }
}
