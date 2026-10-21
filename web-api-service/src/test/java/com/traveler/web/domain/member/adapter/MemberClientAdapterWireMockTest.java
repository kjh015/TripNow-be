package com.traveler.web.domain.member.adapter;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import com.traveler.web.domain.member.client.MemberClient;
import com.traveler.web.global.exception.WebApiServiceException;
import com.traveler.web.global.exception.code.WebApiServiceErrorCode;
import feign.codec.DecodeException;
import java.util.Map;
import java.util.Set;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 실제 Feign 스택(FeignClientConfig의 디코더·에러 디코더, 타임아웃)을 거쳐 member-service 응답이
 * 어떤 예외·닉네임으로 바뀌는지 WireMock으로 확인한다. Docker가 필요 없어 단위 테스트(test)에서 돈다.
 */
@SpringBootTest(classes = MemberClientAdapterWireMockTest.FeignTestConfig.class)
class MemberClientAdapterWireMockTest {

    private static final int READ_TIMEOUT_MILLIS = 500;

    private static final WireMockServer WIRE_MOCK = new WireMockServer(options().dynamicPort());

    static {
        WIRE_MOCK.start();
    }

    @Configuration
    @ImportAutoConfiguration({
        JacksonAutoConfiguration.class,
        HttpMessageConvertersAutoConfiguration.class,
        FeignAutoConfiguration.class
    })
    @EnableFeignClients(clients = MemberClient.class)
    @Import(MemberClientAdapter.class)
    static class FeignTestConfig {}

    @DynamicPropertySource
    static void feignProperties(DynamicPropertyRegistry registry) {
        // url을 주면 Eureka·로드밸런서 없이 WireMock으로 바로 호출한다
        registry.add("spring.cloud.openfeign.client.config.MemberClient.url", WIRE_MOCK::baseUrl);
        registry.add("spring.cloud.openfeign.client.config.default.connect-timeout", () -> 500);
        registry.add("spring.cloud.openfeign.client.config.default.read-timeout", () -> READ_TIMEOUT_MILLIS);
    }

    @Autowired
    private MemberClientAdapter memberClientAdapter;

    @BeforeEach
    void resetStubs() {
        WIRE_MOCK.resetAll();
    }

    @AfterAll
    static void stopWireMock() {
        WIRE_MOCK.stop();
    }

    @Test
    @DisplayName("정상 응답이면 member-service의 닉네임을 쓴다")
    void success() {
        WIRE_MOCK.stubFor(
                get(urlPathEqualTo("/v1/members/1"))
                        .willReturn(
                                okJson(
                                        "{\"success\":true,\"code\":\"COMMON200\",\"message\":\"OK\",\"result\":{\"memberId\":1,\"nickname\":\"여행자\"}}")));

        assertThat(memberClientAdapter.getMemberNickname(1L)).isEqualTo("여행자");
    }

    @Test
    @DisplayName("member-service 5xx는 기본 닉네임으로 대체한다")
    void serverErrorFallsBackToDefault() {
        WIRE_MOCK.stubFor(get(urlPathEqualTo("/v1/members/1"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"success\":false,\"code\":\"COMMON500\",\"message\":\"서버 오류\",\"result\":null}")));

        assertThat(memberClientAdapter.getMemberNickname(1L)).isEqualTo(MemberClientAdapter.DEFAULT_NICKNAME);
    }

    @Test
    @DisplayName("read timeout을 넘기면 기본 닉네임으로 대체한다")
    void timeoutFallsBackToDefault() {
        WIRE_MOCK.stubFor(get(urlPathEqualTo("/v1/members/1"))
                .willReturn(okJson("{\"success\":true,\"result\":{\"memberId\":1,\"nickname\":\"늦은 응답\"}}")
                        .withFixedDelay(READ_TIMEOUT_MILLIS * 3)));

        assertThat(memberClientAdapter.getMemberNickname(1L)).isEqualTo(MemberClientAdapter.DEFAULT_NICKNAME);
    }

    @Test
    @DisplayName("연결이 끊기면 목록 조회의 모든 회원을 기본 닉네임으로 채운다")
    void connectionResetFallsBackToDefaultForAll() {
        WIRE_MOCK.stubFor(
                get(urlPathEqualTo("/v1/members")).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        Map<Long, String> nicknames = memberClientAdapter.getNicknameMap(Set.of(1L, 2L));

        assertThat(nicknames)
                .containsOnly(
                        Map.entry(1L, MemberClientAdapter.DEFAULT_NICKNAME),
                        Map.entry(2L, MemberClientAdapter.DEFAULT_NICKNAME));
    }

    @Test
    @DisplayName("member-service 4xx는 BFF의 잘못된 요청이므로 하위 코드 그대로 예외를 전파한다")
    void clientErrorPropagates() {
        WIRE_MOCK.stubFor(get(urlPathEqualTo("/v1/members/1"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                                "{\"success\":false,\"code\":\"MEMBER404_1\",\"message\":\"회원 없음\",\"result\":null}")));

        assertThatThrownBy(() -> memberClientAdapter.getMemberNickname(1L))
                .isInstanceOf(WebApiServiceException.class)
                .satisfies(ex -> {
                    WebApiServiceException webEx = (WebApiServiceException) ex;
                    assertThat(webEx.getCode().getStatus()).isEqualTo(404);
                    assertThat(webEx.getCode().getCode()).isEqualTo("MEMBER404_1");
                });
    }

    @Test
    @DisplayName("성공 응답인데 result가 비어 있으면 DecodeException으로 감싸진 채 전파되어 기본 닉네임을 쓰지 않는다(현재 동작)")
    void emptyResultPropagatesAsDecodeException() {
        // ValidatedFeignDecoder의 SERVICE_RESPONSE_EMPTY(500)를 Feign이 DecodeException으로 감싸
        // ExceptionUtils.isRetryableException()이 장애로 보지 않는다. 동작을 바꾸면 이 테스트도 함께 고친다
        WIRE_MOCK.stubFor(get(urlPathEqualTo("/v1/members/1"))
                .willReturn(okJson("{\"success\":true,\"code\":\"COMMON200\",\"message\":\"OK\",\"result\":null}")));

        assertThatThrownBy(() -> memberClientAdapter.getMemberNickname(1L))
                .isInstanceOf(DecodeException.class)
                .cause()
                .isInstanceOf(WebApiServiceException.class)
                .extracting("code")
                .isEqualTo(WebApiServiceErrorCode.SERVICE_RESPONSE_EMPTY);
    }
}
