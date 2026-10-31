package com.traveler.post.integration.member.dto.event;

import com.traveler.post.integration.member.dto.message.MemberMessage;

public final class MemberEvent {

    private MemberEvent() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /** 탈퇴 회원의 콘텐츠 정리 완료. MemberEventListener가 받아 Outbox에 기록한다(member-service가 정리 상태를 DONE으로 바꾼다) */
    public record ContentDeleted(MemberMessage.ContentDeletedDTO memberMsg) {}
}
