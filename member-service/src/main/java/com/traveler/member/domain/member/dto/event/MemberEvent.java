package com.traveler.member.domain.member.dto.event;

import com.traveler.member.domain.member.dto.message.MemberMessage;

public final class MemberEvent {

    private MemberEvent() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /** 회원 탈퇴. MemberEventListener가 받아 Outbox에 기록한다(post-service가 콘텐츠를 정리한다) */
    public record Withdrawn(MemberMessage.WithdrawnDTO memberMsg) {}
}
