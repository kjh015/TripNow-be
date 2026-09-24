package com.traveler.web.domain.member.adapter;

import com.traveler.web.domain.member.client.MemberClient;
import com.traveler.web.domain.member.client.dto.request.MemberClientRequest;
import com.traveler.web.domain.member.client.dto.response.MemberClientResponse;
import com.traveler.web.global.exception.ExceptionUtils;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MemberClientAdapter {
    public static final String DEFAULT_NICKNAME = "알 수 없음";

    private final MemberClient memberClient;

    public MemberClientResponse.SignUpDTO signUp(MemberClientRequest.SignUpDTO dto) {
        return memberClient.signUp(dto).result();
    }

    public MemberClientResponse.WithdrawDTO withdraw() {
        return memberClient.withdraw().result();
    }

    public MemberClientResponse.UpdateDTO updateMember(MemberClientRequest.UpdateDTO dto) {
        return memberClient.updateMember(dto).result();
    }

    public MemberClientResponse.UpdatePasswordDTO updatePassword(MemberClientRequest.UpdatePasswordDTO dto) {
        return memberClient.updatePassword(dto).result();
    }

    public MemberClientResponse.MyProfileDTO getMyProfile() {
        return memberClient.getMyProfile().result();
    }

    public MemberClientResponse.AvailabilityDTO checkLoginIdAvailability(String loginId) {
        return memberClient.checkLoginIdAvailability(loginId).result();
    }

    public MemberClientResponse.AvailabilityDTO checkEmailAvailability(String email) {
        return memberClient.checkEmailAvailability(email).result();
    }

    public MemberClientResponse.AvailabilityDTO checkNicknameAvailability(String nickname) {
        return memberClient.checkNicknameAvailability(nickname).result();
    }

    /**
     * 요청한 모든 memberId에 닉네임을 채워 반환한다.
     * 닉네임은 부가 정보이므로 member-service 장애(5xx·타임아웃·연결 실패) 시 기본 닉네임으로 대체하고,
     * 4xx는 BFF의 잘못된 요청이므로 그대로 전파한다.
     */
    public Map<Long, String> getNicknameMap(Set<Long> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, String> fetched =
                fetchOrDefault(() -> fetchNicknameMap(memberIds), Collections.emptyMap(), "memberIds=" + memberIds);

        Map<Long, String> nicknameMap = new HashMap<>();
        memberIds.forEach(memberId -> nicknameMap.put(memberId, fetched.getOrDefault(memberId, DEFAULT_NICKNAME)));
        return nicknameMap;
    }

    public String getMemberNickname(Long memberId) {
        return fetchOrDefault(
                () -> {
                    MemberClientResponse.ProfileDTO profile =
                            memberClient.getMemberProfile(memberId).result();
                    return nicknameOrDefault(profile != null ? profile.nickname() : null);
                },
                DEFAULT_NICKNAME,
                "memberId=" + memberId);
    }

    public String getMyNickname() {
        return fetchOrDefault(
                () -> {
                    MemberClientResponse.MyProfileDTO profile =
                            memberClient.getMyProfile().result();
                    return nicknameOrDefault(profile != null ? profile.nickname() : null);
                },
                DEFAULT_NICKNAME,
                "me");
    }

    private Map<Long, String> fetchNicknameMap(Set<Long> memberIds) {
        List<MemberClientResponse.ProfileDTO> profiles =
                memberClient.getMemberProfiles(memberIds).result();
        if (profiles == null) {
            return Collections.emptyMap();
        }

        return profiles.stream()
                .filter(Objects::nonNull)
                .filter(p -> p.memberId() != null && p.nickname() != null)
                .collect(Collectors.toMap(
                        MemberClientResponse.ProfileDTO::memberId,
                        MemberClientResponse.ProfileDTO::nickname,
                        (existing, replacement) -> existing));
    }

    private String nicknameOrDefault(String nickname) {
        return nickname != null ? nickname : DEFAULT_NICKNAME;
    }

    private <T> T fetchOrDefault(Supplier<T> call, T fallback, String target) {
        try {
            return call.get();
        } catch (RuntimeException ex) {
            if (!ExceptionUtils.isRetryableException(ex)) {
                throw ex;
            }
            log.warn("member-service 장애로 기본 닉네임을 사용합니다. target: {}", target, ex);
            return fallback;
        }
    }
}
