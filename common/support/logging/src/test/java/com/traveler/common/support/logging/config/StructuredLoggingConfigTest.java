package com.traveler.common.support.logging.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.traveler.common.support.logging.MdcKeys;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.logging.LoggingSystemProperty;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

/** logging.yml의 프로필별 출력 형식. 실제 Spring Boot 로깅 초기화를 거쳐 콘솔 출력을 확인한다 */
@ExtendWith(OutputCaptureExtension.class)
class StructuredLoggingConfigTest {

    private static final Logger log = LoggerFactory.getLogger(StructuredLoggingConfigTest.class);

    private ConfigurableApplicationContext context;

    @BeforeEach
    void setUp() {
        clearLoggingSystemProperties();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
        if (context != null) {
            context.close();
        }
        clearLoggingSystemProperties();
    }

    /**
     * Boot는 로깅 설정(구조화 형식, 로그 파일 경로)을 시스템 속성으로 옮기며, 기존 값이 있으면 덮어쓰지 않는다.
     * 같은 JVM에서 이전 실행(dev)의 값이 남아 있으면 다음 실행(local)에도 콘솔 로그가 JSON 형식으로 출력될 수 있으므로,
     * 실행할 때마다 관련 시스템 속성을 제거한다.
     */
    private static void clearLoggingSystemProperties() {
        for (LoggingSystemProperty property : LoggingSystemProperty.values()) {
            System.clearProperty(property.getEnvironmentVariableName());
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
    @DisplayName("local 프로필이면 콘솔은 사람이 읽는 형식을 유지하고, 파일에는 한 줄 ECS JSON으로 쓴다")
    void keepsPlainConsoleAndWritesEcsFileOnLocalProfile(CapturedOutput output) throws IOException {
        // logging.yml의 파일 경로(logs/...)는 작업 디렉터리 기준이라, 테스트에서는 build 아래로 바꾼다.
        Path logFile = Path.of("build", "test-logs", "structured-logging-" + System.nanoTime() + ".log");
        context = run("local", "--logging.file.name=" + logFile);
        MDC.put(MdcKeys.USER_ID, "7");

        log.info("plain-check");

        String consoleLine = lineContaining(output.getOut(), "plain-check");
        assertThat(consoleLine).doesNotStartWith("{").contains("[userId:7]");

        DocumentContext json = JsonPath.parse(lineContaining(Files.readString(logFile), "plain-check"));
        assertThat(json.read("$['service.name']", String.class)).isEqualTo("test-service");
        assertThat(json.read("$['service.environment']", String.class)).isEqualTo("local");
        assertThat(json.read("$.userId", String.class)).isEqualTo("7");
    }

    private static ConfigurableApplicationContext run(String profile, String... args) {
        SpringApplication application = new SpringApplicationBuilder(EmptyConfig.class)
                .web(WebApplicationType.NONE)
                .profiles(profile)
                .properties("spring.application.name=test-service", "spring.config.import=classpath:logging.yml")
                .build();
        return application.run(args);
    }

    private static String lineContaining(CapturedOutput output, String text) {
        return lineContaining(output.getOut(), text);
    }

    private static String lineContaining(String content, String text) {
        return Arrays.stream(content.split("\\R"))
                .filter(line -> line.contains(text))
                .findFirst()
                .orElseThrow(() -> new AssertionError("출력에 '" + text + "'이 없다:\n" + content));
    }

    @Configuration(proxyBeanMethods = false)
    static class EmptyConfig {}
}
