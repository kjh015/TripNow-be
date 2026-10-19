package com.traveler.useractivity.global.audit;

import com.traveler.common.support.logging.audit.AuditAction;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** user-activity-service 파이프라인 규칙 변경 감사 로그 대상 */
@Getter
@AllArgsConstructor
public enum UserActivityServiceAuditAction implements AuditAction {
    // Domain - Format Rule
    CREATE_FORMAT_RULE("formatRule"),
    UPDATE_FORMAT_RULE("formatRule"),
    DELETE_FORMAT_RULE("formatRule"),

    // Domain - Filter Rule
    CREATE_FILTER_RULE("filterRule"),
    UPDATE_FILTER_RULE("filterRule"),
    DELETE_FILTER_RULE("filterRule"),

    // Domain - Dedup Rule
    CREATE_DEDUP_RULE("dedupRule"),
    UPDATE_DEDUP_RULE("dedupRule"),
    DELETE_DEDUP_RULE("dedupRule"),

    // Domain - Log Process
    CREATE_LOG_PROCESS("logProcess"),
    UPDATE_LOG_PROCESS("logProcess"),
    DELETE_LOG_PROCESS("logProcess");

    private final String targetType;
}
