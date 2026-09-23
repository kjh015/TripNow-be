package com.traveler.web.domain.member.adapter;

import com.traveler.common.core.response.PageResponse;
import com.traveler.web.domain.member.client.AdminMemberClient;
import com.traveler.web.domain.member.client.dto.response.AdminMemberClientResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminMemberClientAdapter {
    private final AdminMemberClient adminMemberClient;

    public AdminMemberClientResponse.GrantAdminDTO grantAdminRole(Long memberId) {
        return adminMemberClient.grantAdminRole(memberId).result();
    }

    public AdminMemberClientResponse.DeleteDTO deleteMember(Long memberId) {
        return adminMemberClient.deleteMember(memberId).result();
    }

    public PageResponse<AdminMemberClientResponse.ListDTO> getMembers(Pageable pageable) {
        return adminMemberClient.getMembers(pageable).result();
    }

    public AdminMemberClientResponse.DetailDTO getMember(Long memberId) {
        return adminMemberClient.getMember(memberId).result();
    }
}
