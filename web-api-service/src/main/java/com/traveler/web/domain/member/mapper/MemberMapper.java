package com.traveler.web.domain.member.mapper;

import com.traveler.web.domain.member.client.dto.request.AdminMemberClientRequest;
import com.traveler.web.domain.member.client.dto.request.MemberClientRequest;
import com.traveler.web.domain.member.client.dto.response.AdminMemberClientResponse;
import com.traveler.web.domain.member.client.dto.response.MemberClientResponse;
import com.traveler.web.domain.member.dto.request.AdminMemberRequest;
import com.traveler.web.domain.member.dto.request.MemberRequest;
import com.traveler.web.domain.member.dto.response.AdminMemberResponse;
import com.traveler.web.domain.member.dto.response.MemberResponse;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface MemberMapper {
    // --- Request Mapping (Web -> Client) ---
    MemberClientRequest.SignUpDTO toClientSignUpDTO(MemberRequest.SignUpDTO request);

    MemberClientRequest.UpdateDTO toClientUpdateDTO(MemberRequest.UpdateDTO request);

    MemberClientRequest.UpdatePasswordDTO toClientUpdatePasswordDTO(MemberRequest.UpdatePasswordDTO request);

    AdminMemberClientRequest.SearchDTO toClientSearchDTO(AdminMemberRequest.SearchDTO request);

    // --- Response Mapping (Client -> Web) ---
    MemberResponse.SignUpDTO toResponseSignUpDTO(MemberClientResponse.SignUpDTO clientResponse);

    MemberResponse.WithdrawDTO toResponseWithdrawDTO(MemberClientResponse.WithdrawDTO clientResponse);

    MemberResponse.UpdateDTO toResponseUpdateDTO(MemberClientResponse.UpdateDTO clientResponse);

    MemberResponse.UpdatePasswordDTO toResponseUpdatePasswordDTO(MemberClientResponse.UpdatePasswordDTO clientResponse);

    MemberResponse.MyProfileDTO toResponseMyProfileDTO(MemberClientResponse.MyProfileDTO clientResponse);

    MemberResponse.AvailabilityDTO toResponseAvailabilityDTO(MemberClientResponse.AvailabilityDTO clientResponse);

    // Admin
    AdminMemberResponse.GrantAdminDTO toResponseGrantAdminDTO(AdminMemberClientResponse.GrantAdminDTO clientResponse);

    AdminMemberResponse.DeleteDTO toResponseDeleteDTO(AdminMemberClientResponse.DeleteDTO clientResponse);

    AdminMemberResponse.ListDTO toResponseListDTO(AdminMemberClientResponse.ListDTO listDTO);

    AdminMemberResponse.DetailDTO toResponseDetailDTO(AdminMemberClientResponse.DetailDTO clientResponse);
}
