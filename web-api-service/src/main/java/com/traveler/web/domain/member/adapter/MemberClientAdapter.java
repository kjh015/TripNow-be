package com.traveler.web.domain.member.adapter;

import com.traveler.web.domain.member.client.MemberClient;
import com.traveler.web.domain.member.client.dto.request.MemberClientRequest;
import com.traveler.web.domain.member.client.dto.response.MemberClientResponse;
import com.traveler.web.global.resilience.DownstreamCallExecutor;
import com.traveler.web.global.resilience.DownstreamServiceNames;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MemberClientAdapter {
    public static final String DEFAULT_NICKNAME = "알 수 없음";

    private final MemberClient memberClient;
    private final DownstreamCallExecutor downstreamCallExecutor;

    public MemberClientResponse.SignUpDTO signUp(MemberClientRequest.SignUpDTO dto) {
        return downstreamCallExecutor.execute(
                DownstreamServiceNames.MEMBER, () -> memberClient.signUp(dto).result());
    }

    public MemberClientResponse.WithdrawDTO withdraw() {
        return downstreamCallExecutor.execute(
                DownstreamServiceNames.MEMBER, () -> memberClient.withdraw().result());
    }

    public MemberClientResponse.UpdateDTO updateMember(MemberClientRequest.UpdateDTO dto) {
        return downstreamCallExecutor.execute(
                DownstreamServiceNames.MEMBER,
                () -> memberClient.updateMember(dto).result());
    }

    public MemberClientResponse.UpdatePasswordDTO updatePassword(MemberClientRequest.UpdatePasswordDTO dto) {
        return downstreamCallExecutor.execute(
                DownstreamServiceNames.MEMBER,
                () -> memberClient.updatePassword(dto).result());
    }

    public MemberClientResponse.MyProfileDTO getMyProfile() {
        return downstreamCallExecutor.execute(
                DownstreamServiceNames.MEMBER, () -> memberClient.getMyProfile().result());
    }

    public MemberClientResponse.AvailabilityDTO checkLoginIdAvailability(String loginId) {
        return downstreamCallExecutor.execute(
                DownstreamServiceNames.MEMBER,
                () -> memberClient.checkLoginIdAvailability(loginId).result());
    }

    public MemberClientResponse.AvailabilityDTO checkEmailAvailability(String email) {
        return downstreamCallExecutor.execute(
                DownstreamServiceNames.MEMBER,
                () -> memberClient.checkEmailAvailability(email).result());
    }

    public MemberClientResponse.AvailabilityDTO checkNicknameAvailability(String nickname) {
        return downstreamCallExecutor.execute(
                DownstreamServiceNames.MEMBER,
                () -> memberClient.checkNicknameAvailability(nickname).result());
    }

    /**
     * 요청한 모든 memberId에 닉네임을 채워 반환한다.
     * 닉네임은 부가 정보이므로 member-service 장애(5xx·타임아웃·연결 실패)나 서킷 열림 시 기본 닉네임으로 대체하고,
     * 4xx는 BFF의 잘못된 요청이므로 그대로 전파한다.
     */
    public Map<Long, String> getNicknameMap(Set<Long> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, String> fetched = downstreamCallExecutor.executeOrDefault(
                DownstreamServiceNames.MEMBER, () -> fetchNicknameMap(memberIds), Collections.emptyMap());

        Map<Long, String> nicknameMap = new HashMap<>();
        memberIds.forEach(memberId -> nicknameMap.put(memberId, fetched.getOrDefault(memberId, DEFAULT_NICKNAME)));
        return nicknameMap;
    }

    public String getMemberNickname(Long memberId) {
        return downstreamCallExecutor.executeOrDefault(
                DownstreamServiceNames.MEMBER,
                () -> {
                    MemberClientResponse.ProfileDTO profile =
                            memberClient.getMemberProfile(memberId).result();
                    return nicknameOrDefault(profile != null ? profile.nickname() : null);
                },
                DEFAULT_NICKNAME);
    }

    public String getMyNickname() {
        return downstreamCallExecutor.executeOrDefault(
                DownstreamServiceNames.MEMBER,
                () -> {
                    MemberClientResponse.MyProfileDTO profile =
                            memberClient.getMyProfile().result();
                    return nicknameOrDefault(profile != null ? profile.nickname() : null);
                },
                DEFAULT_NICKNAME);
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
}
