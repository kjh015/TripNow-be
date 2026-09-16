package com.traveler.gateway.auth.support;

import com.traveler.common.core.auth.AuthConstants;
import com.traveler.common.core.auth.UserContext;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;

@Component
public class AuthContextManager {
    public static final String AUTH_USER_KEY = "auth_user_context";

    public ServerWebExchange prepareAuthorizedExchange(ServerWebExchange exchange, UserContext user) {
        return exchange.mutate()
                .request(r -> r.headers(headers -> {
                    // 클라이언트 헤더 제거는 InternalHeaderStripFilter가 모든 요청에서 수행한다.
                    // 인증 정보가 있는 경우에만 주입하며, set으로 남은 값이 있어도 덮어쓴다 (Late Binding)
                    if (user != null) {
                        headers.set(AuthConstants.X_USER_ID, String.valueOf(user.id()));
                        if (user.roles() != null && !user.roles().isEmpty()) {
                            headers.set(AuthConstants.X_USER_ROLES, String.join(",", user.roles()));
                        }
                        if (StringUtils.hasText(user.accessToken())) {
                            headers.set(AuthConstants.X_ACCESS_TOKEN, user.accessToken());
                        }
                    }
                }))
                .build();
    }

    public void storeAuthenticatedUser(ServerWebExchange exchange, UserContext user) {
        if (user != null) {
            exchange.getAttributes().put(AUTH_USER_KEY, user);
        }
    }

    public Optional<UserContext> getAuthenticatedUser(ServerWebExchange exchange) {
        return Optional.ofNullable(exchange.getAttribute(AUTH_USER_KEY));
    }
}
