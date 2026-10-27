package com.traveler.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.traveler.gateway.config.RateLimitProperties.KeyType;
import com.traveler.gateway.config.RateLimitProperties.Policy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class RateLimitPropertiesTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(PropertiesConfig.class);

    @Test
    @DisplayName("기준·한도가 모두 있고 requested-tokens가 burst-capacity 이하면 만들어진다")
    void validPolicy() {
        assertThatNoException().isThrownBy(() -> new Policy(KeyType.IP, 1, 60, 6));
        assertThatNoException().isThrownBy(() -> new Policy(KeyType.USER, 5, 10, 10));
    }

    @Test
    @DisplayName("key가 없으면 실패한다")
    void missingKey() {
        assertThatThrownBy(() -> new Policy(null, 1, 60, 6))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("key");
    }

    @Test
    @DisplayName("한도 값이 1보다 작으면(빠뜨려서 0으로 바인딩된 경우 포함) 실패한다")
    void nonPositiveLimits() {
        assertThatThrownBy(() -> new Policy(KeyType.IP, 0, 60, 6)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Policy(KeyType.IP, 1, 0, 6)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Policy(KeyType.IP, 1, 60, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("requested-tokens가 burst-capacity보다 크면(모든 요청이 거절되는 설정) 실패한다")
    void requestedTokensOverBurst() {
        assertThatThrownBy(() -> new Policy(KeyType.IP, 1, 5, 6))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("burst-capacity");
    }

    @Test
    @DisplayName("yml에서 값을 빠뜨리면 설정 바인딩이 실패해 애플리케이션이 뜨지 않는다")
    void bindingFailsOnInvalidYml() {
        contextRunner
                .withPropertyValues(
                        "app.rate-limit.enabled=true",
                        "app.rate-limit.policies.login.key=ip",
                        "app.rate-limit.policies.login.replenish-rate=1",
                        "app.rate-limit.policies.login.burst-capacity=60")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("올바른 yml은 바인딩된다")
    void bindingSucceedsOnValidYml() {
        contextRunner
                .withPropertyValues(
                        "app.rate-limit.enabled=true",
                        "app.rate-limit.policies.login.key=ip",
                        "app.rate-limit.policies.login.replenish-rate=1",
                        "app.rate-limit.policies.login.burst-capacity=60",
                        "app.rate-limit.policies.login.requested-tokens=6")
                .run(context -> assertThat(
                                context.getBean(RateLimitProperties.class).policies())
                        .containsEntry("login", new Policy(KeyType.IP, 1, 60, 6)));
    }

    @Configuration
    @EnableConfigurationProperties(RateLimitProperties.class)
    static class PropertiesConfig {}
}
