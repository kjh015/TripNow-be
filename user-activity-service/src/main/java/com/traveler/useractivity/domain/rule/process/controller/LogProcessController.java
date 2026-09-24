package com.traveler.useractivity.domain.rule.process.controller;

import com.traveler.common.api.auth.annotation.RequireRole;
import com.traveler.common.core.code.ErrorCode;
import com.traveler.common.core.code.SuccessCode;
import com.traveler.common.core.response.ApiResponse;
import com.traveler.common.core.response.PageResponse;
import com.traveler.useractivity.domain.rule.process.dto.request.LogProcessRequest;
import com.traveler.useractivity.domain.rule.process.dto.response.LogProcessResponse;
import com.traveler.useractivity.domain.rule.process.service.command.LogProcessCommandService;
import com.traveler.useractivity.domain.rule.process.service.query.LogProcessQueryService;
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

@Tag(name = "LogProcess", description = "LogProcess API")
@RestController
@RequiredArgsConstructor
@RequireRole("ROLE_ADMIN")
@RequestMapping("/v1/admin/log-processes")
public class LogProcessController {
    private final LogProcessCommandService logProcessCommandService;
    private final LogProcessQueryService logProcessQueryService;

    @Operation(summary = "로그 프로세스 생성", description = "새로운 로그 프로세스를 생성합니다.")
    @ApiErrorCodeExamples(common = {ErrorCode.INVALID_TYPE_VALUE})
    @PostMapping
    public ApiResponse<LogProcessResponse.CreateDTO> createLogProcess(
            @Valid @RequestBody LogProcessRequest.CreateDTO dto) {
        return ApiResponse.onSuccess(SuccessCode.OK, logProcessCommandService.createLogProcess(dto));
    }

    @Operation(summary = "로그 프로세스 수정", description = "기존 로그 프로세스의 이름/설명을 수정합니다.")
    @ApiErrorCodeExamples(
            value = {UserActivityServiceErrorCode.LOG_PROCESS_NOT_FOUND},
            common = {ErrorCode.INVALID_TYPE_VALUE})
    @PatchMapping("/{logProcessId}")
    public ApiResponse<LogProcessResponse.UpdateDTO> updateLogProcess(
            @Parameter(description = "로그 프로세스 ID") @PathVariable Long logProcessId,
            @Valid @RequestBody LogProcessRequest.UpdateDTO dto) {
        return ApiResponse.onSuccess(SuccessCode.OK, logProcessCommandService.updateLogProcess(logProcessId, dto));
    }

    @Operation(summary = "로그 프로세스 삭제", description = "로그 프로세스를 삭제(Soft Delete) 처리합니다.")
    @ApiErrorCodeExamples(UserActivityServiceErrorCode.LOG_PROCESS_NOT_FOUND)
    @DeleteMapping("/{logProcessId}")
    public ApiResponse<LogProcessResponse.DeleteDTO> deleteLogProcess(
            @Parameter(description = "로그 프로세스 ID") @PathVariable Long logProcessId) {
        return ApiResponse.onSuccess(SuccessCode.OK, logProcessCommandService.deleteLogProcess(logProcessId));
    }

    @Operation(summary = "로그 프로세스 목록 조회", description = "등록된 로그 프로세스 목록을 페이지 단위로 조회합니다.")
    @GetMapping
    public ApiResponse<PageResponse<LogProcessResponse.ListDTO>> getLogProcesses(
            @ParameterObject @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
                    Pageable pageable) {
        return ApiResponse.onSuccess(SuccessCode.OK, logProcessQueryService.getLogProcesses(pageable));
    }
}
