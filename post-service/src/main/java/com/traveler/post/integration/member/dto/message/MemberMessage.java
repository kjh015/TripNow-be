package com.traveler.post.integration.member.dto.message;

import java.time.Instant;

/**
 * 회원 탈퇴 Saga 메시지의 payload. WithdrawnDTO는 member-service가 발행해 여기서 읽고(member-service.member.events), ContentDeletedDTO는
 * 여기서 발행해 member-service가 읽는다(post-service.content.events).
 */
public final class MemberMessage {

    private MemberMessage() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    // reason: USER(본인 탈퇴) | ADMIN(관리자 강제 탈퇴).
    public record WithdrawnDTO(Long memberId, Instant withdrawnAt, String reason) {}

    public record ContentDeletedDTO(Long memberId, Instant deletedAt, int posts, int comments, int likes) {}
}
