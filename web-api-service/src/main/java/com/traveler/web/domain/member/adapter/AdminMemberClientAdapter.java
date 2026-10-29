package com.traveler.web.domain.member.adapter;

import com.traveler.common.core.response.PageResponse;
import com.traveler.web.domain.member.cache.MemberNicknameCache;
import com.traveler.web.domain.member.client.AdminMemberClient;
import com.traveler.web.domain.member.client.dto.response.AdminMemberClientResponse;
import com.traveler.web.global.resilience.DownstreamCallExecutor;
import com.traveler.web.global.resilience.DownstreamServiceNames;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminMemberClientAdapter {
    private final AdminMemberClient adminMemberClient;
    private final DownstreamCallExecutor downstreamCallExecutor;
    private final MemberNicknameCache memberNicknameCache;

    public AdminMemberClientResponse.GrantAdminDTO grantAdminRole(Long memberId) {
        return downstreamCallExecutor.execute(
                DownstreamServiceNames.MEMBER,
                () -> adminMemberClient.grantAdminRole(memberId).result());
    }

    public AdminMemberClientResponse.DeleteDTO deleteMember(Long memberId) {
        AdminMemberClientResponse.DeleteDTO response = downstreamCallExecutor.execute(
                DownstreamServiceNames.MEMBER,
                () -> adminMemberClient.deleteMember(memberId).result());
        memberNicknameCache.evict(memberId);
        return response;
    }

    public PageResponse<AdminMemberClientResponse.ListDTO> getMembers(Pageable pageable) {
        return downstreamCallExecutor.execute(
                DownstreamServiceNames.MEMBER,
                () -> adminMemberClient.getMembers(pageable).result());
    }

    public AdminMemberClientResponse.DetailDTO getMember(Long memberId) {
        return downstreamCallExecutor.execute(
                DownstreamServiceNames.MEMBER,
                () -> adminMemberClient.getMember(memberId).result());
    }
}
