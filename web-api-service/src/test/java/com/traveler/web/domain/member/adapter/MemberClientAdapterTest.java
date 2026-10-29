package com.traveler.web.domain.member.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.traveler.common.core.code.SuccessCode;
import com.traveler.common.core.response.ApiResponse;
import com.traveler.web.domain.member.cache.MemberNicknameCache;
import com.traveler.web.domain.member.client.MemberClient;
import com.traveler.web.domain.member.client.dto.response.MemberClientResponse;
import com.traveler.web.global.exception.WebApiServiceException;
import com.traveler.web.global.feign.decoder.ServiceErrorCode;
import com.traveler.web.global.resilience.DownstreamCallExecutor;
import com.traveler.web.global.resilience.DownstreamServiceNames;
import feign.Request;
import feign.RetryableException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemberClientAdapterTest {

    private static final String DEFAULT_NICKNAME = MemberClientAdapter.DEFAULT_NICKNAME;

    @Mock
    private MemberClient memberClient;

    private MemberNicknameCache memberNicknameCache;
    private MemberClientAdapter memberClientAdapter;

    // Resilience4j 라이브러리 기본 설정(ofDefaults)으로 서킷을 만든다. 기본값은 호출이 100번 쌓여야 판정하므로
    // 이 테스트의 호출 수로는 서킷이 열리지 않고, 닉네임 대체 동작만 확인할 수 있다. 서킷 동작은 DownstreamCallExecutorTest가 확인한다
    @BeforeEach
    void setUp() {
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();
        registry.circuitBreaker(DownstreamServiceNames.MEMBER);
        memberNicknameCache = new MemberNicknameCache();
        memberClientAdapter =
                new MemberClientAdapter(memberClient, new DownstreamCallExecutor(registry), memberNicknameCache);
    }

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

    @Nested
    @DisplayName("닉네임 캐시")
    class NicknameCache {

        @Test
        @DisplayName("상세: 한 번 받은 닉네임은 캐시에서 꺼내 member-service를 다시 부르지 않는다")
        void memberNickname_cacheHit_skipsMemberCall() {
            given(memberClient.getMemberProfile(1L))
                    .willReturn(ApiResponse.onSuccess(SuccessCode.OK, profile(1L, "여행자")));

            memberClientAdapter.getMemberNickname(1L);
            String second = memberClientAdapter.getMemberNickname(1L);

            assertThat(second).isEqualTo("여행자");
            verify(memberClient, times(1)).getMemberProfile(1L);
        }

        @Test
        @DisplayName("목록: 모두 캐시에 있으면 member-service를 부르지 않는다")
        void nicknameMap_allHit_skipsMemberCall() {
            memberNicknameCache.put(1L, "여행자");
            memberNicknameCache.put(2L, "산책가");

            Map<Long, String> result = memberClientAdapter.getNicknameMap(Set.of(1L, 2L));

            assertThat(result).containsExactlyInAnyOrderEntriesOf(Map.of(1L, "여행자", 2L, "산책가"));
            verify(memberClient, never()).getMemberProfiles(anySet());
        }

        @Test
        @DisplayName("목록: 일부만 캐시에 있으면 없는 회원만 member-service에 묻고, 받은 닉네임을 캐시에 넣는다")
        void nicknameMap_partialHit_fetchesOnlyMissing() {
            memberNicknameCache.put(1L, "여행자");
            given(memberClient.getMemberProfiles(Set.of(2L, 3L)))
                    .willReturn(ApiResponse.onSuccess(SuccessCode.OK, List.of(profile(2L, "산책가"))));

            Map<Long, String> result = memberClientAdapter.getNicknameMap(Set.of(1L, 2L, 3L));

            assertThat(result).containsExactlyInAnyOrderEntriesOf(Map.of(1L, "여행자", 2L, "산책가", 3L, DEFAULT_NICKNAME));
            verify(memberClient).getMemberProfiles(Set.of(2L, 3L));
            assertThat(memberNicknameCache.get(2L)).isEqualTo("산책가");
            // 응답에 없어 기본 닉네임으로 채운 회원은 캐시에 넣지 않는다
            assertThat(memberNicknameCache.get(3L)).isNull();
        }

        @Test
        @DisplayName("상세: 장애로 받은 기본 닉네임은 캐시에 넣지 않아, 복구 뒤 바로 실제 닉네임을 받는다")
        void memberNickname_fallbackNotCached() {
            given(memberClient.getMemberProfile(1L))
                    .willThrow(serviceException(503))
                    .willReturn(ApiResponse.onSuccess(SuccessCode.OK, profile(1L, "여행자")));

            assertThat(memberClientAdapter.getMemberNickname(1L)).isEqualTo(DEFAULT_NICKNAME);
            assertThat(memberClientAdapter.getMemberNickname(1L)).isEqualTo("여행자");
            verify(memberClient, times(2)).getMemberProfile(1L);
        }

        @Test
        @DisplayName("목록: 장애로 받은 기본 닉네임은 캐시에 넣지 않는다")
        void nicknameMap_fallbackNotCached() {
            given(memberClient.getMemberProfiles(anySet())).willThrow(retryableException());

            memberClientAdapter.getNicknameMap(Set.of(1L, 2L));

            assertThat(memberNicknameCache.getAll(Set.of(1L, 2L))).isEmpty();
        }

        @Test
        @DisplayName("회원 정보 수정이 성공하면 그 회원의 캐시 항목을 지운다")
        void updateMember_evicts() {
            memberNicknameCache.put(1L, "여행자");
            given(memberClient.updateMember(any()))
                    .willReturn(ApiResponse.onSuccess(
                            SuccessCode.OK, new MemberClientResponse.UpdateDTO(1L, Instant.now())));

            memberClientAdapter.updateMember(null);

            assertThat(memberNicknameCache.get(1L)).isNull();
        }

        @Test
        @DisplayName("회원 정보 수정이 실패하면 캐시 항목을 그대로 둔다")
        void updateMember_failure_keepsCache() {
            memberNicknameCache.put(1L, "여행자");
            WebApiServiceException exception = serviceException(400);
            given(memberClient.updateMember(any())).willThrow(exception);

            assertThatThrownBy(() -> memberClientAdapter.updateMember(null)).isSameAs(exception);
            assertThat(memberNicknameCache.get(1L)).isEqualTo("여행자");
        }

        @Test
        @DisplayName("탈퇴가 성공하면 그 회원의 캐시 항목을 지운다")
        void withdraw_evicts() {
            memberNicknameCache.put(1L, "여행자");
            given(memberClient.withdraw())
                    .willReturn(ApiResponse.onSuccess(
                            SuccessCode.OK, new MemberClientResponse.WithdrawDTO(1L, Instant.now())));

            memberClientAdapter.withdraw();

            assertThat(memberNicknameCache.get(1L)).isNull();
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
