package com.traveler.web.domain.member.adapter;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.traveler.web.domain.member.cache.MemberNicknameCache;
import com.traveler.web.domain.member.client.AdminMemberClient;
import com.traveler.web.domain.member.client.dto.request.AdminMemberClientRequest;
import com.traveler.web.domain.member.enums.Provider;
import com.traveler.web.domain.member.enums.RoleType;
import com.traveler.web.global.resilience.DownstreamCallExecutor;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** 실제 Feign 스택을 거쳐 관리자 회원 검색 조건과 페이지 정보가 member-service 쿼리 파라미터로 나가는지 WireMock으로 확인한다. */
@SpringBootTest(classes = AdminMemberClientWireMockTest.FeignTestConfig.class)
class AdminMemberClientWireMockTest {

    private static final String EMPTY_PAGE =
            """
            {"isSuccess":true,"code":"COMMON200","message":"OK","result":{"content":[],"currentPage":1,"size":10,
            "totalElements":0,"totalPages":0,"isFirst":true,"isLast":true}}
            """;

    private static final WireMockServer WIRE_MOCK = new WireMockServer(options().dynamicPort());

    static {
        WIRE_MOCK.start();
    }

    @Configuration
    @ImportAutoConfiguration({
        JacksonAutoConfiguration.class,
        HttpMessageConvertersAutoConfiguration.class,
        FeignAutoConfiguration.class,
        CircuitBreakerAutoConfiguration.class
    })
    @EnableFeignClients(clients = AdminMemberClient.class)
    @Import({AdminMemberClientAdapter.class, DownstreamCallExecutor.class, MemberNicknameCache.class})
    static class FeignTestConfig {}

    @DynamicPropertySource
    static void feignProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.openfeign.client.config.AdminClient.url", WIRE_MOCK::baseUrl);
    }

    @Autowired
    private AdminMemberClientAdapter adminMemberClientAdapter;

    @BeforeEach
    void resetStubs() {
        WIRE_MOCK.resetAll();
        WIRE_MOCK.stubFor(get(urlPathEqualTo("/v1/admin/members")).willReturn(okJson(EMPTY_PAGE)));
    }

    @AfterAll
    static void stopWireMock() {
        WIRE_MOCK.stop();
    }

    @Test
    @DisplayName("검색 조건과 페이지·정렬이 모두 쿼리 파라미터로 나간다")
    void getMembers_sendsConditionAndPageable() {
        AdminMemberClientRequest.SearchDTO condition = new AdminMemberClientRequest.SearchDTO(
                "여행",
                Provider.KAKAO,
                RoleType.ROLE_ADMIN,
                false,
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31));

        adminMemberClientAdapter.getMembers(
                condition, PageRequest.of(2, 20, Sort.by(Sort.Direction.DESC, "createdAt")));

        WIRE_MOCK.verify(getRequestedFor(urlPathEqualTo("/v1/admin/members"))
                .withQueryParam("keyword", equalTo("여행"))
                .withQueryParam("provider", equalTo("KAKAO"))
                .withQueryParam("role", equalTo("ROLE_ADMIN"))
                .withQueryParam("deleted", equalTo("false"))
                .withQueryParam("joinedFrom", equalTo("2026-10-01"))
                .withQueryParam("joinedTo", equalTo("2026-10-31"))
                .withQueryParam("page", equalTo("2"))
                .withQueryParam("size", equalTo("20"))
                .withQueryParam("sort", equalTo("createdAt,DESC")));
    }

    @Test
    @DisplayName("비운 조건은 쿼리 파라미터로 보내지 않는다")
    void getMembers_omitsEmptyCondition() {
        adminMemberClientAdapter.getMembers(
                new AdminMemberClientRequest.SearchDTO(null, null, null, null, null, null), PageRequest.of(0, 10));

        String url = WIRE_MOCK.getAllServeEvents().get(0).getRequest().getUrl();
        assertThat(url).doesNotContain("keyword", "provider", "role", "deleted", "joined");
    }
}
