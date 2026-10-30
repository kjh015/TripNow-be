package com.traveler.member.domain.member.enums;

/**
 * 탈퇴 회원의 콘텐츠 정리(post-service) 상태. 탈퇴할 때 PENDING, post-service의 CONTENT_DELETED를 받으면 DONE.
 */
public enum ContentDeletionStatus {
    PENDING,
    DONE
}
