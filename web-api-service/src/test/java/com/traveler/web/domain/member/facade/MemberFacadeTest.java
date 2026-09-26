package com.traveler.web.domain.member.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.traveler.web.domain.member.adapter.MemberClientAdapter;
import com.traveler.web.domain.member.client.dto.response.MemberClientResponse;
import com.traveler.web.domain.member.dto.response.MemberResponse;
import com.traveler.web.domain.member.mapper.MemberMapper;
import com.traveler.web.global.security.support.AuthCookieProvider;
import com.traveler.web.global.security.support.AuthHttpSupport;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;

class MemberFacadeTest {

    private final MemberClientAdapter memberClientAdapter = mock(MemberClientAdapter.class);
    private final MemberMapper memberMapper = mock(MemberMapper.class);
    private final AuthHttpSupport authHttpSupport = new AuthHttpSupport(new AuthCookieProvider(1_209_600_000L, true));

    private final MemberFacade memberFacade = new MemberFacade(memberClientAdapter, memberMapper, authHttpSupport);

    @Test
    @DisplayName("탈퇴에 성공하면 리프레시 토큰 쿠키를 즉시 만료시킨다")
    void withdraw_success_expiresRefreshTokenCookie() {
        Instant deletedAt = Instant.now();
        given(memberClientAdapter.withdraw()).willReturn(new MemberClientResponse.WithdrawDTO(1L, deletedAt));
        given(memberMapper.toResponseWithdrawDTO(any())).willReturn(new MemberResponse.WithdrawDTO(1L, deletedAt));
        MockHttpServletResponse response = new MockHttpServletResponse();

        MemberResponse.WithdrawDTO result = memberFacade.withdraw(response);

        assertThat(result.memberId()).isEqualTo(1L);
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
                .startsWith("refreshToken=;")
                .contains("Max-Age=0");
    }

    @Test
    @DisplayName("탈퇴에 실패하면 인증 쿠키를 건드리지 않는다")
    void withdraw_failure_keepsCookie() {
        given(memberClientAdapter.withdraw()).willThrow(new IllegalStateException("member-service 호출 실패"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> memberFacade.withdraw(response)).isInstanceOf(IllegalStateException.class);
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNull();
    }
}
