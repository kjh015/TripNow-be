package com.traveler.common.support.logging.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.traveler.common.support.logging.MdcKeys;
import java.util.Arrays;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

/** logging.yml의 프로필별 출력 형식. 실제 Spring Boot 로깅 초기화를 거쳐 콘솔 출력을 확인한다 */
@ExtendWith(OutputCaptureExtension.class)
class StructuredLoggingConfigTest {

    private static final Logger log = LoggerFactory.getLogger(StructuredLoggingConfigTest.class);

    private ConfigurableApplicationContext context;

    @AfterEach
    void tearDown() {
        MDC.clear();
        if (context != null) {
            context.close();
        }
    }

    @Test
    @DisplayName("dev 프로필이면 MDC·key-value·스택 트레이스를 담은 한 줄 ECS JSON으로 출력한다")
    void writesEcsJsonOnDeployProfile(CapturedOutput output) {
        context = run("dev");
        MDC.put(MdcKeys.USER_ID, "7");

        log.atError()
                .addKeyValue("action", "DELETE_POST")
                .setCause(new IllegalStateException("boom"))
                .log("structured-check");

        String line = lineContaining(output, "structured-check");
        DocumentContext json = JsonPath.parse(line);
        assertThat(json.read("$['service.name']", String.class)).isEqualTo("test-service");
        assertThat(json.read("$['service.environment']", String.class)).isEqualTo("dev");
        assertThat(json.read("$['log.level']", String.class)).isEqualTo("ERROR");
        assertThat(json.read("$.userId", String.class)).isEqualTo("7");
        assertThat(json.read("$.action", String.class)).isEqualTo("DELETE_POST");
        assertThat(json.read("$['error.type']", String.class)).isEqualTo(IllegalStateException.class.getName());
        assertThat(json.read("$['error.stack_trace']", String.class)).contains("writesEcsJsonOnDeployProfile");
    }

    @Test
    @DisplayName("local 프로필이면 사람이 읽는 형식을 유지한다")
    void keepsPlainTextOnLocalProfile(CapturedOutput output) {
        context = run("local");
        MDC.put(MdcKeys.USER_ID, "7");

        log.info("plain-check");

        String line = lineContaining(output, "plain-check");
        assertThat(line).doesNotStartWith("{").contains("[userId:7]");
    }

    private static ConfigurableApplicationContext run(String profile) {
        SpringApplication application = new SpringApplicationBuilder(EmptyConfig.class)
                .web(WebApplicationType.NONE)
                .profiles(profile)
                .properties("spring.application.name=test-service", "spring.config.import=classpath:logging.yml")
                .build();
        return application.run();
    }

    private static String lineContaining(CapturedOutput output, String text) {
        return Arrays.stream(output.getOut().split("\\R"))
                .filter(line -> line.contains(text))
                .findFirst()
                .orElseThrow(() -> new AssertionError("출력에 '" + text + "'이 없다:\n" + output.getOut()));
    }

    @Configuration(proxyBeanMethods = false)
    static class EmptyConfig {}
}
