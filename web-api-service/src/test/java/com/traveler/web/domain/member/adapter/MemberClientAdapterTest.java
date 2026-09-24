package com.traveler.web.domain.member.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.BDDMockito.given;

import com.traveler.common.core.code.SuccessCode;
import com.traveler.common.core.response.ApiResponse;
import com.traveler.web.domain.member.client.MemberClient;
import com.traveler.web.domain.member.client.dto.response.MemberClientResponse;
import com.traveler.web.global.exception.WebApiServiceException;
import com.traveler.web.global.feign.decoder.ServiceErrorCode;
import feign.Request;
import feign.RetryableException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemberClientAdapterTest {

    private static final String DEFAULT_NICKNAME = MemberClientAdapter.DEFAULT_NICKNAME;

    @Mock
    private MemberClient memberClient;

    @InjectMocks
    private MemberClientAdapter memberClientAdapter;

    @Nested
    @DisplayName("getNicknameMap")
    class GetNicknameMap {

        @Test
        @DisplayName("성공하면 실제 닉네임을 채우고, 응답에 없는 회원은 기본 닉네임으로 채운다")
        void success_fillsMissingWithDefault() {
            given(memberClient.getMemberProfiles(anySet()))
                    .willReturn(ApiResponse.onSuccess(SuccessCode.OK, List.of(profile(1L, "여행자"))));

            Map<Long, String> result = memberClientAdapter.getNicknameMap(Set.of(1L, 2L));

            assertThat(result).containsExactlyInAnyOrderEntriesOf(Map.of(1L, "여행자", 2L, DEFAULT_NICKNAME));
        }

        @Test
        @DisplayName("member-service가 5xx를 반환하면 모든 회원을 기본 닉네임으로 채운다")
        void serverError_fallsBackToDefault() {
            given(memberClient.getMemberProfiles(anySet())).willThrow(serviceException(503));

            Map<Long, String> result = memberClientAdapter.getNicknameMap(Set.of(1L, 2L));

            assertThat(result).containsExactlyInAnyOrderEntriesOf(Map.of(1L, DEFAULT_NICKNAME, 2L, DEFAULT_NICKNAME));
        }

        @Test
        @DisplayName("타임아웃·연결 실패(RetryableException)면 기본 닉네임으로 채운다")
        void networkError_fallsBackToDefault() {
            given(memberClient.getMemberProfiles(anySet())).willThrow(retryableException());

            Map<Long, String> result = memberClientAdapter.getNicknameMap(Set.of(1L));

            assertThat(result).containsExactly(Map.entry(1L, DEFAULT_NICKNAME));
        }

        @Test
        @DisplayName("member-service가 4xx를 반환하면 예외를 그대로 전파한다")
        void clientError_propagates() {
            WebApiServiceException exception = serviceException(400);
            given(memberClient.getMemberProfiles(anySet())).willThrow(exception);

            assertThatThrownBy(() -> memberClientAdapter.getNicknameMap(Set.of(1L)))
                    .isSameAs(exception);
        }

        @Test
        @DisplayName("memberId가 null인 항목도 기본 닉네임으로 채운다")
        void nullMemberId_filledWithDefault() {
            given(memberClient.getMemberProfiles(anySet()))
                    .willReturn(ApiResponse.onSuccess(SuccessCode.OK, List.of(profile(1L, "여행자"))));
            Set<Long> memberIds = new HashSet<>();
            memberIds.add(1L);
            memberIds.add(null);

            Map<Long, String> result = memberClientAdapter.getNicknameMap(memberIds);

            Map<Long, String> expected = new HashMap<>();
            expected.put(1L, "여행자");
            expected.put(null, DEFAULT_NICKNAME);
            assertThat(result).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("getMemberNickname")
    class GetMemberNickname {

        @Test
        @DisplayName("성공하면 실제 닉네임을 반환한다")
        void success_returnsNickname() {
            given(memberClient.getMemberProfile(anyLong()))
                    .willReturn(ApiResponse.onSuccess(SuccessCode.OK, profile(1L, "여행자")));

            assertThat(memberClientAdapter.getMemberNickname(1L)).isEqualTo("여행자");
        }

        @Test
        @DisplayName("member-service가 5xx를 반환하면 기본 닉네임을 반환한다")
        void serverError_fallsBackToDefault() {
            given(memberClient.getMemberProfile(anyLong())).willThrow(serviceException(500));

            assertThat(memberClientAdapter.getMemberNickname(1L)).isEqualTo(DEFAULT_NICKNAME);
        }

        @Test
        @DisplayName("member-service가 4xx를 반환하면 예외를 그대로 전파한다")
        void clientError_propagates() {
            WebApiServiceException exception = serviceException(404);
            given(memberClient.getMemberProfile(anyLong())).willThrow(exception);

            assertThatThrownBy(() -> memberClientAdapter.getMemberNickname(1L)).isSameAs(exception);
        }
    }

    @Nested
    @DisplayName("getMyNickname")
    class GetMyNickname {

        @Test
        @DisplayName("성공하면 실제 닉네임을 반환한다")
        void success_returnsNickname() {
            given(memberClient.getMyProfile())
                    .willReturn(ApiResponse.onSuccess(
                            SuccessCode.OK,
                            new MemberClientResponse.MyProfileDTO(
                                    1L, "traveler", "t@example.com", "여행자", null, null, null, List.of())));

            assertThat(memberClientAdapter.getMyNickname()).isEqualTo("여행자");
        }

        @Test
        @DisplayName("타임아웃·연결 실패면 기본 닉네임을 반환한다")
        void networkError_fallsBackToDefault() {
            given(memberClient.getMyProfile()).willThrow(retryableException());

            assertThat(memberClientAdapter.getMyNickname()).isEqualTo(DEFAULT_NICKNAME);
        }

        @Test
        @DisplayName("member-service가 4xx를 반환하면 예외를 그대로 전파한다")
        void clientError_propagates() {
            WebApiServiceException exception = serviceException(401);
            given(memberClient.getMyProfile()).willThrow(exception);

            assertThatThrownBy(() -> memberClientAdapter.getMyNickname()).isSameAs(exception);
        }
    }

    private static MemberClientResponse.ProfileDTO profile(Long memberId, String nickname) {
        return new MemberClientResponse.ProfileDTO(memberId, nickname, null, null, null);
    }

    // FeignErrorDecoder가 하위 서비스 에러 응답을 변환한 형태와 같게 만든다
    private static WebApiServiceException serviceException(int status) {
        return new WebApiServiceException(ServiceErrorCode.of(status, null, null));
    }

    private static RetryableException retryableException() {
        Request request =
                Request.create(Request.HttpMethod.GET, "http://member-service/v1/members", Map.of(), null, null, null);
        return new RetryableException(-1, "Read timed out", Request.HttpMethod.GET, (Long) null, request);
    }
}
