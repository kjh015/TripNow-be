package com.traveler.post.integration.member.listener;

import com.traveler.post.integration.member.dto.event.MemberEvent;
import com.traveler.post.integration.member.publisher.MemberOutboxPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** 탈퇴 정리 이벤트를 Outbox 발행으로 옮긴다. 호출한 트랜잭션 안에서 실행된다. */
@Component
@RequiredArgsConstructor
public class MemberEventListener {
    private final MemberOutboxPublisher memberOutboxPublisher;

    @EventListener
    public void handleContentDeletedEvent(MemberEvent.ContentDeleted event) {
        memberOutboxPublisher.publishContentDeleted(event.memberMsg());
    }
}
