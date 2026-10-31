package com.traveler.member.domain.member.dto.message;

import com.traveler.member.domain.member.enums.WithdrawalReason;
import java.time.Instant;

public final class MemberMessage {

    private MemberMessage() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public record WithdrawnDTO(Long memberId, Instant withdrawnAt, WithdrawalReason reason) {}
}
