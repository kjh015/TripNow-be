package com.traveler.common.support.logging.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.traveler.common.support.logging.MdcKeys;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.actuate.autoconfigure.observation.ObservationAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.tracing.BraveAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.tracing.MicrometerTracingAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.logging.LoggingSystemProperty;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

/**
 * logging.yml의 추적 설정(샘플링 0)으로 실제 추적 자동 설정을 띄웠을 때, span 안의 로그에 traceId·spanId가 실리는지 확인한다.
 */
@ExtendWith(OutputCaptureExtension.class)
class TracingLogCorrelationTest {

    private static final Logger log = LoggerFactory.getLogger(TracingLogCorrelationTest.class);

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
     * Boot는 로깅 설정(구조화 형식, 패턴)을 시스템 속성으로 옮기고 이미 있으면 덮어쓰지 않는다.
     * 같은 JVM에서 앞 실행(dev)의 값이 남으면 다음 실행(local)도 JSON으로 출력되므로 실행마다 지운다
     */
    private static void clearLoggingSystemProperties() {
        for (LoggingSystemProperty property : LoggingSystemProperty.values()) {
            System.clearProperty(property.getEnvironmentVariableName());
        }
    }

    @Test
    @DisplayName("샘플링하지 않는 span이어도 배포 프로필 JSON 로그에 traceId·spanId 필드가 들어간다")
    void writesTraceIdToJsonEvenWhenNotSampled(CapturedOutput output) {
        context = run("dev");

        Span span = logInsideObservation("traced-json-check");

        assertThat(span.context().sampled()).isFalse();
        DocumentContext json = JsonPath.parse(lineContaining(output, "traced-json-check"));
        assertThat(json.read("$." + MdcKeys.TRACE_ID, String.class))
                .isEqualTo(span.context().traceId());
        assertThat(json.read("$." + MdcKeys.SPAN_ID, String.class))
                .isEqualTo(span.context().spanId());
    }

    @Test
    @DisplayName("local 프로필 평문 로그에는 [traceId,spanId]가 붙고, span 밖 로그는 비어 있다")
    void writesCorrelationPatternOnLocalProfile(CapturedOutput output) {
        context = run("local");

        Span span = logInsideObservation("traced-plain-check");
        log.info("untraced-plain-check");

        assertThat(lineContaining(output, "traced-plain-check"))
                .contains("[" + span.context().traceId() + "," + span.context().spanId() + "]");
        assertThat(lineContaining(output, "untraced-plain-check")).contains("[,]");
    }

    /** observation을 열고 그 안에서 로그를 남긴다. 그때의 span을 돌려준다 */
    private Span logInsideObservation(String message) {
        ObservationRegistry registry = context.getBean(ObservationRegistry.class);
        Tracer tracer = context.getBean(Tracer.class);
        AtomicReference<Span> current = new AtomicReference<>();
        Observation.createNotStarted("test.operation", registry).observe(() -> {
            current.set(tracer.currentSpan());
            log.info(message);
        });
        assertThat(current.get()).as("observation 안에서 현재 span이 있어야 한다").isNotNull();
        return current.get();
    }

    private static ConfigurableApplicationContext run(String profile) {
        return new SpringApplicationBuilder(TracingConfig.class)
                .web(WebApplicationType.NONE)
                .profiles(profile)
                .properties("spring.application.name=test-service", "spring.config.import=classpath:logging.yml")
                // local 프로필의 로그 파일(logs/...)을 모듈 폴더에 만들지 않는다. 빈 값이면 Boot가 파일 출력을 끈다
                .run("--logging.file.name=");
    }

    private static String lineContaining(CapturedOutput output, String text) {
        return Arrays.stream(output.getOut().split("\\R"))
                .filter(line -> line.contains(text))
                .findFirst()
                .orElseThrow(() -> new AssertionError("출력에 '" + text + "'이 없다:\n" + output.getOut()));
    }

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration({
        ObservationAutoConfiguration.class,
        MicrometerTracingAutoConfiguration.class,
        BraveAutoConfiguration.class
    })
    static class TracingConfig {}
}
