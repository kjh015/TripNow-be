package com.traveler.common.support.logging.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.traveler.common.support.logging.MdcKeys;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.event.KeyValuePair;

class AuditLoggerTest {

    private final AuditLogger auditLogger = new AuditLogger();
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Logger auditLog = (Logger) LoggerFactory.getLogger(AuditLogger.LOGGER_NAME);

    @BeforeEach
    void setUp() {
        appender.start();
        auditLog.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        auditLog.detachAppender(appender);
        MDC.clear();
    }

    @Test
    @DisplayName("MDC의 요청 사용자 ID를 actor로 넣고, 메시지와 key-value에 같은 값을 남긴다")
    void logsMessageAndKeyValues() {
        MDC.put(MdcKeys.USER_ID, "1");

        auditLogger
                .event(TestAuditAction.DELETE_COMMENT, 10L)
                .detail("postId", 3L)
                .log();

        ILoggingEvent event = appender.list.getFirst();
        assertThat(event.getLoggerName()).isEqualTo("AUDIT");
        assertThat(event.getFormattedMessage()).isEqualTo("action=DELETE_COMMENT actor=1 target=comment:10 postId=3");
        assertThat(keyValues(event))
                .containsExactly(
                        Map.entry("action", "DELETE_COMMENT"),
                        Map.entry("actor", "1"),
                        Map.entry("target", "comment:10"),
                        Map.entry("postId", 3L));
    }

    @Test
    @DisplayName("요청 사용자가 없으면 actor를 system으로 남긴다")
    void logsSystemActorWithoutUser() {
        auditLogger.log(TestAuditAction.DELETE_POST, 3L);

        assertThat(appender.list.getFirst().getFormattedMessage())
                .isEqualTo("action=DELETE_POST actor=system target=post:3");
    }

    @Test
    @DisplayName("기본 필드나 이미 쓴 키를 detail로 다시 넣으면 예외를 던진다")
    void rejectsDuplicateKey() {
        AuditEvent event = auditLogger.event(TestAuditAction.DELETE_POST, 3L).detail("imageKeys", 2);

        assertThatThrownBy(() -> event.detail("imageKeys", 3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> event.detail("actor", "9")).isInstanceOf(IllegalArgumentException.class);
    }

    private static Map<String, Object> keyValues(ILoggingEvent event) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (KeyValuePair pair : event.getKeyValuePairs()) {
            result.put(pair.key, pair.value);
        }
        return result;
    }

    private enum TestAuditAction implements AuditAction {
        DELETE_POST("post"),
        DELETE_COMMENT("comment");

        private final String targetType;

        TestAuditAction(String targetType) {
            this.targetType = targetType;
        }

        @Override
        public String getTargetType() {
            return targetType;
        }
    }
}
