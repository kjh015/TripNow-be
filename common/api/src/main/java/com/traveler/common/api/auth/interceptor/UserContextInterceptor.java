package com.traveler.common.api.auth.interceptor;

import com.traveler.common.api.auth.context.UserContextHolder;
import com.traveler.common.core.auth.AuthConstants;
import com.traveler.common.core.auth.UserContext;
import com.traveler.common.core.code.ErrorCode;
import com.traveler.common.core.exception.GeneralException;
import com.traveler.common.support.logging.MdcKeys;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
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
                // 이 요청 스레드의 모든 로그에 사용자 ID가 붙는다 (AuditLogger의 actor도 이 값)
                MDC.put(MdcKeys.USER_ID, String.valueOf(context.id()));
            } catch (NumberFormatException e) {
                warnMalformedAuthHeaders(request, userId, roles, accessToken);
                throw new GeneralException(ErrorCode.BAD_REQUEST);
            }
        } else if (hasAnyAuthHeader) {
            warnMalformedAuthHeaders(request, userId, roles, accessToken);
            throw new GeneralException(ErrorCode.BAD_REQUEST);
        }
        return true;
    }

    /**
     * 인증 헤더가 일부만 있거나 형식이 틀린 요청. 게이트웨이를 우회한 직접 호출이나 게이트웨이 버그의 신호다.
     * 헤더 값은 남기지 않고 전달된 헤더 이름만 남긴다.
     */
    private void warnMalformedAuthHeaders(HttpServletRequest request, String userId, String roles, String accessToken) {
        List<String> present = new ArrayList<>();
        if (userId != null) present.add(AuthConstants.X_USER_ID);
        if (roles != null) present.add(AuthConstants.X_USER_ROLES);
        if (accessToken != null) present.add(AuthConstants.X_ACCESS_TOKEN);
        log.warn("[Auth] Malformed internal auth headers. headers={} uri={}", present, request.getRequestURI());
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
        MDC.remove(MdcKeys.USER_ID);
    }
}
