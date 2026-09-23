package com.traveler.web.domain.member.facade;

import com.traveler.common.core.response.PageResponse;
import com.traveler.web.domain.member.adapter.AdminMemberClientAdapter;
import com.traveler.web.domain.member.client.dto.response.AdminMemberClientResponse;
import com.traveler.web.domain.member.dto.response.AdminMemberResponse;
import com.traveler.web.domain.member.mapper.MemberMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminMemberFacade {
    private final AdminMemberClientAdapter adminMemberClientAdapter;
    private final MemberMapper memberMapper;

    public AdminMemberResponse.GrantAdminDTO grantAdminRole(Long memberId) {
        AdminMemberClientResponse.GrantAdminDTO clientResponse = adminMemberClientAdapter.grantAdminRole(memberId);
        return memberMapper.toResponseGrantAdminDTO(clientResponse);
    }

    public AdminMemberResponse.DeleteDTO deleteMember(Long memberId) {
        AdminMemberClientResponse.DeleteDTO clientResponse = adminMemberClientAdapter.deleteMember(memberId);
        return memberMapper.toResponseDeleteDTO(clientResponse);
    }

    public PageResponse<AdminMemberResponse.ListDTO> getMembers(Pageable pageable) {
        PageResponse<AdminMemberClientResponse.ListDTO> clientResponse = adminMemberClientAdapter.getMembers(pageable);
        return clientResponse.map(memberMapper::toResponseListDTO);
    }

    public AdminMemberResponse.DetailDTO getMember(Long memberId) {
        AdminMemberClientResponse.DetailDTO clientResponse = adminMemberClientAdapter.getMember(memberId);
        return memberMapper.toResponseDetailDTO(clientResponse);
    }
}
