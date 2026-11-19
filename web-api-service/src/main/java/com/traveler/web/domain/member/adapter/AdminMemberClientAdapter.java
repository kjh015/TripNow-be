package com.traveler.web.domain.member.adapter;

import com.traveler.common.core.response.PageResponse;
import com.traveler.web.domain.member.cache.MemberNicknameCache;
import com.traveler.web.domain.member.client.AdminMemberClient;
import com.traveler.web.domain.member.client.dto.request.AdminMemberClientRequest;
import com.traveler.web.domain.member.client.dto.response.AdminMemberClientResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminMemberClientAdapter {
    private final AdminMemberClient adminMemberClient;
    private final MemberNicknameCache memberNicknameCache;

    public AdminMemberClientResponse.GrantAdminDTO grantAdminRole(Long memberId) {
        return adminMemberClient.grantAdminRole(memberId).result();
    }

    public AdminMemberClientResponse.DeleteDTO deleteMember(Long memberId) {
        AdminMemberClientResponse.DeleteDTO response =
                adminMemberClient.deleteMember(memberId).result();
        memberNicknameCache.evict(memberId);
        return response;
    }

    public PageResponse<AdminMemberClientResponse.ListDTO> getMembers(
            AdminMemberClientRequest.SearchDTO condition, Pageable pageable) {
        return adminMemberClient
                .getMembers(
                        condition.keyword(),
                        condition.provider(),
                        condition.role(),
                        condition.deleted(),
                        condition.joinedFrom(),
                        condition.joinedTo(),
                        pageable)
                .result();
    }

    public PageResponse<AdminMemberClientResponse.PendingWithdrawalDTO> getPendingWithdrawals(Pageable pageable) {
        return adminMemberClient.getPendingWithdrawals(pageable).result();
    }

    public AdminMemberClientResponse.DetailDTO getMember(Long memberId) {
        return adminMemberClient.getMember(memberId).result();
    }
}
