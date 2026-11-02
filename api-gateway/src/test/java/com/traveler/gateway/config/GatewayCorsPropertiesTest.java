package com.traveler.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.DefaultBootstrapContext;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.cloud.gateway.config.GlobalCorsProperties;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.web.cors.CorsConfiguration;

/** 기본 yml의 운영 CORS 값이 dev·prod에 그대로 적용되고, local만 완화되는지 실제 yml을 읽어 확인한다. */
class GatewayCorsPropertiesTest {

    @ParameterizedTest(name = "프로필: {0}")
    @ValueSource(strings = {"default", "dev", "prod"})
    @DisplayName("운영 기준 프로필은 필요한 헤더만 허용하고 기본값을 공유한다")
    void usesOperationalValues(String profile) {
        CorsConfiguration cors = corsOf(profile);

        assertThat(cors.getAllowedOrigins()).containsExactly("https://tripnow.shop");
        assertThat(cors.getAllowedHeaders()).containsExactly("Content-Type", "Authorization");
        assertThat(cors.getAllowedMethods()).contains("PATCH");
        assertThat(cors.getExposedHeaders()).contains("Authorization", "X-Trace-Id");
        assertThat(cors.getAllowCredentials()).isTrue();
    }

    @Test
    @DisplayName("local 프로필은 허용 헤더만 넓히고 나머지는 기본값을 따른다")
    void relaxesOnlyOnLocal() {
        CorsConfiguration cors = corsOf("local");

        assertThat(cors.getAllowedOrigins()).containsExactly("http://localhost:3000");
        assertThat(cors.getAllowedHeaders()).containsExactly("*");
        assertThat(cors.getAllowedMethods()).contains("PATCH");
        assertThat(cors.getExposedHeaders()).contains("Authorization", "X-Trace-Id");
    }

    private static CorsConfiguration corsOf(String profile) {
        StandardEnvironment environment = new StandardEnvironment();
        // 실행 환경의 ALLOWED_ORIGINS와 관계없이 yml에 적힌 기본값을 확인한다
        environment
                .getPropertySources()
                .replace(
                        StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                        new MapPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, Map.of()));
        List<String> profiles = "default".equals(profile) ? List.of() : List.of(profile);
        ConfigDataEnvironmentPostProcessor.applyTo(
                environment, new DefaultResourceLoader(), new DefaultBootstrapContext(), profiles);
        return Binder.get(environment)
                .bind("spring.cloud.gateway.globalcors", GlobalCorsProperties.class)
                .get()
                .getCorsConfigurations()
                .get("/**");
    }
}
