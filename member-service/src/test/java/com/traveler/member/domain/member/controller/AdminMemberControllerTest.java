package com.traveler.member.domain.member.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.traveler.common.api.converter.PageConverter;
import com.traveler.member.domain.member.dto.request.AdminMemberRequest;
import com.traveler.member.domain.member.enums.Provider;
import com.traveler.member.domain.member.enums.RoleType;
import com.traveler.member.domain.member.service.command.AdminMemberCommandService;
import com.traveler.member.domain.member.service.query.AdminMemberQueryService;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AdminMemberControllerTest {

    @Mock
    private AdminMemberQueryService adminMemberQueryService;

    @Mock
    private AdminMemberCommandService adminMemberCommandService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new AdminMemberController(adminMemberQueryService, adminMemberCommandService))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    @DisplayName("쿼리 파라미터가 검색 조건으로 바인딩되고 정렬 기본값은 가입일 내림차순이다")
    void getMembers_bindsCondition() throws Exception {
        given(adminMemberQueryService.getMembers(any(), any())).willReturn(PageConverter.emptyPageResponse(10));

        mockMvc.perform(get("/v1/admin/members")
                        .param("keyword", "travel")
                        .param("provider", "KAKAO")
                        .param("role", "ROLE_ADMIN")
                        .param("deleted", "false")
                        .param("joinedFrom", "2026-10-01")
                        .param("joinedTo", ""))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(adminMemberQueryService)
                .getMembers(
                        eq(new AdminMemberRequest.SearchDTO(
                                "travel", Provider.KAKAO, RoleType.ROLE_ADMIN, false, LocalDate.of(2026, 10, 1), null)),
                        pageable.capture());
        assertThat(pageable.getValue().getSort().getOrderFor("createdAt").getDirection())
                .isEqualTo(Sort.Direction.DESC);
    }

    @Test
    @DisplayName("없는 권한 값은 400으로 끝나고 조회하지 않는다")
    void getMembers_rejectsUnknownRole() throws Exception {
        mockMvc.perform(get("/v1/admin/members").param("role", "ROLE_NOPE")).andExpect(status().isBadRequest());

        verifyNoInteractions(adminMemberQueryService);
    }
}
