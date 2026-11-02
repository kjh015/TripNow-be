package com.traveler.web.domain.member.facade;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

import com.traveler.web.domain.member.adapter.AuthClientAdapter;
import com.traveler.web.domain.member.mapper.AuthMapper;
import com.traveler.web.global.exception.WebApiServiceException;
import com.traveler.web.global.exception.code.WebApiServiceErrorCode;
import com.traveler.web.global.security.support.AuthHttpSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
class AuthFacadeTest {

    @Mock
    private AuthClientAdapter authClientAdapter;

    @Mock
    private AuthMapper authMapper;

    @Mock
    private AuthHttpSupport authHttpSupport;

    @InjectMocks
    private AuthFacade authFacade;

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    @DisplayName("리프레시 토큰 쿠키가 없으면 member-service를 부르지 않고 401 REFRESH_TOKEN_MISSING으로 끝낸다")
    void reissue_withoutRefreshToken_throwsUnauthorized(String refreshToken) {
        assertThatThrownBy(() -> authFacade.reissue(refreshToken, new MockHttpServletResponse()))
                .isInstanceOf(WebApiServiceException.class)
                .extracting("code")
                .isEqualTo(WebApiServiceErrorCode.REFRESH_TOKEN_MISSING);
        verifyNoInteractions(authClientAdapter);
    }
}
