package com.traveler.member.global.audit;

import com.traveler.common.support.logging.audit.AuditAction;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** member-service 관리자 조치 감사 로그 대상 */
@Getter
@AllArgsConstructor
public enum MemberServiceAuditAction implements AuditAction {
    // Domain - Member
    GRANT_ADMIN("member"),
    DELETE_MEMBER("member");

    private final String targetType;
}
