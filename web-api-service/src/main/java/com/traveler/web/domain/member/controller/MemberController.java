package com.traveler.web.domain.member.controller;

import com.traveler.common.api.auth.annotation.RequireAuth;
import com.traveler.common.core.code.ErrorCode;
import com.traveler.common.core.code.SuccessCode;
import com.traveler.common.core.response.ApiResponse;
import com.traveler.web.domain.member.dto.request.MemberRequest;
import com.traveler.web.domain.member.dto.response.MemberResponse;
import com.traveler.web.domain.member.facade.MemberFacade;
import com.traveler.web.global.swagger.ApiErrorCodeExamples;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Member API (Web)", description = "웹 클라이언트 전용 회원 관리 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/members")
public class MemberController {
    private final MemberFacade memberFacade;

    @Operation(summary = "회원 가입", description = "입력된 정보를 검증한 후 Member 서버로 회원가입을 요청합니다.")
    @ApiErrorCodeExamples(common = {ErrorCode.INVALID_TYPE_VALUE})
    @PostMapping()
    public ApiResponse<MemberResponse.SignUpDTO> signUp(@Valid @RequestBody MemberRequest.SignUpDTO dto) {
        return ApiResponse.onSuccess(SuccessCode.OK, memberFacade.signUp(dto));
    }

    @Operation(summary = "회원 탈퇴", description = "현재 로그인된 회원의 탈퇴 처리를 Member 서버에 요청하고 웹 브라우저의 인증 쿠키를 만료시킵니다.")
    @RequireAuth
    @DeleteMapping("/me")
    public ApiResponse<MemberResponse.WithdrawDTO> withdraw(HttpServletResponse response) {
        // 인증 정보는 API Gateway나 Interceptor에서 Header로 주입됨을 가정
        return ApiResponse.onSuccess(SuccessCode.OK, memberFacade.withdraw(response));
    }

    @Operation(summary = "내 정보 수정", description = "현재 로그인된 회원의 프로필 정보(닉네임 등)를 수정합니다.")
    @ApiErrorCodeExamples(common = {ErrorCode.INVALID_TYPE_VALUE})
    @RequireAuth
    @PatchMapping("/me")
    public ApiResponse<MemberResponse.UpdateDTO> updateMember(@Valid @RequestBody MemberRequest.UpdateDTO dto) {
        return ApiResponse.onSuccess(SuccessCode.OK, memberFacade.updateMember(dto));
    }

    @Operation(summary = "비밀번호 변경", description = "현재 비밀번호를 확인한 후 새로운 비밀번호로 변경합니다.")
    @ApiErrorCodeExamples(common = {ErrorCode.INVALID_TYPE_VALUE})
    @RequireAuth
    @PatchMapping("/me/password")
    public ApiResponse<MemberResponse.UpdatePasswordDTO> updatePassword(
            @Valid @RequestBody MemberRequest.UpdatePasswordDTO dto) {
        return ApiResponse.onSuccess(SuccessCode.OK, memberFacade.updatePassword(dto));
    }

    @Operation(summary = "내 프로필 조회", description = "현재 로그인된 회원의 상세 프로필을 조회합니다.")
    @RequireAuth
    @GetMapping("/me")
    public ApiResponse<MemberResponse.MyProfileDTO> getMyProfile() {
        return ApiResponse.onSuccess(SuccessCode.OK, memberFacade.getMyProfile());
    }

    @Operation(summary = "로그인 ID 중복 확인", description = "로그인 ID의 사용 가능 여부를 확인합니다.")
    @ApiErrorCodeExamples(common = {ErrorCode.INVALID_TYPE_VALUE})
    @GetMapping("/availability/login-id")
    public ApiResponse<MemberResponse.AvailabilityDTO> checkLoginIdAvailability(
            @Parameter(description = "검사할 로그인 ID")
                    @NotBlank(message = "로그인 ID를 입력해주세요.")
                    @Pattern(regexp = "^[a-zA-Z0-9]{4,20}$", message = "로그인 ID는 영문 및 숫자 조합 4~20자리여야 합니다.")
                    @RequestParam
                    String loginId) {
        return ApiResponse.onSuccess(SuccessCode.OK, memberFacade.checkLoginIdAvailability(loginId));
    }

    @Operation(summary = "이메일 중복 확인", description = "이메일의 사용 가능 여부를 확인합니다.")
    @ApiErrorCodeExamples(common = {ErrorCode.INVALID_TYPE_VALUE})
    @GetMapping("/availability/email")
    public ApiResponse<MemberResponse.AvailabilityDTO> checkEmailAvailability(
            @Parameter(description = "검사할 이메일")
                    @NotBlank(message = "이메일을 입력해주세요.")
                    @Size(max = 320, message = "이메일 길이는 320자를 초과할 수 없습니다.")
                    @Email(message = "이메일 형식이 올바르지 않습니다.")
                    @RequestParam
                    String email) {
        return ApiResponse.onSuccess(SuccessCode.OK, memberFacade.checkEmailAvailability(email));
    }

    @Operation(summary = "닉네임 중복 확인", description = "닉네임의 사용 가능 여부를 확인합니다.")
    @ApiErrorCodeExamples(common = {ErrorCode.INVALID_TYPE_VALUE})
    @GetMapping("/availability/nickname")
    public ApiResponse<MemberResponse.AvailabilityDTO> checkNicknameAvailability(
            @Parameter(description = "검사할 닉네임")
                    @NotBlank(message = "닉네임을 입력해주세요.")
                    @Size(min = 2, max = 30, message = "닉네임은 2자 이상 30자 이하로 입력해주세요.")
                    @RequestParam
                    String nickname) {
        return ApiResponse.onSuccess(SuccessCode.OK, memberFacade.checkNicknameAvailability(nickname));
    }
}
