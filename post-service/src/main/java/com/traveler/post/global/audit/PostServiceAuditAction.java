package com.traveler.post.global.audit;

import com.traveler.common.support.logging.audit.AuditAction;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** post-service 관리자 조치 감사 로그 대상 */
@Getter
@AllArgsConstructor
public enum PostServiceAuditAction implements AuditAction {
    // Domain - Post
    DELETE_POST("post"),
    RESTORE_POST("post"),
    PERMANENT_DELETE_POST("post"),

    // Domain - Comment
    DELETE_COMMENT("comment"),
    RESTORE_COMMENT("comment");

    private final String targetType;
}
