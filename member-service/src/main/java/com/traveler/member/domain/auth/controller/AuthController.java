package com.traveler.member.domain.auth.controller;

import com.traveler.common.api.auth.annotation.LoginUser;
import com.traveler.common.api.auth.annotation.RequireAuth;
import com.traveler.common.core.auth.UserContext;
import com.traveler.common.core.code.ErrorCode;
import com.traveler.common.core.code.SuccessCode;
import com.traveler.common.core.response.ApiResponse;
import com.traveler.member.domain.auth.dto.request.AuthRequest;
import com.traveler.member.domain.auth.dto.response.AuthResponse;
import com.traveler.member.domain.auth.service.AuthService;
import com.traveler.member.global.exception.code.MemberServiceErrorCode;
import com.traveler.member.global.swagger.ApiErrorCodeExamples;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth API", description = "사용자 인증 및 인가 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/auth")
public class AuthController {
    private final AuthService authService;

    @Operation(summary = "일반 로그인", description = "ID와 비밀번호를 사용하여 로그인하고 토큰을 발급받습니다.")
    @ApiErrorCodeExamples(
            value = {MemberServiceErrorCode.MEMBER_NOT_FOUND, MemberServiceErrorCode.INVALID_PASSWORD},
            common = {ErrorCode.INVALID_TYPE_VALUE})
    @PostMapping("/login")
    public ApiResponse<AuthResponse.LoginResult> login(@Valid @RequestBody AuthRequest.LoginDTO dto) {
        return ApiResponse.onSuccess(SuccessCode.OK, authService.login(dto));
    }

    @Operation(summary = "로그아웃", description = "현재 사용자의 Access Token을 무효화하고 Refresh Token을 삭제합니다.")
    @ApiErrorCodeExamples(value = {MemberServiceErrorCode.INVALID_TOKEN_TYPE, MemberServiceErrorCode.EXPIRED_JWT})
    @RequireAuth
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Parameter(hidden = true) @LoginUser UserContext user) {
        authService.logout(user.id(), user.accessToken());
        return ApiResponse.onSuccess(SuccessCode.OK, null);
    }

    @Operation(summary = "토큰 재발급", description = "유효한 Refresh Token을 사용하여 새로운 Access Token과 Refresh Token을 재발급합니다.")
    @ApiErrorCodeExamples(
            value = {
                MemberServiceErrorCode.TOKEN_REISSUE_FAILED,
                MemberServiceErrorCode.EXPIRED_JWT,
                MemberServiceErrorCode.INVALID_TOKEN_TYPE
            },
            common = {ErrorCode.INVALID_TYPE_VALUE})
    @PostMapping("/tokens/refresh")
    public ApiResponse<AuthResponse.LoginResult> reissueRefreshToken(@Valid @RequestBody AuthRequest.ReissueDTO dto) {
        return ApiResponse.onSuccess(SuccessCode.OK, authService.reissue(dto.refreshToken()));
    }

    @Operation(summary = "OAuth 로그인/회원가입", description = "OAuth 인증 정보를 바탕으로 로그인하거나 신규 회원가입을 진행합니다.")
    @ApiErrorCodeExamples(common = {ErrorCode.INVALID_TYPE_VALUE})
    @PostMapping("/oauth/login")
    public ApiResponse<AuthResponse.LoginResult> oauthLogin(@Valid @RequestBody AuthRequest.OAuthLoginDTO dto) {
        return ApiResponse.onSuccess(SuccessCode.OK, authService.loginOrSignup(dto));
    }
}
