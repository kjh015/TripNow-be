package com.traveler.common.api.auth.interceptor;

import com.traveler.common.api.auth.annotation.RequireAuth;
import com.traveler.common.api.auth.annotation.RequireRole;
import com.traveler.common.api.auth.context.UserContextHolder;
import com.traveler.common.core.auth.UserContext;
import com.traveler.common.core.code.ErrorCode;
import com.traveler.common.core.exception.GeneralException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
public class AuthorizationInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) return true;

        RequireAuth requireAuth = getAnnotation(handlerMethod, RequireAuth.class);
        RequireRole requireRole = getAnnotation(handlerMethod, RequireRole.class);
        UserContext context = UserContextHolder.getContext();

        // 보안 어노테이션이 전혀 없는 public API는 즉시 통과
        if (requireAuth == null && requireRole == null) {
            return true;
        }

        // 어노테이션이 있는데 컨텍스트(유저 정보)가 없으면 401 차단
        if (context == null) {
            log.warn(
                    "[Auth] Unauthenticated. uri={} required={}",
                    request.getRequestURI(),
                    requireRole != null ? Arrays.toString(requireRole.value()) : "AUTH");
            throw new GeneralException(ErrorCode.UNAUTHORIZED);
        }

        // 권한(Role) 요구사항이 있는 경우 403 인가(Authorization) 검사
        if (requireRole != null) {
            List<String> requiredRoles = Arrays.asList(requireRole.value());

            // context가 절대 null이 아님이 위에서 보장되었으므로 안전하게 접근 가능
            boolean hasRole = context.roles().stream().anyMatch(requiredRoles::contains);

            if (!hasRole) {
                log.warn(
                        "[Auth] Access Denied. userId={} uri={} required={} actual={}",
                        context.id(),
                        request.getRequestURI(),
                        requiredRoles,
                        context.roles());
                throw new GeneralException(ErrorCode.FORBIDDEN);
            }
        }

        return true;
    }

    private <T extends java.lang.annotation.Annotation> T getAnnotation(
            HandlerMethod handlerMethod, Class<T> annotationType) {
        T annotation = handlerMethod.getMethodAnnotation(annotationType);
        return (annotation != null) ? annotation : handlerMethod.getBeanType().getAnnotation(annotationType);
    }
}
