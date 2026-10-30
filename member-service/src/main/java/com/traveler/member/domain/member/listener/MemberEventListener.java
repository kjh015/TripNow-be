package com.traveler.member.domain.member.listener;

import com.traveler.member.domain.member.dto.event.MemberEvent;
import com.traveler.member.domain.member.dto.message.MemberMessage;
import com.traveler.member.domain.member.entity.MemberWithdrawal;
import com.traveler.member.domain.member.publisher.MemberOutboxPublisher;
import com.traveler.member.domain.member.repository.MemberWithdrawalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** 회원 도메인 이벤트를 Outbox 발행으로 옮긴다. 호출한 트랜잭션 안에서 실행된다. */
@Component
@RequiredArgsConstructor
public class MemberEventListener {
    private final MemberOutboxPublisher memberOutboxPublisher;
    private final MemberWithdrawalRepository memberWithdrawalRepository;

    // 탈퇴 기록을 PENDING으로 남기고 탈퇴 이벤트를 기록한다.
    @EventListener
    public void handleWithdrawnEvent(MemberEvent.Withdrawn event) {
        MemberMessage.WithdrawnDTO msg = event.memberMsg();
        memberWithdrawalRepository.save(MemberWithdrawal.pending(msg.memberId(), msg.reason()));
        memberOutboxPublisher.publishWithdrawn(msg);
    }
}
