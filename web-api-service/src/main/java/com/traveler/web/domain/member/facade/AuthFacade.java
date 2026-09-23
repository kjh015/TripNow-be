package com.traveler.web.domain.member.facade;

import com.traveler.web.domain.member.adapter.AuthClientAdapter;
import com.traveler.web.domain.member.client.dto.request.AuthClientRequest;
import com.traveler.web.domain.member.client.dto.response.AuthClientResponse;
import com.traveler.web.domain.member.dto.request.AuthRequest;
import com.traveler.web.domain.member.dto.response.AuthResponse;
import com.traveler.web.domain.member.mapper.AuthMapper;
import com.traveler.web.global.security.support.AuthHttpSupport;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthFacade {
    private final AuthClientAdapter authClientAdapter;
    private final AuthMapper authMapper;
    private final AuthHttpSupport authHttpSupport;

    public AuthResponse.LoginDTO login(AuthRequest.LoginDTO dto, HttpServletResponse response) {
        AuthClientRequest.LoginDTO clientRequest = authMapper.toClientLoginRequest(dto);

        AuthClientResponse.LoginResult clientResponse = authClientAdapter.login(clientRequest);

        // BFF에서 브라우저 응답 헤더 및 쿠키 굽기
        authHttpSupport.setAuthResponse(response, clientResponse.tokens());

        return authMapper.toResponseLoginDTO(clientResponse.loginInfo());
    }

    public void logout(HttpServletResponse response) {
        try {
            authClientAdapter.logout();
        } finally {
            authHttpSupport.clearAuthResponse(response);
        }
    }

    public AuthResponse.LoginDTO reissue(String refreshToken, HttpServletResponse response) {
        AuthClientRequest.ReissueDTO clientRequest = authMapper.toClientReissueDTO(refreshToken);

        AuthClientResponse.LoginResult clientResponse = authClientAdapter.reissue(clientRequest);

        authHttpSupport.setAuthResponse(response, clientResponse.tokens());

        return authMapper.toResponseLoginDTO(clientResponse.loginInfo());
    }

    public AuthResponse.LoginDTO oauthLoginByCodeData(
            AuthClientRequest.OauthLoginDTO oauthLoginReq, HttpServletResponse response) {
        AuthClientResponse.LoginResult loginResult = authClientAdapter.oauthLogin(oauthLoginReq);
        authHttpSupport.setAuthResponse(response, loginResult.tokens());
        return authMapper.toResponseLoginDTO(loginResult.loginInfo());
    }
}
