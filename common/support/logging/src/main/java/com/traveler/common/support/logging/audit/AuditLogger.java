package com.traveler.common.support.logging.audit;

import com.traveler.common.support.logging.MdcKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * 관리자 조치처럼 "누가, 무엇을, 누구에게" 했는지 남기는 감사 로그.
 * 로거 이름이 "AUDIT"으로 고정돼 있어 서비스와 무관하게 로거 이름 하나로 모아 볼 수 있다.
 *
 * <p>actor는 호출자가 넘기지 않고 MDC({@link MdcKeys#USER_ID})에서 읽는다.
 * 요청 스레드 밖(스케줄러, 컨슈머 등)에서 호출되면 {@value #SYSTEM_ACTOR}로 남는다.
 *
 * <pre>
 * auditLogger.log(DELETE_POST, postId);
 *
 * auditLogger.event(DELETE_COMMENT, commentId)
 *         .detail("postId", postId)
 *         .log();
 * // action=DELETE_COMMENT actor=1 target=comment:10 postId=3
 * </pre>
 */
public class AuditLogger {

    public static final String LOGGER_NAME = "AUDIT";
    static final String SYSTEM_ACTOR = "system";

    private static final Logger AUDIT = LoggerFactory.getLogger(LOGGER_NAME);

    /** 추가 정보 없이 바로 남긴다 */
    public void log(AuditAction action, Object targetId) {
        event(action, targetId).log();
    }

    /** 추가 정보를 {@link AuditEvent#detail}로 붙인 뒤 {@link AuditEvent#log()}로 남긴다 */
    public AuditEvent event(AuditAction action, Object targetId) {
        return new AuditEvent(AUDIT, action, currentActor(), targetId);
    }

    private static String currentActor() {
        String userId = MDC.get(MdcKeys.USER_ID);
        return userId != null && !userId.isBlank() ? userId : SYSTEM_ACTOR;
    }
}
