package com.traveler.useractivity.domain.process.format.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.traveler.useractivity.domain.process.core.code.ProcessFailCode;
import com.traveler.useractivity.domain.process.core.dispatcher.ProcessDispatcher;
import com.traveler.useractivity.domain.process.core.executor.KafkaPipelineExecutor;
import com.traveler.useractivity.domain.process.core.logging.ProcessLogContext;
import com.traveler.useractivity.domain.process.core.message.FailInfo;
import com.traveler.useractivity.domain.process.core.message.LogMetadata;
import com.traveler.useractivity.domain.process.core.model.ActiveLogProcess;
import com.traveler.useractivity.domain.process.core.provider.LogProcessProvider;
import com.traveler.useractivity.domain.process.format.message.RawLog;
import com.traveler.useractivity.domain.process.format.model.ActiveFormatRule;
import com.traveler.useractivity.domain.process.format.provider.FormatRuleProvider;
import com.traveler.useractivity.domain.process.format.service.FormatService;
import com.traveler.useractivity.global.kafka.KafkaTopicProperties;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FormatProcessor {
    private final ObjectMapper objectMapper;
    private final FormatService formatService;
    private final LogProcessProvider logProcessProvider;
    private final FormatRuleProvider formatRuleProvider;
    private final ProcessDispatcher processDispatcher;
    private final KafkaPipelineExecutor kafkaPipelineExecutor;
    private final KafkaTopicProperties topics;

    @KafkaListener(topics = "${app.kafka.topics.format-stream}", groupId = "${app.kafka.groups.format}")
    public CompletableFuture<Void> process(
            @Payload String payload,
            @Header(value = "X-Log-Process-Code", required = false) byte[] logProcessCodeHeader,
            Acknowledgment ack) {

        // fluentd는 헤더를 순수 문자열 바이트로 보내므로(타입 메타데이터 없음) 직접 파싱해야 함
        String logProcessCode = decodeHeader(logProcessCodeHeader);

        return kafkaPipelineExecutor.execute(ack, () -> {
            ActiveLogProcess logProcess = resolveLogProcess(logProcessCode, payload);

            RawLog rawLog = objectMapper.readValue(payload, RawLog.class);
            LogMetadata metadata = createMetadata(logProcess);
            ProcessLogContext.put(metadata);
            log.debug(
                    "수신 (logProcessId: {}, logProcessCode: {}, path: {})",
                    logProcess.id(),
                    logProcess.name(),
                    rawLog.path());

            List<ActiveFormatRule> activeFormatRules = formatRuleProvider.getActiveFormatRules(logProcess.id());
            Map<String, String> formattedLog = formatService.formatLog(rawLog, activeFormatRules);

            if (formattedLog == null || formattedLog.isEmpty()) {
                log.warn("포맷 실패 -> Sink (path: {})", rawLog.path());
                FailInfo failInfo = createFailInfo();
                Map<String, String> failData = Map.of("path", rawLog.path() != null ? rawLog.path() : "");
                return processDispatcher.dispatchFailure(topics.sinkStream(), metadata, failInfo, failData);
            }
            // 여기서부터 event가 확정되므로 로그 컨텍스트를 갱신
            ProcessLogContext.put(metadata, formattedLog);
            log.info("포맷 성공 -> Filter");
            return processDispatcher.dispatchSuccess(topics.filterStream(), metadata, formattedLog);
        });
    }

    private String decodeHeader(byte[] header) {
        if (header == null) {
            return null;
        }
        String value = new String(header, StandardCharsets.UTF_8).trim();
        return value.isEmpty() ? null : value;
    }

    // 헤더의 프로세스 코드로 로그 프로세스 조회
    private ActiveLogProcess resolveLogProcess(String logProcessCode, String payload) {
        if (logProcessCode != null) {
            return logProcessProvider.getByCode(logProcessCode);
        }

        throw new IllegalArgumentException(String.format(
                "Kafka Header 누락: X-Log-Process-Code (Payload Size: %d bytes)",
                payload != null ? payload.length() : 0));
    }

    // 식별용 메타데이터 조립
    private LogMetadata createMetadata(ActiveLogProcess logProcess) {
        return new LogMetadata(UUID.randomUUID().toString(), logProcess.id(), logProcess.name());
    }

    // 포맷팅 실패 원인 객체 조립
    private FailInfo createFailInfo() {
        return new FailInfo(ProcessFailCode.FORMAT_RULE_NOT_FOUND, null, null, "활성화된 포맷 규칙이 없거나 조건 불일치");
    }
}
