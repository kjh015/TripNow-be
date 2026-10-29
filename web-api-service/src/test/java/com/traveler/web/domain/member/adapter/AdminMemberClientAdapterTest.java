package com.traveler.web.domain.member.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.traveler.common.core.code.SuccessCode;
import com.traveler.common.core.response.ApiResponse;
import com.traveler.web.domain.member.cache.MemberNicknameCache;
import com.traveler.web.domain.member.client.AdminMemberClient;
import com.traveler.web.domain.member.client.dto.response.AdminMemberClientResponse;
import com.traveler.web.global.exception.WebApiServiceException;
import com.traveler.web.global.feign.decoder.ServiceErrorCode;
import com.traveler.web.global.resilience.DownstreamCallExecutor;
import com.traveler.web.global.resilience.DownstreamServiceNames;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminMemberClientAdapterTest {

    @Mock
    private AdminMemberClient adminMemberClient;

    private MemberNicknameCache memberNicknameCache;
    private AdminMemberClientAdapter adminMemberClientAdapter;

    @BeforeEach
    void setUp() {
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();
        registry.circuitBreaker(DownstreamServiceNames.MEMBER);
        memberNicknameCache = new MemberNicknameCache();
        adminMemberClientAdapter = new AdminMemberClientAdapter(
                adminMemberClient, new DownstreamCallExecutor(registry), memberNicknameCache);
        memberNicknameCache.put(1L, "여행자");
    }

    @Test
    @DisplayName("관리자 회원 삭제가 성공하면 그 회원의 닉네임 캐시 항목을 지운다")
    void deleteMember_evictsNickname() {
        given(adminMemberClient.deleteMember(1L))
                .willReturn(ApiResponse.onSuccess(
                        SuccessCode.OK, new AdminMemberClientResponse.DeleteDTO(1L, Instant.now())));

        adminMemberClientAdapter.deleteMember(1L);

        assertThat(memberNicknameCache.get(1L)).isNull();
    }

    @Test
    @DisplayName("관리자 회원 삭제가 실패하면 닉네임 캐시 항목을 그대로 둔다")
    void deleteMember_failure_keepsNickname() {
        WebApiServiceException exception = new WebApiServiceException(ServiceErrorCode.of(404, null, null));
        given(adminMemberClient.deleteMember(1L)).willThrow(exception);

        assertThatThrownBy(() -> adminMemberClientAdapter.deleteMember(1L)).isSameAs(exception);
        assertThat(memberNicknameCache.get(1L)).isEqualTo("여행자");
    }
}
