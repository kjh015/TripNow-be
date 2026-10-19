package com.traveler.common.support.logging.audit;

/**
 * 감사 로그로 남길 조치. 서비스마다 enum으로 구현한다 (예: PostServiceAuditAction).
 */
public interface AuditAction {

    /** 조치 이름. enum 상수 이름이 그대로 쓰인다 (예: DELETE_COMMENT) */
    String name();

    /** 조치 대상 종류 (예: comment). 로그의 target은 "대상 종류:ID" 형식이다 */
    String getTargetType();
}
