package com.traveler.web.domain.member.adapter;

import com.traveler.web.domain.member.cache.MemberNicknameCache;
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
    private final MemberNicknameCache memberNicknameCache;

    public MemberClientResponse.SignUpDTO signUp(MemberClientRequest.SignUpDTO dto) {
        return downstreamCallExecutor.execute(
                DownstreamServiceNames.MEMBER, () -> memberClient.signUp(dto).result());
    }

    public MemberClientResponse.WithdrawDTO withdraw() {
        MemberClientResponse.WithdrawDTO response = downstreamCallExecutor.execute(
                DownstreamServiceNames.MEMBER, () -> memberClient.withdraw().result());
        memberNicknameCache.evict(response != null ? response.memberId() : null);
        return response;
    }

    public MemberClientResponse.UpdateDTO updateMember(MemberClientRequest.UpdateDTO dto) {
        MemberClientResponse.UpdateDTO response = downstreamCallExecutor.execute(
                DownstreamServiceNames.MEMBER,
                () -> memberClient.updateMember(dto).result());
        memberNicknameCache.evict(response != null ? response.memberId() : null);
        return response;
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
     * 캐시에 있는 회원은 그대로 쓰고, 없는 회원만 member-service에 묻는다(캐시 → 서킷 브레이커 → member 호출).
     * 닉네임은 부가 정보이므로 member-service 장애(5xx·타임아웃·연결 실패)나 서킷 열림 시 기본 닉네임으로 대체하고,
     * 4xx는 BFF의 잘못된 요청이므로 그대로 전파한다. 기본 닉네임은 캐시에 넣지 않는다.
     */
    public Map<Long, String> getNicknameMap(Set<Long> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, String> nicknameMap = new HashMap<>(memberNicknameCache.getAll(memberIds));
        Set<Long> missingIds = memberIds.stream()
                .filter(memberId -> !nicknameMap.containsKey(memberId))
                .collect(Collectors.toSet());
        if (missingIds.isEmpty()) {
            return nicknameMap;
        }

        Map<Long, String> fetched = downstreamCallExecutor.executeOrDefault(
                DownstreamServiceNames.MEMBER, () -> fetchNicknameMap(missingIds), Collections.emptyMap());
        memberNicknameCache.putAll(fetched);

        missingIds.forEach(memberId -> nicknameMap.put(memberId, fetched.getOrDefault(memberId, DEFAULT_NICKNAME)));
        return nicknameMap;
    }

    /** 캐시 → 서킷 브레이커 → member 호출 순서. 장애로 받은 기본 닉네임은 캐시에 넣지 않는다. */
    public String getMemberNickname(Long memberId) {
        String cached = memberNicknameCache.get(memberId);
        if (cached != null) {
            return cached;
        }

        String nickname = downstreamCallExecutor.executeOrDefault(
                DownstreamServiceNames.MEMBER,
                () -> {
                    MemberClientResponse.ProfileDTO profile =
                            memberClient.getMemberProfile(memberId).result();
                    return profile != null ? profile.nickname() : null;
                },
                null);
        if (nickname == null) {
            return DEFAULT_NICKNAME;
        }
        memberNicknameCache.put(memberId, nickname);
        return nickname;
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
