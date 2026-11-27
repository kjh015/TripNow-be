package com.traveler.web.domain.member.client;

import com.traveler.common.core.response.ApiResponse;
import com.traveler.web.domain.member.client.dto.request.AuthClientRequest;
import com.traveler.web.domain.member.client.dto.response.AuthClientResponse;
import com.traveler.web.global.feign.config.FeignClientConfig;
import com.traveler.web.global.resilience.DownstreamServiceNames;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = DownstreamServiceNames.MEMBER,
        contextId = "AuthClient",
        path = "/v1/auth",
        configuration = FeignClientConfig.class)
public interface AuthClient {
    @PostMapping("/login")
    ApiResponse<AuthClientResponse.LoginResult> login(@RequestBody AuthClientRequest.LoginDTO dto);

    @PostMapping("/logout")
    ApiResponse<Void> logout();

    @PostMapping("/tokens/refresh")
    ApiResponse<AuthClientResponse.LoginResult> reissueRefreshToken(@RequestBody AuthClientRequest.ReissueDTO dto);

    @PostMapping("/oauth/login")
    ApiResponse<AuthClientResponse.LoginResult> oauthLogin(@RequestBody AuthClientRequest.OauthLoginDTO dto);
}
