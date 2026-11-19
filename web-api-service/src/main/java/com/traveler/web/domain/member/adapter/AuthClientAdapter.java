package com.traveler.web.domain.member.adapter;

import com.traveler.web.domain.member.client.AuthClient;
import com.traveler.web.domain.member.client.dto.request.AuthClientRequest;
import com.traveler.web.domain.member.client.dto.response.AuthClientResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthClientAdapter {
    private final AuthClient authClient;

    public AuthClientResponse.LoginResult login(AuthClientRequest.LoginDTO dto) {
        return authClient.login(dto).result();
    }

    public void logout() {
        authClient.logout();
    }

    public AuthClientResponse.LoginResult reissue(AuthClientRequest.ReissueDTO dto) {
        return authClient.reissueRefreshToken(dto).result();
    }

    public AuthClientResponse.LoginResult oauthLogin(AuthClientRequest.OauthLoginDTO dto) {
        return authClient.oauthLogin(dto).result();
    }
}
