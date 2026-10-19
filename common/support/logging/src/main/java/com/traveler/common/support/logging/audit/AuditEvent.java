package com.traveler.common.support.logging.audit;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.spi.LoggingEventBuilder;

/**
 * 감사 로그 한 건. {@link AuditLogger#event}로 만들고 {@link #log()}를 호출
 *
 * <p>메시지에는 사람이 읽는 "key=value" 문자열을 넣고, 같은 값을 SLF4J key-value로도 싣는다.
 * 구조화 로깅(JSON)에서는 key-value가 각각 필드가 되어 action·target·detail로 검색할 수 있다.
 */
public class AuditEvent {

    static final String KEY_ACTION = "action";
    static final String KEY_ACTOR = "actor";
    static final String KEY_TARGET = "target";

    private final Logger logger;
    private final Map<String, Object> fields = new LinkedHashMap<>();

    AuditEvent(Logger logger, AuditAction action, String actor, Object targetId) {
        this.logger = logger;
        fields.put(KEY_ACTION, action.name());
        fields.put(KEY_ACTOR, actor);
        fields.put(KEY_TARGET, action.getTargetType() + ":" + targetId);
    }

    /** 추가 정보 (예: detail("postId", postId)) */
    public AuditEvent detail(String key, Object value) {
        if (fields.containsKey(key)) {
            throw new IllegalArgumentException("이미 쓰인 감사 로그 키입니다: " + key);
        }
        fields.put(key, value);
        return this;
    }

    public void log() {
        LoggingEventBuilder builder = logger.atInfo();
        StringBuilder message = new StringBuilder();
        for (Map.Entry<String, Object> field : fields.entrySet()) {
            builder = builder.addKeyValue(field.getKey(), field.getValue());
            if (!message.isEmpty()) {
                message.append(' ');
            }
            message.append(field.getKey()).append('=').append(field.getValue());
        }
        builder.log(message.toString());
    }
}
