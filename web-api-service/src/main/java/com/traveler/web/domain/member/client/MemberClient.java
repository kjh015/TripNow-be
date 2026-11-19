package com.traveler.web.domain.member.client;

import com.traveler.common.core.response.ApiResponse;
import com.traveler.web.domain.member.client.dto.request.MemberClientRequest;
import com.traveler.web.domain.member.client.dto.response.MemberClientResponse;
import com.traveler.web.global.feign.config.FeignClientConfig;
import com.traveler.web.global.resilience.DownstreamServiceNames;
import java.util.List;
import java.util.Set;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(
        name = DownstreamServiceNames.MEMBER,
        contextId = "MemberClient",
        path = "/v1/members",
        configuration = FeignClientConfig.class)
public interface MemberClient {
    @PostMapping()
    ApiResponse<MemberClientResponse.SignUpDTO> signUp(@RequestBody MemberClientRequest.SignUpDTO dto);

    @DeleteMapping("/me")
    ApiResponse<MemberClientResponse.WithdrawDTO> withdraw();

    @PatchMapping("/me")
    ApiResponse<MemberClientResponse.UpdateDTO> updateMember(@RequestBody MemberClientRequest.UpdateDTO dto);

    @PatchMapping("/me/password")
    ApiResponse<MemberClientResponse.UpdatePasswordDTO> updatePassword(
            @RequestBody MemberClientRequest.UpdatePasswordDTO dto);

    @GetMapping
    ApiResponse<List<MemberClientResponse.ProfileDTO>> getMemberProfiles(@RequestParam Set<Long> memberIds);

    @GetMapping("/{memberId}")
    ApiResponse<MemberClientResponse.ProfileDTO> getMemberProfile(@PathVariable Long memberId);

    @GetMapping("/me")
    ApiResponse<MemberClientResponse.MyProfileDTO> getMyProfile();

    @GetMapping("/availability/login-id")
    ApiResponse<MemberClientResponse.AvailabilityDTO> checkLoginIdAvailability(@RequestParam String loginId);

    @GetMapping("/availability/email")
    ApiResponse<MemberClientResponse.AvailabilityDTO> checkEmailAvailability(@RequestParam String email);

    @GetMapping("/availability/nickname")
    ApiResponse<MemberClientResponse.AvailabilityDTO> checkNicknameAvailability(@RequestParam String nickname);
}
