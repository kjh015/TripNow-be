package com.traveler.common.api.auth.interceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.traveler.common.api.auth.context.UserContextHolder;
import com.traveler.common.core.auth.AuthConstants;
import com.traveler.common.core.auth.UserContext;
import com.traveler.common.core.code.ErrorCode;
import com.traveler.common.core.exception.GeneralException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class UserContextInterceptorTest {

    private final UserContextInterceptor interceptor = new UserContextInterceptor();
    private final MockHttpServletRequest request = new MockHttpServletRequest();

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    @DisplayName("세 헤더가 모두 있으면 인증 컨텍스트를 만든다")
    void allHeaders() {
        request.addHeader(AuthConstants.X_USER_ID, "1");
        request.addHeader(AuthConstants.X_USER_ROLES, "ROLE_USER, ROLE_ADMIN");
        request.addHeader(AuthConstants.X_ACCESS_TOKEN, "token");

        preHandle();

        UserContext context = UserContextHolder.getContext();
        assertThat(context.id()).isEqualTo(1L);
        assertThat(context.roles()).containsExactly("ROLE_USER", "ROLE_ADMIN");
        assertThat(context.accessToken()).isEqualTo("token");
    }

    @Test
    @DisplayName("X-User-Roles만 없으면 빈 권한의 인증 요청으로 본다")
    void rolesMissing() {
        request.addHeader(AuthConstants.X_USER_ID, "1");
        request.addHeader(AuthConstants.X_ACCESS_TOKEN, "token");

        preHandle();

        UserContext context = UserContextHolder.getContext();
        assertThat(context.id()).isEqualTo(1L);
        assertThat(context.roles()).isEmpty();
    }

    @Test
    @DisplayName("인증 헤더가 전부 없으면 비로그인 요청으로 통과한다")
    void noHeaders() {
        preHandle();

        assertThat(UserContextHolder.getContext()).isNull();
    }

    @Test
    @DisplayName("X-User-Id만 있으면 400")
    void userIdOnly() {
        request.addHeader(AuthConstants.X_USER_ID, "1");

        assertBadRequest();
    }

    @Test
    @DisplayName("X-Access-Token만 있으면 400")
    void accessTokenOnly() {
        request.addHeader(AuthConstants.X_ACCESS_TOKEN, "token");

        assertBadRequest();
    }

    @Test
    @DisplayName("X-User-Id가 숫자가 아니면 400")
    void invalidUserId() {
        request.addHeader(AuthConstants.X_USER_ID, "abc");
        request.addHeader(AuthConstants.X_ACCESS_TOKEN, "token");

        assertBadRequest();
    }

    private void preHandle() {
        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isTrue();
    }

    private void assertBadRequest() {
        assertThatThrownBy(this::preHandle).isInstanceOfSatisfying(GeneralException.class, e -> assertThat(e.getCode())
                .isEqualTo(ErrorCode.BAD_REQUEST));
        assertThat(UserContextHolder.getContext()).isNull();
    }
}
