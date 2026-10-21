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

import com.traveler.common.support.logging.MdcKeys;
import com.traveler.common.support.logging.audit.AuditLogger;
import com.traveler.member.domain.auth.repository.RefreshTokenRepository;
import com.traveler.member.domain.auth.repository.TokenBlacklistRepository;
import com.traveler.member.domain.auth.support.AuthTokenRevoker;
import com.traveler.member.domain.auth.support.JwtTokenProvider;
import com.traveler.member.domain.member.dto.response.AdminMemberResponse;
import com.traveler.member.domain.member.entity.Member;
import com.traveler.member.domain.member.enums.RoleType;
import com.traveler.member.domain.member.mapper.MemberMapper;
import com.traveler.member.domain.member.repository.MemberRepository;
import com.traveler.member.testsupport.fixture.MemberFixture;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.redis.RedisConnectionFailureException;

@ExtendWith(OutputCaptureExtension.class)
class AdminMemberCommandServiceTest {

    private static final Long MEMBER_ID = 7L;
    private static final Long ADMIN_ID = 1L;

    private final MemberRepository memberRepository = mock(MemberRepository.class);
    private final MemberMapper memberMapper = mock(MemberMapper.class);
    private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
    private final TokenBlacklistRepository tokenBlacklistRepository = mock(TokenBlacklistRepository.class);
    private final JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);

    private final AdminMemberCommandService adminMemberCommandService = new AdminMemberCommandService(
            memberRepository,
            memberMapper,
            new AuthTokenRevoker(refreshTokenRepository, tokenBlacklistRepository, jwtTokenProvider),
            new AuditLogger());

    private Member member;

    @BeforeEach
    void setUp() {
        member = MemberFixture.member(MEMBER_ID);
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
        given(memberMapper.toDeleteDTO(any())).willAnswer(invocation -> {
            Member m = invocation.getArgument(0);
            return new AdminMemberResponse.DeleteDTO(m.getId(), m.getDeletedAt());
        });
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    @DisplayName("관리자 권한을 부여하면 요청 관리자와 대상 회원을 AUDIT 로거로 남긴다")
    void grantAdminRole_writesAuditLog(CapturedOutput output) {
        // 요청 스레드에서는 UserContextInterceptor가 MDC에 요청 사용자 ID를 넣는다
        MDC.put(MdcKeys.USER_ID, String.valueOf(ADMIN_ID));
        given(memberRepository.findByIdWithRoles(MEMBER_ID)).willReturn(Optional.of(member));

        adminMemberCommandService.grantAdminRole(MEMBER_ID);

        assertThat(member.getRoleTypes()).contains(RoleType.ROLE_ADMIN);
        assertThat(output)
                .contains("AUDIT")
                .contains("action=GRANT_ADMIN actor=" + ADMIN_ID + " target=member:" + MEMBER_ID);
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
