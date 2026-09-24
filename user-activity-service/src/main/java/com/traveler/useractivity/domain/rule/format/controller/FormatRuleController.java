package com.traveler.useractivity.domain.rule.format.controller;

import com.traveler.common.api.auth.annotation.RequireRole;
import com.traveler.common.core.code.ErrorCode;
import com.traveler.common.core.code.SuccessCode;
import com.traveler.common.core.response.ApiResponse;
import com.traveler.common.core.response.PageResponse;
import com.traveler.useractivity.domain.rule.format.dto.request.FormatRuleRequest;
import com.traveler.useractivity.domain.rule.format.dto.response.FormatRuleResponse;
import com.traveler.useractivity.domain.rule.format.service.command.FormatRuleCommandService;
import com.traveler.useractivity.domain.rule.format.service.query.FormatRuleQueryService;
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

@Tag(name = "Format Rule", description = "Format Rule API")
@RestController
@RequiredArgsConstructor
@RequireRole("ROLE_ADMIN")
@RequestMapping("/v1/admin")
public class FormatRuleController {
    private final FormatRuleCommandService formatRuleCommandService;
    private final FormatRuleQueryService formatRuleQueryService;

    @Operation(summary = "포맷 규칙 생성", description = "특정 로그 프로세스에 새로운 포맷 규칙을 생성합니다.")
    @ApiErrorCodeExamples(
            value = {UserActivityServiceErrorCode.LOG_PROCESS_NOT_FOUND},
            common = {ErrorCode.INVALID_TYPE_VALUE})
    @PostMapping("/log-processes/{logProcessId}/format-rules")
    public ApiResponse<FormatRuleResponse.CreateDTO> createFormatRule(
            @Parameter(description = "로그 프로세스 ID") @PathVariable Long logProcessId,
            @Valid @RequestBody FormatRuleRequest.CreateDTO dto) {
        return ApiResponse.onSuccess(SuccessCode.OK, formatRuleCommandService.createFormatRule(logProcessId, dto));
    }

    @Operation(summary = "포맷 규칙 수정", description = "기존 포맷 규칙의 내용을 수정합니다.")
    @ApiErrorCodeExamples(
            value = {UserActivityServiceErrorCode.FORMAT_RULE_NOT_FOUND},
            common = {ErrorCode.INVALID_TYPE_VALUE})
    @PatchMapping("/format-rules/{formatRuleId}")
    public ApiResponse<FormatRuleResponse.UpdateDTO> updateFormatRule(
            @Parameter(description = "포맷 규칙 ID") @PathVariable Long formatRuleId,
            @Valid @RequestBody FormatRuleRequest.UpdateDTO dto) {
        return ApiResponse.onSuccess(SuccessCode.OK, formatRuleCommandService.updateFormatRule(formatRuleId, dto));
    }

    @Operation(summary = "포맷 규칙 삭제", description = "포맷 규칙을 삭제(Soft Delete) 처리합니다.")
    @ApiErrorCodeExamples(UserActivityServiceErrorCode.FORMAT_RULE_NOT_FOUND)
    @DeleteMapping("/format-rules/{formatRuleId}")
    public ApiResponse<FormatRuleResponse.DeleteDTO> deleteFormatRule(
            @Parameter(description = "포맷 규칙 ID") @PathVariable Long formatRuleId) {
        return ApiResponse.onSuccess(SuccessCode.OK, formatRuleCommandService.deleteFormatRule(formatRuleId));
    }

    @Operation(summary = "포맷 규칙 목록 조회", description = "특정 로그 프로세스에 속한 포맷 규칙 목록을 조회합니다.")
    @GetMapping("/log-processes/{logProcessId}/format-rules")
    public ApiResponse<PageResponse<FormatRuleResponse.ListDTO>> getFormatRules(
            @Parameter(description = "로그 프로세스 ID") @PathVariable Long logProcessId,
            @ParameterObject @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
                    Pageable pageable) {
        return ApiResponse.onSuccess(SuccessCode.OK, formatRuleQueryService.getFormatRules(logProcessId, pageable));
    }

    @Operation(summary = "포맷 규칙 상세 조회", description = "특정 포맷 규칙의 상세 정보를 조회합니다.")
    @ApiErrorCodeExamples(UserActivityServiceErrorCode.FORMAT_RULE_NOT_FOUND)
    @GetMapping("/format-rules/{formatRuleId}")
    public ApiResponse<FormatRuleResponse.DetailDTO> getFormatRule(
            @Parameter(description = "포맷 규칙 ID") @PathVariable Long formatRuleId) {
        return ApiResponse.onSuccess(SuccessCode.OK, formatRuleQueryService.getFormatRule(formatRuleId));
    }

    @Operation(summary = "활성 포맷 규칙 필드 조회", description = "특정 로그 프로세스에서 현재 활성화된 포맷 규칙의 필드 목록을 조회합니다.")
    @ApiErrorCodeExamples(UserActivityServiceErrorCode.LOG_PROCESS_NOT_FOUND)
    @GetMapping("/log-processes/{logProcessId}/format-rules/fields")
    public ApiResponse<FormatRuleResponse.FieldDTO> getActiveFormatRuleFields(
            @Parameter(description = "로그 프로세스 ID") @PathVariable Long logProcessId) {
        return ApiResponse.onSuccess(SuccessCode.OK, formatRuleQueryService.getActiveFormatRuleFields(logProcessId));
    }
}
