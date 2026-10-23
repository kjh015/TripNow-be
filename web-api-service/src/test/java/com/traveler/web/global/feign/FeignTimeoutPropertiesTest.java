package com.traveler.web.global.feign;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.DefaultBootstrapContext;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.cloud.openfeign.FeignClientProperties;
import org.springframework.cloud.openfeign.support.FeignHttpClientProperties;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;

/**
 * 프로필과 관계없이 같은 Feign 타임아웃 예산(검색 2s + 프로필 1.5s = 3.5s < 게이트웨이 5s)이 적용되는지 확인한다.
 * 컨텍스트를 띄우지 않고 실제 yml(application*.yml + 공통 모듈 import)만 읽어 바인딩한다.
 */
class FeignTimeoutPropertiesTest {

    @ParameterizedTest(name = "프로필: {0}")
    @ValueSource(strings = {"default", "local", "dev", "prod"})
    @DisplayName("모든 프로필에서 같은 Feign 타임아웃과 커넥션 풀 설정이 적용된다")
    void appliesSameTimeoutBudgetForEveryProfile(String profile) {
        Binder binder = Binder.get(loadEnvironment(profile));

        FeignClientProperties client = binder.bind("spring.cloud.openfeign.client", FeignClientProperties.class)
                .get();
        FeignClientProperties.FeignClientConfiguration defaults =
                client.getConfig().get("default");
        FeignClientProperties.FeignClientConfiguration searchPost =
                client.getConfig().get("SearchPostClient");
        assertThat(defaults.getConnectTimeout()).isEqualTo(1000);
        assertThat(defaults.getReadTimeout()).isEqualTo(1500);
        assertThat(searchPost.getReadTimeout()).isEqualTo(2000);

        FeignHttpClientProperties httpClient = binder.bind(
                        "spring.cloud.openfeign.httpclient", FeignHttpClientProperties.class)
                .get();
        assertThat(binder.bind("spring.cloud.openfeign.httpclient.hc5.enabled", Boolean.class)
                        .get())
                .isTrue();
        assertThat(httpClient.getHc5().getConnectionRequestTimeout()).isEqualTo(500);
        assertThat(httpClient.getHc5().getConnectionRequestTimeoutUnit()).isEqualTo(TimeUnit.MILLISECONDS);
        assertThat(httpClient.getMaxConnectionsPerRoute()).isEqualTo(100);
    }

    private static StandardEnvironment loadEnvironment(String profile) {
        StandardEnvironment environment = new StandardEnvironment();
        List<String> profiles = "default".equals(profile) ? List.of() : List.of(profile);
        ConfigDataEnvironmentPostProcessor.applyTo(
                environment, new DefaultResourceLoader(), new DefaultBootstrapContext(), profiles);
        return environment;
    }
}
