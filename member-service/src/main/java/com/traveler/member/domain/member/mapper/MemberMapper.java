package com.traveler.member.domain.member.mapper;

import com.traveler.member.domain.member.dto.event.MemberEvent;
import com.traveler.member.domain.member.dto.message.MemberMessage;
import com.traveler.member.domain.member.dto.request.MemberRequest;
import com.traveler.member.domain.member.dto.response.AdminMemberResponse;
import com.traveler.member.domain.member.dto.response.MemberResponse;
import com.traveler.member.domain.member.entity.Member;
import com.traveler.member.domain.member.entity.MemberWithdrawal;
import com.traveler.member.domain.member.enums.AvailabilityType;
import com.traveler.member.domain.member.enums.WithdrawalReason;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface MemberMapper {
    @Mapping(source = "encodedPassword", target = "password")
    Member toCreateEntity(MemberRequest.SignUpDTO dto, String encodedPassword);

    @Mapping(source = "id", target = "memberId")
    MemberResponse.SignUpDTO toSignUpDTO(Member member);

    @Mapping(source = "id", target = "memberId")
    MemberResponse.WithdrawDTO toWithdrawDTO(Member member);

    @Mapping(source = "id", target = "memberId")
    MemberResponse.UpdateDTO toUpdateDTO(Member member);

    @Mapping(source = "id", target = "memberId")
    MemberResponse.UpdatePasswordDTO toUpdatePasswordDTO(Member member);

    @Mapping(source = "member.id", target = "memberId")
    MemberResponse.ProfileDTO toProfileDTO(Member member, Integer age);

    @Mapping(source = "member.id", target = "memberId")
    @Mapping(source = "member.roleTypes", target = "roles")
    MemberResponse.MyProfileDTO toMyProfileDTO(Member member, Integer age);

    MemberResponse.AvailabilityDTO toAvailabilityDTO(boolean isAvailable, String value, AvailabilityType reason);

    // Admin
    @Mapping(source = "id", target = "memberId")
    @Mapping(source = "member.roleTypes", target = "roles")
    AdminMemberResponse.GrantAdminDTO toGrantAdminDTO(Member member);

    @Mapping(source = "id", target = "memberId")
    AdminMemberResponse.DeleteDTO toDeleteDTO(Member member);

    @Mapping(source = "id", target = "memberId")
    @Mapping(source = "member.roleTypes", target = "roles")
    AdminMemberResponse.ListDTO toListDTO(Member member);

    @Mapping(source = "member.id", target = "memberId")
    @Mapping(source = "member.roleTypes", target = "roles")
    @Mapping(source = "member.createdAt", target = "createdAt")
    @Mapping(source = "member.updatedAt", target = "updatedAt")
    @Mapping(source = "member.deleted", target = "isDeleted")
    AdminMemberResponse.DetailDTO toDetailDTO(Member member, MemberWithdrawal withdrawal);

    @Mapping(source = "createdAt", target = "withdrawnAt")
    AdminMemberResponse.PendingWithdrawalDTO toPendingWithdrawalDTO(MemberWithdrawal withdrawal);

    // Message
    @Mapping(source = "member.id", target = "memberId")
    @Mapping(source = "member.deletedAt", target = "withdrawnAt")
    MemberMessage.WithdrawnDTO toWithdrawnMsgDTO(Member member, WithdrawalReason reason);

    // Event
    default MemberEvent.Withdrawn toWithdrawnEvent(Member member, WithdrawalReason reason) {
        return new MemberEvent.Withdrawn(toWithdrawnMsgDTO(member, reason));
    }
}
