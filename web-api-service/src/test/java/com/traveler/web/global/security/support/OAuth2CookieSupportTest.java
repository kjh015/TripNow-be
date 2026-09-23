package com.traveler.web.global.security.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationResponseType;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;

class OAuth2CookieSupportTest {

    // 애플리케이션이 주입받는 Spring Boot 기본 ObjectMapper와 같은 방식으로 생성
    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();
    private final OAuth2CookieSupport cookieSupport = new OAuth2CookieSupport(false, objectMapper);

    @Test
    @DisplayName("OAuth2AuthorizationRequest를 쿠키 값으로 직렬화한 뒤 역직렬화하면 원래 요청이 복원된다")
    void serializeAndDeserialize_roundTrip_restoresAuthorizationRequest() {
        OAuth2AuthorizationRequest original = OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://kauth.kakao.com/oauth/authorize")
                .clientId("kakao-client-id")
                .redirectUri("https://tripnow.example.com/login/oauth2/code/kakao")
                .scopes(Set.of("profile_nickname", "account_email"))
                .state("state-value")
                .additionalParameters(Map.of("prompt", "login"))
                .attributes(Map.of(OAuth2ParameterNames.REGISTRATION_ID, "kakao"))
                .build();

        Cookie cookie = new Cookie("oauth2_auth_request", cookieSupport.serialize(original));
        OAuth2AuthorizationRequest restored = cookieSupport.deserialize(cookie, OAuth2AuthorizationRequest.class);

        assertThat(restored.getAuthorizationUri()).isEqualTo(original.getAuthorizationUri());
        assertThat(restored.getGrantType()).isEqualTo(original.getGrantType());
        assertThat(restored.getResponseType()).isEqualTo(OAuth2AuthorizationResponseType.CODE);
        assertThat(restored.getClientId()).isEqualTo(original.getClientId());
        assertThat(restored.getRedirectUri()).isEqualTo(original.getRedirectUri());
        assertThat(restored.getScopes()).isEqualTo(original.getScopes());
        assertThat(restored.getState()).isEqualTo(original.getState());
        assertThat(restored.getAdditionalParameters()).isEqualTo(original.getAdditionalParameters());
        assertThat(restored.<String>getAttribute(OAuth2ParameterNames.REGISTRATION_ID))
                .isEqualTo("kakao");
        assertThat(restored.getAuthorizationRequestUri()).isEqualTo(original.getAuthorizationRequestUri());
    }

    @Test
    @DisplayName("쿠키 전용 Jackson 모듈 등록이 주입받은 전역 ObjectMapper에 영향을 주지 않는다")
    void constructor_doesNotModifyInjectedObjectMapper() {
        assertThat(objectMapper.getRegisteredModuleIds())
                .noneMatch(id -> id.toString().contains("security"));
    }
}
