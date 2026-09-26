package com.traveler.member.domain.member.service.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.traveler.member.domain.auth.repository.RefreshTokenRepository;
import com.traveler.member.domain.auth.repository.TokenBlacklistRepository;
import com.traveler.member.domain.auth.support.AuthTokenRevoker;
import com.traveler.member.domain.auth.support.JwtTokenProvider;
import com.traveler.member.domain.member.dto.response.AdminMemberResponse;
import com.traveler.member.domain.member.entity.Member;
import com.traveler.member.domain.member.mapper.MemberMapper;
import com.traveler.member.domain.member.repository.MemberRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.test.util.ReflectionTestUtils;

class AdminMemberCommandServiceTest {

    private static final Long MEMBER_ID = 7L;

    private final MemberRepository memberRepository = mock(MemberRepository.class);
    private final MemberMapper memberMapper = mock(MemberMapper.class);
    private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
    private final TokenBlacklistRepository tokenBlacklistRepository = mock(TokenBlacklistRepository.class);
    private final JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);

    private final AdminMemberCommandService adminMemberCommandService = new AdminMemberCommandService(
            memberRepository,
            memberMapper,
            new AuthTokenRevoker(refreshTokenRepository, tokenBlacklistRepository, jwtTokenProvider));

    private Member member;

    @BeforeEach
    void setUp() {
        member = Member.builder().loginId("traveler").nickname("여행자").build();
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
        given(memberMapper.toDeleteDTO(any())).willAnswer(invocation -> {
            Member m = invocation.getArgument(0);
            return new AdminMemberResponse.DeleteDTO(m.getId(), m.getDeletedAt());
        });
    }

    @Test
    @DisplayName("강제 탈퇴하면 대상 회원의 리프레시 토큰을 지운다")
    void deleteMember_deletesRefreshToken() {
        adminMemberCommandService.deleteMember(MEMBER_ID);

        assertThat(member.isDeleted()).isTrue();
        verify(refreshTokenRepository).deleteByMemberId(MEMBER_ID);
        verify(tokenBlacklistRepository, never()).save(anyString(), anyLong());
    }

    @Test
    @DisplayName("리프레시 토큰 삭제 중 Redis 예외가 나도 강제 탈퇴는 성공한다")
    void deleteMember_redisFailure_stillSucceeds() {
        willThrow(new RedisConnectionFailureException("down"))
                .given(refreshTokenRepository)
                .deleteByMemberId(MEMBER_ID);

        AdminMemberResponse.DeleteDTO result = adminMemberCommandService.deleteMember(MEMBER_ID);

        assertThat(member.isDeleted()).isTrue();
        assertThat(result.memberId()).isEqualTo(MEMBER_ID);
    }
}
