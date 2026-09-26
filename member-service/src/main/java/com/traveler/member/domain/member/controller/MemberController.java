package com.traveler.member.domain.member.controller;

import com.traveler.common.api.auth.annotation.LoginUser;
import com.traveler.common.api.auth.annotation.RequireAuth;
import com.traveler.common.core.auth.UserContext;
import com.traveler.common.core.code.ErrorCode;
import com.traveler.common.core.code.SuccessCode;
import com.traveler.common.core.response.ApiResponse;
import com.traveler.member.domain.member.dto.request.MemberRequest;
import com.traveler.member.domain.member.dto.response.MemberResponse;
import com.traveler.member.domain.member.service.command.MemberCommandService;
import com.traveler.member.domain.member.service.query.MemberQueryService;
import com.traveler.member.global.exception.code.MemberServiceErrorCode;
import com.traveler.member.global.swagger.ApiErrorCodeExamples;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Member API", description = "회원 관리 및 프로필 조회 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/members")
public class MemberController {
    private final MemberQueryService memberQueryService;
    private final MemberCommandService memberCommandService;

    @Operation(summary = "회원 가입", description = "신규 회원을 등록합니다.")
    @ApiErrorCodeExamples(
            value = {
                MemberServiceErrorCode.MEMBER_EXISTS_LOGINID,
                MemberServiceErrorCode.MEMBER_EXISTS_EMAIL,
                MemberServiceErrorCode.MEMBER_EXISTS_NICKNAME
            },
            common = {ErrorCode.INVALID_TYPE_VALUE})
    @PostMapping()
    public ApiResponse<MemberResponse.SignUpDTO> signUp(@Valid @RequestBody MemberRequest.SignUpDTO dto) {
        return ApiResponse.onSuccess(SuccessCode.OK, memberCommandService.signUp(dto));
    }

    @Operation(summary = "회원 탈퇴", description = "현재 로그인된 회원을 탈퇴 처리(논리적 삭제)합니다.")
    @ApiErrorCodeExamples({MemberServiceErrorCode.MEMBER_NOT_FOUND, MemberServiceErrorCode.MEMBER_ALREADY_DELETED})
    @RequireAuth
    @DeleteMapping("/me")
    public ApiResponse<MemberResponse.WithdrawDTO> withdraw(@Parameter(hidden = true) @LoginUser UserContext user) {
        return ApiResponse.onSuccess(SuccessCode.OK, memberCommandService.withdraw(user.id(), user.accessToken()));
    }

    @Operation(summary = "내 정보 수정", description = "현재 로그인된 회원의 프로필 정보(닉네임 등)를 수정합니다.")
    @ApiErrorCodeExamples(
            value = {MemberServiceErrorCode.MEMBER_NOT_FOUND, MemberServiceErrorCode.MEMBER_ALREADY_DELETED},
            common = {ErrorCode.INVALID_TYPE_VALUE})
    @RequireAuth
    @PatchMapping("/me")
    public ApiResponse<MemberResponse.UpdateDTO> updateMember(
            @Valid @RequestBody MemberRequest.UpdateDTO dto, @Parameter(hidden = true) @LoginUser UserContext user) {
        return ApiResponse.onSuccess(SuccessCode.OK, memberCommandService.updateMember(dto, user.id()));
    }

    @Operation(summary = "비밀번호 변경", description = "현재 비밀번호를 확인한 후 새로운 비밀번호로 변경합니다.")
    @ApiErrorCodeExamples(
            value = {
                MemberServiceErrorCode.MEMBER_NOT_FOUND,
                MemberServiceErrorCode.INVALID_PASSWORD,
                MemberServiceErrorCode.PASSWORD_SAME_AS_OLD
            },
            common = {ErrorCode.INVALID_TYPE_VALUE})
    @RequireAuth
    @PatchMapping("/me/password")
    public ApiResponse<MemberResponse.UpdatePasswordDTO> updatePassword(
            @Valid @RequestBody MemberRequest.UpdatePasswordDTO dto,
            @Parameter(hidden = true) @LoginUser UserContext user) {
        return ApiResponse.onSuccess(SuccessCode.OK, memberCommandService.updatePassword(dto, user.id()));
    }

    @Operation(summary = "다중 프로필 조회", description = "주어진 회원 ID 목록에 해당하는 프로필 정보들을 조회합니다.")
    @GetMapping
    public ApiResponse<List<MemberResponse.ProfileDTO>> getMemberProfiles(
            @Parameter(description = "조회할 회원 ID 목록") @NotEmpty(message = "회원 ID 목록은 비어있을 수 없습니다.") @RequestParam
                    Set<Long> memberIds) {
        return ApiResponse.onSuccess(SuccessCode.OK, memberQueryService.getMemberProfiles(memberIds));
    }

    @Operation(summary = "단일 프로필 조회", description = "특정 회원의 공개 프로필을 조회합니다.")
    @ApiErrorCodeExamples({MemberServiceErrorCode.MEMBER_NOT_FOUND})
    @GetMapping("/{memberId}")
    public ApiResponse<MemberResponse.ProfileDTO> getMemberProfile(
            @Parameter(description = "회원 ID") @PathVariable Long memberId) {
        return ApiResponse.onSuccess(SuccessCode.OK, memberQueryService.getMemberProfile(memberId));
    }

    @Operation(summary = "내 프로필 조회", description = "현재 로그인된 회원의 상세 프로필을 조회합니다.")
    @ApiErrorCodeExamples({MemberServiceErrorCode.MEMBER_NOT_FOUND})
    @RequireAuth
    @GetMapping("/me")
    public ApiResponse<MemberResponse.MyProfileDTO> getMyProfile(
            @Parameter(hidden = true) @LoginUser UserContext user) {
        return ApiResponse.onSuccess(SuccessCode.OK, memberQueryService.getMyProfile(user.id()));
    }

    @Operation(summary = "로그인 ID 중복 확인", description = "로그인 ID의 사용 가능 여부를 확인합니다.")
    @ApiErrorCodeExamples(common = {ErrorCode.INVALID_TYPE_VALUE})
    @GetMapping("/availability/login-id")
    public ApiResponse<MemberResponse.AvailabilityDTO> checkLoginIdAvailability(
            @NotBlank(message = "로그인 ID를 입력해주세요.") @RequestParam String loginId) {
        return ApiResponse.onSuccess(SuccessCode.OK, memberQueryService.checkLoginIdAvailability(loginId));
    }

    @Operation(summary = "이메일 중복 확인", description = "이메일의 사용 가능 여부를 확인합니다.")
    @ApiErrorCodeExamples(common = {ErrorCode.INVALID_TYPE_VALUE})
    @GetMapping("/availability/email")
    public ApiResponse<MemberResponse.AvailabilityDTO> checkEmailAvailability(
            @NotBlank(message = "이메일은 필수입니다.") @Email(message = "이메일 형식이 올바르지 않습니다.") @RequestParam String email) {
        return ApiResponse.onSuccess(SuccessCode.OK, memberQueryService.checkEmailAvailability(email));
    }

    @Operation(summary = "닉네임 중복 확인", description = "닉네임의 사용 가능 여부를 확인합니다.")
    @ApiErrorCodeExamples(common = {ErrorCode.INVALID_TYPE_VALUE})
    @GetMapping("/availability/nickname")
    public ApiResponse<MemberResponse.AvailabilityDTO> checkNicknameAvailability(
            @NotBlank(message = "닉네임을 입력해주세요.") @RequestParam String nickname) {
        return ApiResponse.onSuccess(SuccessCode.OK, memberQueryService.checkNicknameAvailability(nickname));
    }
}
