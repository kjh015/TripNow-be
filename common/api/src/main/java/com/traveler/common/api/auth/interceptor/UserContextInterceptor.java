package com.traveler.common.api.auth.interceptor;

import com.traveler.common.api.auth.context.UserContextHolder;
import com.traveler.common.core.auth.AuthConstants;
import com.traveler.common.core.auth.UserContext;
import com.traveler.common.core.code.ErrorCode;
import com.traveler.common.core.exception.GeneralException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.List;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

public class UserContextInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String userId = request.getHeader(AuthConstants.X_USER_ID);
        String roles = request.getHeader(AuthConstants.X_USER_ROLES);
        String accessToken = request.getHeader(AuthConstants.X_ACCESS_TOKEN);

        boolean hasAnyAuthHeader = userId != null || roles != null || accessToken != null;
        // 권한이 없는 사용자는 X-User-Roles가 전달되지 않으므로 userId와 accessToken만으로 인증 요청을 판단한다
        boolean isAuthenticated = userId != null && StringUtils.hasText(accessToken);

        if (isAuthenticated) {
            try {
                UserContext context = UserContext.of(Long.valueOf(userId), parseRoles(roles), accessToken);
                UserContextHolder.setContext(context);
            } catch (NumberFormatException e) {
                throw new GeneralException(ErrorCode.BAD_REQUEST);
            }
        } else if (hasAnyAuthHeader) {
            throw new GeneralException(ErrorCode.BAD_REQUEST);
        }
        return true;
    }

    /** 권한 헤더가 없거나 비어 있으면 빈 권한으로 해석한다. */
    private List<String> parseRoles(String roles) {
        if (!StringUtils.hasText(roles)) {
            return List.of();
        }
        return Arrays.stream(roles.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContextHolder.clear();
    }
}
