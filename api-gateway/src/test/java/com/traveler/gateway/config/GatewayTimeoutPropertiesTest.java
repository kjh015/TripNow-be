package com.traveler.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.DefaultBootstrapContext;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.cloud.gateway.config.HttpClientProperties;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;

/**
 * 게이트웨이 response-timeout이 모든 프로필에서 web-api Feign 타임아웃 예산(3.5s)보다 긴 5s인지 확인한다.
 * 컨텍스트를 띄우지 않고 실제 yml만 읽어 바인딩한다.
 */
class GatewayTimeoutPropertiesTest {

    @ParameterizedTest(name = "프로필: {0}")
    @ValueSource(strings = {"default", "local", "dev", "prod"})
    @DisplayName("모든 프로필에서 response-timeout은 5초다")
    void appliesSameResponseTimeoutForEveryProfile(String profile) {
        HttpClientProperties httpClient = Binder.get(loadEnvironment(profile))
                .bind("spring.cloud.gateway.httpclient", HttpClientProperties.class)
                .get();

        assertThat(httpClient.getResponseTimeout()).isEqualTo(Duration.ofSeconds(5));
    }

    private static StandardEnvironment loadEnvironment(String profile) {
        StandardEnvironment environment = new StandardEnvironment();
        List<String> profiles = "default".equals(profile) ? List.of() : List.of(profile);
        ConfigDataEnvironmentPostProcessor.applyTo(
                environment, new DefaultResourceLoader(), new DefaultBootstrapContext(), profiles);
        return environment;
    }
}
