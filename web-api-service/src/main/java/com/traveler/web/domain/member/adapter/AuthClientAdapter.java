package com.traveler.web.domain.member.adapter;

import com.traveler.web.domain.member.client.AuthClient;
import com.traveler.web.domain.member.client.dto.request.AuthClientRequest;
import com.traveler.web.domain.member.client.dto.response.AuthClientResponse;
import com.traveler.web.global.resilience.DownstreamCallExecutor;
import com.traveler.web.global.resilience.DownstreamServiceNames;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthClientAdapter {
    private final AuthClient authClient;
    private final DownstreamCallExecutor downstreamCallExecutor;

    public AuthClientResponse.LoginResult login(AuthClientRequest.LoginDTO dto) {
        return downstreamCallExecutor.execute(
                DownstreamServiceNames.MEMBER, () -> authClient.login(dto).result());
    }

    public void logout() {
        downstreamCallExecutor.execute(DownstreamServiceNames.MEMBER, () -> authClient.logout());
    }

    public AuthClientResponse.LoginResult reissue(AuthClientRequest.ReissueDTO dto) {
        return downstreamCallExecutor.execute(
                DownstreamServiceNames.MEMBER,
                () -> authClient.reissueRefreshToken(dto).result());
    }

    public AuthClientResponse.LoginResult oauthLogin(AuthClientRequest.OauthLoginDTO dto) {
        return downstreamCallExecutor.execute(
                DownstreamServiceNames.MEMBER, () -> authClient.oauthLogin(dto).result());
    }
}
